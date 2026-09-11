package com.gridee.parking.ui.operator

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.GrideeApplication
import com.google.gson.GsonBuilder
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.BookingPolicyResolver
import com.gridee.parking.data.model.CheckInMode
import com.gridee.parking.data.model.CheckInRequest
import com.gridee.parking.data.model.ErrorResponse
import com.gridee.parking.data.repository.BookingRepository
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.notifications.ParkingSpotRefreshEvents
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.VehicleNumberValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Response
import java.util.concurrent.atomic.AtomicLong

internal data class OperatorOperationKey(
    val operation: String,
    val inputMode: String,
    val identifier: String,
    val parkingLotId: String,
    val parkingSpotId: String?
)

internal sealed class OperatorOperationStart {
    object Started : OperatorOperationStart()
    data class Busy(val activeRequestId: Long) : OperatorOperationStart()
    data class CoolingDown(val remainingMs: Long) : OperatorOperationStart()
}

private data class ActiveOperatorOperation(
    val key: OperatorOperationKey,
    val requestId: Long,
    val sessionUserId: String?,
    val startedAtElapsedMs: Long,
    val networkTraceId: String?,
    val result: Deferred<CheckInState>,
) {
    var completedAtElapsedMs: Long = NOT_COMPLETED
    val terminalDelivery = OperatorTerminalDelivery()

    companion object {
        const val NOT_COMPLETED = Long.MIN_VALUE
    }
}

internal data class OperatorOperationPresentation(
    val requestId: Long,
    val startedAtElapsedMs: Long,
    val isInFlight: Boolean,
    val networkTraceId: String?,
)

enum class OperatorErrorCategory(val metricValue: String) {
    VALIDATION("validation"),
    AUTHORIZATION("authorization"),
    NOT_FOUND("not_found"),
    BACKEND_REJECTION("backend_rejection"),
    SCOPE_MISMATCH("scope_mismatch"),
    NETWORK("network"),
    UNKNOWN("unknown"),
}

internal object OperatorBookingScopeValidator {
    fun validate(
        returnedLotId: String,
        returnedSpotId: String,
        expectedLotId: String,
        expectedSpotId: String?
    ): String? {
        if (returnedLotId.trim() != expectedLotId) {
            return "Operator response did not match the assigned parking lot"
        }
        val normalizedReturnedSpotId = returnedSpotId.trim()
        if (normalizedReturnedSpotId.isEmpty()) {
            return "Operator response did not include a parking spot"
        }
        if (expectedSpotId != null && normalizedReturnedSpotId != expectedSpotId) {
            return "Operator response did not match the selected parking spot"
        }
        return null
    }
}

/**
 * ViewModel for operator dashboard. Mutations are deliberately serialized: a check-in and a
 * check-out can never overlap in one operator session, even when the Activity is recreated.
 */
class OperatorViewModel(
    private val bookingRepository: BookingRepository = BookingRepository(),
    private val parkingRepository: ParkingRepository = ParkingRepository(),
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) : ViewModel() {

    private val _checkInState = MutableLiveData<CheckInState>(CheckInState.Idle)
    val checkInState: LiveData<CheckInState> = _checkInState

    private val _checkOutState = MutableLiveData<CheckInState>(CheckInState.Idle)
    val checkOutState: LiveData<CheckInState> = _checkOutState
    private var observedRequestId: Long? = null
    private val terminalConsumerId = nextTerminalConsumerId.incrementAndGet()
    private var terminalDeliveryActive = false
    private var terminalWaitJob: Job? = null

    fun hasActiveOperation(): Boolean = currentRetainedOperation()?.let {
        it.completedAtElapsedMs == ActiveOperatorOperation.NOT_COMPLETED
    } == true

    fun activeRequestId(): Long? = currentRetainedOperation()?.requestId

    internal fun operationPresentation(requestId: Long? = null): OperatorOperationPresentation? {
        val operation = currentRetainedOperation() ?: return null
        if (requestId != null && operation.requestId != requestId) return null
        return OperatorOperationPresentation(
            requestId = operation.requestId,
            startedAtElapsedMs = operation.startedAtElapsedMs,
            isInFlight = operation.completedAtElapsedMs == ActiveOperatorOperation.NOT_COMPLETED,
            networkTraceId = operation.networkTraceId,
        )
    }

    /**
     * Activities call this from onResume/onPause so only the visible operator surface can claim
     * a terminal mutation result. This prevents a stopped dashboard and its scanner from both
     * presenting the same success/error, and avoids replaying an old LiveData terminal later.
     */
    fun setTerminalDeliveryActive(active: Boolean) {
        terminalDeliveryActive = active
        if (active) {
            reattachToRetainedOperation()
        } else {
            terminalWaitJob?.cancel()
            terminalWaitJob = null
            observedRequestId?.let { releaseTerminalConsumer(it, terminalConsumerId) }
        }
    }

    /** Reconnects an existing dashboard/scanner ViewModel after the other Activity closes. */
    private fun reattachToRetainedOperation() {
        val operation = currentRetainedOperation()
        if (operation != null) {
            observeOperation(operation)
            return
        }
        // No retained request for this authenticated session means any local Loading value is
        // orphaned. This also covers an account change while the previous account's application-
        // scope request winds down; that old coordinator lock must not strand the new screen.
        terminalWaitJob?.cancel()
        terminalWaitJob = null
        if (_checkInState.value is CheckInState.Loading) _checkInState.value = CheckInState.Idle
        if (_checkOutState.value is CheckInState.Loading) _checkOutState.value = CheckInState.Idle
    }

    /** Check-in using vehicle number (operator mode). */
    fun checkInByVehicleNumber(
        vehicleNumber: String,
        parkingSpotId: String? = null,
        requestId: Long = SystemClock.elapsedRealtimeNanos(),
        parkingLotId: String? = null,
        networkTraceId: String? = null,
    ): Boolean {
        return performVehicleOperation(
            operation = OPERATION_CHECK_IN,
            vehicleNumber = vehicleNumber,
            parkingSpotId = parkingSpotId,
            parkingLotId = parkingLotId,
            requestId = requestId,
            networkTraceId = networkTraceId,
            state = _checkInState,
            requestCall = { request, lotId ->
                bookingRepository.operatorCheckIn(request, lotId, networkTraceId)
            },
            errorMessage = { code, normalizedVehicle, fallback ->
                when (code) {
                    404 -> "No booking found for vehicle: $normalizedVehicle"
                    403 -> "Not authorized to perform check-in"
                    400 -> "Invalid request. Check vehicle registration number"
                    else -> "Check-in failed: $fallback"
                }
            }
        )
    }

    /** Check-out using vehicle number (operator mode). */
    fun checkOutByVehicleNumber(
        vehicleNumber: String,
        parkingSpotId: String? = null,
        requestId: Long = SystemClock.elapsedRealtimeNanos(),
        parkingLotId: String? = null,
        networkTraceId: String? = null,
    ): Boolean {
        return performVehicleOperation(
            operation = OPERATION_CHECK_OUT,
            vehicleNumber = vehicleNumber,
            parkingSpotId = parkingSpotId,
            parkingLotId = parkingLotId,
            requestId = requestId,
            networkTraceId = networkTraceId,
            state = _checkOutState,
            requestCall = { request, lotId ->
                bookingRepository.operatorCheckOut(request, lotId, networkTraceId)
            },
            errorMessage = { code, normalizedVehicle, fallback ->
                when (code) {
                    404 -> "No active booking found for vehicle: $normalizedVehicle"
                    403 -> "Not authorized to perform check-out"
                    400 -> "Invalid request. Check vehicle registration number"
                    else -> "Check-out failed: $fallback"
                }
            }
        )
    }

    /** Check-in using a booking QR, constrained to the operator's selected physical spot. */
    fun checkInByQrCode(
        qrCode: String,
        parkingSpotId: String? = null,
        requestId: Long = SystemClock.elapsedRealtimeNanos(),
        parkingLotId: String? = null,
        networkTraceId: String? = null,
    ): Boolean {
        return performQrOperation(
            operation = OPERATION_CHECK_IN,
            qrCode = qrCode,
            parkingSpotId = parkingSpotId,
            parkingLotId = parkingLotId,
            requestId = requestId,
            networkTraceId = networkTraceId,
            state = _checkInState,
            requestCall = { request, lotId ->
                bookingRepository.operatorCheckIn(request, lotId, networkTraceId)
            },
            fallbackErrorPrefix = "Check-in failed"
        )
    }

    /** Check-out using a booking QR, constrained to the operator's selected physical spot. */
    fun checkOutByQrCode(
        qrCode: String,
        parkingSpotId: String? = null,
        requestId: Long = SystemClock.elapsedRealtimeNanos(),
        parkingLotId: String? = null,
        networkTraceId: String? = null,
    ): Boolean {
        return performQrOperation(
            operation = OPERATION_CHECK_OUT,
            qrCode = qrCode,
            parkingSpotId = parkingSpotId,
            parkingLotId = parkingLotId,
            requestId = requestId,
            networkTraceId = networkTraceId,
            state = _checkOutState,
            requestCall = { request, lotId ->
                bookingRepository.operatorCheckOut(request, lotId, networkTraceId)
            },
            fallbackErrorPrefix = "Check-out failed"
        )
    }

    fun resetCheckInState() {
        acknowledgeTerminalState(_checkInState.value)
        _checkInState.value = CheckInState.Idle
    }

    fun resetCheckOutState() {
        acknowledgeTerminalState(_checkOutState.value)
        _checkOutState.value = CheckInState.Idle
    }

    private fun performVehicleOperation(
        operation: String,
        vehicleNumber: String,
        parkingSpotId: String?,
        parkingLotId: String?,
        requestId: Long,
        networkTraceId: String?,
        state: MutableLiveData<CheckInState>,
        requestCall: suspend (CheckInRequest, String?) -> Response<Booking>,
        errorMessage: (Int, String, String) -> String
    ): Boolean {
        val vehicleError = VehicleNumberValidator.getError(vehicleNumber)
        val normalizedVehicle = VehicleNumberValidator.normalize(vehicleNumber)
        val normalizedSpotId = normalizeId(parkingSpotId)
        val normalizedLotId = normalizeLotId(parkingLotId)

        val validationError = when {
            vehicleError != null -> vehicleError
            normalizedLotId == null -> "Your operator account is not assigned to a parking lot"
            normalizedSpotId == null -> "Please select a parking spot to continue"
            else -> null
        }
        if (validationError != null) {
            state.value = CheckInState.Error(
                validationError,
                requestId,
                OperatorErrorCategory.VALIDATION,
            )
            return false
        }
        val scopedLotId = requireNotNull(normalizedLotId)
        val scopedSpotId = requireNotNull(normalizedSpotId)

        val request = CheckInRequest(
            mode = CheckInMode.VEHICLE_NUMBER,
            vehicleNumber = normalizedVehicle,
            parkingLotId = scopedLotId,
            parkingSpotId = scopedSpotId
        )
        val key = OperatorOperationKey(
            operation = operation,
            inputMode = CheckInMode.VEHICLE_NUMBER.name,
            identifier = normalizedVehicle,
            parkingLotId = scopedLotId,
            parkingSpotId = scopedSpotId
        )

        return launchOperation(
            key = key,
            requestId = requestId,
            networkTraceId = networkTraceId,
            state = state,
            parkingLotId = scopedLotId,
            expectedSpotId = scopedSpotId,
            requestCall = { requestCall(request, scopedLotId) },
            responseError = { response ->
                extractBackendErrorMessage(response)
                    ?: errorMessage(response.code(), normalizedVehicle, response.message())
            }
        )
    }

    private fun performQrOperation(
        operation: String,
        qrCode: String,
        parkingSpotId: String?,
        parkingLotId: String?,
        requestId: Long,
        networkTraceId: String?,
        state: MutableLiveData<CheckInState>,
        requestCall: suspend (CheckInRequest, String?) -> Response<Booking>,
        fallbackErrorPrefix: String
    ): Boolean {
        val normalizedQrCode = qrCode.trim()
        val normalizedSpotId = normalizeId(parkingSpotId)
        val normalizedLotId = normalizeLotId(parkingLotId)
        val validationError = when {
            normalizedQrCode.isEmpty() -> "QR code cannot be empty"
            normalizedLotId == null -> "Your operator account is not assigned to a parking lot"
            normalizedSpotId == null -> "Please select a parking spot to continue"
            else -> null
        }
        if (validationError != null) {
            state.value = CheckInState.Error(
                validationError,
                requestId,
                OperatorErrorCategory.VALIDATION,
            )
            return false
        }
        val scopedLotId = requireNotNull(normalizedLotId)
        val scopedSpotId = requireNotNull(normalizedSpotId)

        // The QR identifies the booking, while the selected physical spot remains an independent
        // operator safety boundary. The backend response must agree with both lot and spot.
        val request = CheckInRequest(
            mode = CheckInMode.QR_CODE,
            qrCode = normalizedQrCode,
            parkingLotId = scopedLotId,
            parkingSpotId = scopedSpotId
        )
        val key = OperatorOperationKey(
            operation = operation,
            inputMode = CheckInMode.QR_CODE.name,
            identifier = normalizedQrCode,
            parkingLotId = scopedLotId,
            parkingSpotId = scopedSpotId
        )

        return launchOperation(
            key = key,
            requestId = requestId,
            networkTraceId = networkTraceId,
            state = state,
            parkingLotId = scopedLotId,
            expectedSpotId = scopedSpotId,
            requestCall = { requestCall(request, scopedLotId) },
            responseError = { response ->
                extractBackendErrorMessage(response)
                    ?: "$fallbackErrorPrefix: ${response.message()}"
            }
        )
    }

    private fun launchOperation(
        key: OperatorOperationKey,
        requestId: Long,
        networkTraceId: String?,
        state: MutableLiveData<CheckInState>,
        parkingLotId: String,
        expectedSpotId: String?,
        requestCall: suspend () -> Response<Booking>,
        responseError: (Response<Booking>) -> String
    ): Boolean {
        val startedAtElapsedMs = elapsedRealtimeMs()
        when (val start = operationCoordinator.tryStart(key, requestId, startedAtElapsedMs)) {
            OperatorOperationStart.Started -> Unit
            is OperatorOperationStart.Busy -> {
                // Preserve and reattach to this session's original request. If the process-wide
                // guard belongs to a different session, this attempt needs its own terminal state
                // so its Activity cannot be stranded behind an unobservable Loading value.
                val retained = currentRetainedOperation()
                if (retained != null) {
                    observeOperation(retained)
                } else {
                    // The process-wide guard can briefly belong to a previous authenticated
                    // session. Report a terminal local state for this attempted request instead
                    // of leaving its Activity in an unobservable Loading state.
                    state.value = CheckInState.Error(
                        "Another operator request is still processing. Try again shortly.",
                        requestId,
                        OperatorErrorCategory.UNKNOWN,
                    )
                }
                return false
            }
            is OperatorOperationStart.CoolingDown -> {
                state.value = CheckInState.CoolingDown(start.remainingMs, requestId)
                return false
            }
        }

        state.value = CheckInState.Loading(requestId)
        val result = operatorOperationScope.async(start = CoroutineStart.LAZY) {
            try {
                val policyResponse = parkingRepository.getBookingPolicy(parkingLotId)
                val policyBody = policyResponse.body()
                if (!policyResponse.isSuccessful || policyBody == null) {
                    return@async CheckInState.Error(
                        "Parking-lot validation rules are unavailable. Try again.",
                        requestId,
                        OperatorErrorCategory.NETWORK,
                    )
                }
                if (!BookingPolicyResolver.resolve(policyBody).supportsOperatorValidation) {
                    return@async CheckInState.Error(
                        "Operator validation is disabled for this parking lot.",
                        requestId,
                        OperatorErrorCategory.AUTHORIZATION,
                    )
                }
                val response = requestCall()
                val booking = response.body()
                if (response.isSuccessful && booking != null) {
                    val scopeError = validateBookingScope(booking, parkingLotId, expectedSpotId)
                    if (scopeError == null) {
                        operationCoordinator.rememberBookingAliases(
                            parkingLotId = parkingLotId,
                            vehicleNumber = booking.vehicleNumber,
                            qrAliases = listOf(booking.id, booking.qrCode),
                            elapsedRealtimeMs = elapsedRealtimeMs(),
                        )
                        // Refresh is deliberately a sibling application-lifetime task: success
                        // reaches the UI immediately, and destroying that UI cannot cancel the
                        // one targeted selected-lot refresh.
                        operatorOperationScope.launch {
                            refreshSelectedLotSpots(parkingLotId)
                        }
                        CheckInState.Success(booking, requestId)
                    } else {
                        CheckInState.Error(
                            scopeError,
                            requestId,
                            OperatorErrorCategory.SCOPE_MISMATCH,
                        )
                    }
                } else {
                    CheckInState.Error(
                        responseError(response),
                        requestId,
                        when (response.code()) {
                            401, 403 -> OperatorErrorCategory.AUTHORIZATION
                            404 -> OperatorErrorCategory.NOT_FOUND
                            else -> OperatorErrorCategory.BACKEND_REJECTION
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CheckInState.Error(
                    "Network error: ${error.message}",
                    requestId,
                    OperatorErrorCategory.NETWORK,
                )
            } finally {
                markRetainedOperationCompleted(requestId, elapsedRealtimeMs())
                operationCoordinator.finish(requestId)
            }
        }
        val activeOperation = ActiveOperatorOperation(
            key = key,
            requestId = requestId,
            sessionUserId = currentSessionUserId(),
            startedAtElapsedMs = startedAtElapsedMs,
            networkTraceId = networkTraceId,
            result = result,
        )
        setRetainedOperation(activeOperation)
        observeOperation(activeOperation, state)
        result.start()
        return true
    }

    private fun observeOperation(
        operation: ActiveOperatorOperation,
        explicitState: MutableLiveData<CheckInState>? = null,
    ) {
        val state = explicitState ?: when (operation.key.operation) {
            OPERATION_CHECK_IN -> _checkInState
            OPERATION_CHECK_OUT -> _checkOutState
            else -> return
        }
        if (observedRequestId != operation.requestId) {
            observedRequestId = operation.requestId
            state.value = CheckInState.Loading(operation.requestId)
        }
        if (!terminalDeliveryActive) return

        registerTerminalConsumer(operation.requestId, terminalConsumerId)
        terminalWaitJob?.cancel()
        terminalWaitJob = viewModelScope.launch {
            try {
                val terminal = operation.result.await()
                if (
                    terminalDeliveryActive &&
                    claimTerminal(operation.requestId, terminalConsumerId)
                ) {
                    state.value = terminal
                }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive || !terminalDeliveryActive) {
                    throw cancelled
                }
                if (claimTerminal(operation.requestId, terminalConsumerId)) {
                    state.value = CheckInState.Error(
                        "Operator request was interrupted. Verify the booking before retrying.",
                        operation.requestId,
                        OperatorErrorCategory.UNKNOWN,
                    )
                }
            } catch (error: Exception) {
                if (
                    terminalDeliveryActive &&
                    claimTerminal(operation.requestId, terminalConsumerId)
                ) {
                    state.value = CheckInState.Error(
                        "Network error: ${error.message}",
                        operation.requestId,
                        OperatorErrorCategory.NETWORK,
                    )
                }
            }
        }
    }

    private fun acknowledgeTerminalState(state: CheckInState?) {
        val requestId = when (state) {
            is CheckInState.Success -> state.requestId
            is CheckInState.Error -> state.requestId
            else -> return
        }
        clearRetainedOperation(requestId)
    }

    private suspend fun refreshSelectedLotSpots(parkingLotId: String) {
        val refreshed = runCatching {
            OperatorParkingSpotLoader.refreshSelectedLot(
                context = GrideeApplication.instance,
                parkingRepository = parkingRepository,
                parkingLotId = parkingLotId,
            )
        }.getOrDefault(false)
        if (refreshed) {
            ParkingSpotRefreshEvents.publish(
                parkingLotId = parkingLotId,
                cacheAlreadyRefreshed = true
            )
        }
    }

    private fun validateBookingScope(
        booking: Booking,
        expectedLotId: String,
        expectedSpotId: String?
    ): String? {
        return OperatorBookingScopeValidator.validate(
            returnedLotId = booking.lotId,
            returnedSpotId = booking.spotId,
            expectedLotId = expectedLotId,
            expectedSpotId = expectedSpotId
        )
    }

    private fun normalizeId(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeLotId(raw: String?): String? {
        return normalizeId(raw)?.takeIf {
            !it.equals("Parking Lot", ignoreCase = true) &&
                !it.equals("null", ignoreCase = true) &&
                !it.equals("nil", ignoreCase = true)
        }
    }

    private fun <T> extractBackendErrorMessage(response: Response<T>): String? {
        val rawBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (rawBody.isNullOrBlank()) return null
        return try {
            val gson = GsonBuilder().setLenient().create()
            val error = gson.fromJson(rawBody, ErrorResponse::class.java)
            val validationErrors = error?.validationErrors
            when {
                !error?.message.isNullOrBlank() -> error.message
                !error?.error.isNullOrBlank() -> error.error
                !validationErrors.isNullOrEmpty() -> validationErrors.values.joinToString("\n")
                else -> rawBody
            }
        } catch (_: Exception) {
            rawBody
        }
    }

    private companion object {
        const val OPERATION_CHECK_IN = "CHECK_IN"
        const val OPERATION_CHECK_OUT = "CHECK_OUT"
        // Dashboard and scanner are separate Activities with separate ViewModels. Keeping the
        // mutation lock here makes the in-flight guard/debounce process-wide across both entry
        // points and across their configuration changes.
        val operationCoordinator = OperatorScanCoordinator()
        val operatorOperationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var retainedOperation: ActiveOperatorOperation? = null
        private val nextTerminalConsumerId = AtomicLong(0L)

        @Synchronized
        private fun currentRetainedOperation(): ActiveOperatorOperation? {
            val operation = retainedOperation ?: return null
            if (operation.sessionUserId != currentSessionUserId()) {
                retainedOperation = null
                return null
            }
            if (operation.terminalDelivery.isClaimed) return null
            return operation
        }

        @Synchronized
        private fun setRetainedOperation(operation: ActiveOperatorOperation) {
            retainedOperation = operation
        }

        @Synchronized
        private fun clearRetainedOperation(requestId: Long) {
            if (retainedOperation?.requestId == requestId) {
                retainedOperation = null
            }
        }

        @Synchronized
        private fun markRetainedOperationCompleted(requestId: Long, completedAtElapsedMs: Long) {
            retainedOperation
                ?.takeIf { it.requestId == requestId }
                ?.apply {
                    this.completedAtElapsedMs = completedAtElapsedMs
                    terminalDelivery.markCompleted()
                }
        }

        @Synchronized
        private fun registerTerminalConsumer(requestId: Long, consumerId: Long) {
            retainedOperation
                ?.takeIf { it.requestId == requestId && it.sessionUserId == currentSessionUserId() }
                ?.terminalDelivery
                ?.register(consumerId)
        }

        @Synchronized
        private fun releaseTerminalConsumer(requestId: Long, consumerId: Long) {
            retainedOperation
                ?.takeIf { it.requestId == requestId }
                ?.terminalDelivery
                ?.release(consumerId)
        }

        @Synchronized
        private fun claimTerminal(
            requestId: Long,
            consumerId: Long,
        ): Boolean {
            val operation = retainedOperation?.takeIf {
                it.requestId == requestId &&
                    it.sessionUserId == currentSessionUserId()
            } ?: return false
            if (operation.completedAtElapsedMs == ActiveOperatorOperation.NOT_COMPLETED) {
                return false
            }
            return operation.terminalDelivery.claim(consumerId)
        }

        private fun currentSessionUserId(): String? =
            AuthSession.getUserId(GrideeApplication.instance.applicationContext)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
    }
}

sealed class CheckInState {
    object Idle : CheckInState()
    data class Loading(val requestId: Long) : CheckInState()
    data class Success(val booking: Booking, val requestId: Long) : CheckInState()
    data class CoolingDown(val remainingMs: Long, val requestId: Long) : CheckInState()
    data class Error(
        val message: String,
        val requestId: Long,
        val category: OperatorErrorCategory = OperatorErrorCategory.UNKNOWN,
    ) : CheckInState()
}
