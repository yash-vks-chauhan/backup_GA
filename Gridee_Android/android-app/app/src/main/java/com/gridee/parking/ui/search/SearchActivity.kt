package com.gridee.parking.ui.search

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.ActivitySearchBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private var isClosing = false
    private val parkingRepository = ParkingRepository()
    private lateinit var parkingSpotAdapter: ArrayAdapter<SpotOption>
    private lateinit var availabilityAdapter: AvailabilitySummaryAdapter
    private val spotCache = mutableMapOf<String, ParkingSpot>()
    private val bookingDate = Calendar.getInstance()
    private var isLateBooking = false
    private var startTime: Calendar? = null
    private var endTime: Calendar? = null
    private val minBookingHour = 8
    private val minBookingMinute = 0
    private val maxBookingHour = 20
    private val maxBookingMinute = 0
    private var spotOptions: List<SpotOption> = emptyList()
    private var selectedSpotOption: SpotOption? = null

    private data class SpotOption(
        val id: String?,
        val lotId: String?,
        val name: String,
        val isAll: Boolean = false
    ) {
        override fun toString(): String = name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        runCatching { window.sharedElementsUseOverlay = false }
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAvailabilityList()
        setupInsets()
        setupBookingDate()
        setupInteractions()
        setupSpotDropdown()
        playEntranceAnimations()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchRoot) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val topPadding = systemBars.top + resources.getDimensionPixelSize(R.dimen.margin_large)
            binding.searchCard.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = topPadding
            }

            insets
        }
    }

    private fun setupInteractions() {
        binding.searchScrim.setOnClickListener { closeWithAnimation() }
        binding.startDateTimeContainer.setOnClickListener { openTimePicker(isStart = true) }
        binding.endDateTimeContainer.setOnClickListener { openTimePicker(isStart = false) }
        binding.btnSearchSpot.setOnClickListener { runSearch() }

        binding.etParkingSpot.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun playEntranceAnimations() {
        binding.searchScrim.animate()
            .alpha(1f)
            .setDuration(220)
            .setStartDelay(100)
            .start()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = currentFocus?.windowToken ?: binding.searchRoot.windowToken
        imm?.hideSoftInputFromWindow(token, 0)
        currentFocus?.clearFocus()
    }

    private fun setupBookingDate() {
        val now = Calendar.getInstance()
        isLateBooking = now.get(Calendar.HOUR_OF_DAY) >= 20
        bookingDate.timeInMillis = now.timeInMillis
        if (isLateBooking) {
            bookingDate.add(Calendar.DAY_OF_YEAR, 1)
        }
        bookingDate.set(Calendar.SECOND, 0)
        bookingDate.set(Calendar.MILLISECOND, 0)
        updateTimeLabels()
    }

    private fun openTimePicker(isStart: Boolean) {
        val initial = (if (isStart) startTime else endTime) ?: bookingDate
        val is24Hour = DateFormat.is24HourFormat(this)
        val minTime = getMinBookingTime()
        val maxTime = getMaxBookingTime()

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                var selected = Calendar.getInstance().apply {
                    timeInMillis = bookingDate.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                if (selected.before(minTime)) {
                    selected = minTime
                    showToast("Bookings start from ${formatTime(minTime)}")
                }
                if (!isStart && selected.after(maxTime)) {
                    selected = maxTime
                    showToast("End time can't be after ${formatTime(maxTime)}")
                }
                if (isStart) {
                    startTime = selected
                } else {
                    endTime = selected
                }
                updateTimeLabels()
            },
            initial.get(Calendar.HOUR_OF_DAY),
            initial.get(Calendar.MINUTE),
            is24Hour
        ).show()
    }

    private fun updateTimeLabels() {
        val suffix = if (isLateBooking) " (tomorrow)" else ""
        val dateText = DateFormat.getDateFormat(this).format(bookingDate.time)
        binding.tvSearchDate.text = "Searching for $dateText$suffix"
        binding.tvStartDateTime.text = formatTimeLabel(startTime, "Start time", suffix)
        binding.tvEndDateTime.text = formatTimeLabel(endTime, "End time", suffix)
        clearResults()
    }

    private fun formatTimeLabel(time: Calendar?, defaultLabel: String, suffix: String): String {
        return if (time == null) {
            "$defaultLabel$suffix"
        } else {
            "${formatTime(time)}$suffix"
        }
    }

    private fun formatTime(time: Calendar): String {
        return DateFormat.getTimeFormat(this).format(time.time)
    }

    private fun getMinBookingTime(): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = bookingDate.timeInMillis
            set(Calendar.HOUR_OF_DAY, minBookingHour)
            set(Calendar.MINUTE, minBookingMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getMaxBookingTime(): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = bookingDate.timeInMillis
            set(Calendar.HOUR_OF_DAY, maxBookingHour)
            set(Calendar.MINUTE, maxBookingMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun closeWithAnimation() {
        if (isClosing) return
        isClosing = true
        hideKeyboard()

        binding.searchScrim.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction { finishAfterTransition() }
            .start()
    }

    override fun onBackPressed() {
        closeWithAnimation()
    }

    private fun setupSpotDropdown() {
        parkingSpotAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        )
        binding.etParkingSpot.setAdapter(parkingSpotAdapter)
        binding.etParkingSpot.threshold = 0

        binding.parkingSpotContainer.setOnClickListener { showSpotDropdown() }
        binding.ivSpotDropdownIcon.setOnClickListener { showSpotDropdown() }
        binding.etParkingSpot.setOnClickListener { showSpotDropdown() }
        binding.etParkingSpot.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showSpotDropdown()
            }
        }
        binding.etParkingSpot.setOnItemClickListener { parent, _, position, _ ->
            selectedSpotOption = parent.adapter.getItem(position) as? SpotOption
            clearResults()
            hideKeyboard()
        }
        binding.etParkingSpot.doOnTextChanged { text, _, _, _ ->
            val current = text?.toString().orEmpty()
            selectedSpotOption = spotOptions.firstOrNull { it.name == current }
        }

        loadParkingSpots()
    }

    private fun showSpotDropdown() {
        binding.etParkingSpot.requestFocus()
        binding.etParkingSpot.showDropDown()
    }

    private fun loadParkingSpots() {
        lifecycleScope.launch {
            try {
                val resp = parkingRepository.getParkingSpots()
                if (resp.isSuccessful) {
                    val spots = resp.body().orEmpty()
                    spotCache.clear()
                    spots.forEach { spotCache[it.id] = it }
                    val allOption = SpotOption(
                        id = null,
                        lotId = null,
                        name = "All spots",
                        isAll = true
                    )
                    val options = mutableListOf(allOption)
                    spots.mapNotNull { spot ->
                        val name = getSpotDisplayName(spot).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        SpotOption(
                            id = spot.id,
                            lotId = spot.lotId,
                            name = name,
                            isAll = false
                        )
                    }.sortedBy { it.name }.let { options.addAll(it) }
                    spotOptions = options
                    parkingSpotAdapter.clear()
                    parkingSpotAdapter.addAll(options)
                    parkingSpotAdapter.notifyDataSetChanged()
                    if (binding.etParkingSpot.text.isNullOrBlank()) {
                        binding.etParkingSpot.setText(allOption.name, false)
                        selectedSpotOption = allOption
                    }
                }
            } catch (_: Exception) {
                // Ignore for now; dropdown stays empty on failure.
            }
        }
    }

    private fun getSpotDisplayName(spot: ParkingSpot): String {
        return listOf(
            spot.name,
            spot.zoneName,
            spot.lotName,
            spot.spotCode,
            spot.id
        ).firstOrNull { !it.isNullOrBlank() } ?: spot.id
    }

    private fun setupAvailabilityList() {
        availabilityAdapter = AvailabilitySummaryAdapter()
        binding.rvAvailabilitySummary.layoutManager = LinearLayoutManager(this)
        binding.rvAvailabilitySummary.adapter = availabilityAdapter
    }

    private fun runSearch() {
        hideKeyboard()
        val start = startTime ?: run {
            showToast("Select start time")
            return
        }
        val end = endTime ?: run {
            showToast("Select end time")
            return
        }
        if (end.timeInMillis <= start.timeInMillis) {
            showToast("End time must be after start time")
            return
        }

        val selection = selectedSpotOption ?: run {
            showToast("Select a parking spot")
            return
        }

        val startStr = formatDateTime(start)
        val endStr = formatDateTime(end)

        if (selection.isAll) {
            loadAllAvailability(startStr, endStr)
        } else {
            loadSingleSpotAvailability(selection, startStr, endStr)
        }
    }

    private fun loadAllAvailability(startTime: String, endTime: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val spotsResp = parkingRepository.getParkingSpots()
                if (!spotsResp.isSuccessful) {
                    showEmptyState("Unable to load parking spots")
                    return@launch
                }

                val allSpots = spotsResp.body().orEmpty()
                if (allSpots.isEmpty()) {
                    showEmptyState("No parking spots found")
                    return@launch
                }

                val availableBySpotId = mutableMapOf<String, ParkingSpot>()
                val availableBySpotCode = mutableMapOf<String, ParkingSpot>()
                val availableBySpotName = mutableMapOf<String, ParkingSpot>()
                val availableCountByLotId = mutableMapOf<String, Int>()
                val lotIds = allSpots.mapNotNull { it.lotId.takeIf { id -> id.isNotBlank() } }.distinct()
                for (lotId in lotIds) {
                    val availableResp = parkingRepository.getAvailableSpots(lotId, startTime, endTime)
                    if (availableResp.isSuccessful) {
                        val availableSpots = availableResp.body().orEmpty()
                        availableCountByLotId[lotId] = availableSpots.count { it.available > 0 }
                        availableSpots.forEach { spot ->
                            availableBySpotId[spot.id] = spot
                            spot.spotCode?.let { availableBySpotCode[it] = spot }
                            spot.name?.let { availableBySpotName[it.lowercase()] = spot }
                        }
                    }
                }

                val summaries = allSpots.map { spot ->
                    val displayName = getSpotDisplayName(spot)
                    val availableSpot = availableBySpotId[spot.id]
                        ?: spot.spotCode?.let { availableBySpotCode[it] }
                        ?: spot.name?.let { availableBySpotName[it.lowercase()] }
                    val lotAvailableCount = spot.lotId.takeIf { it.isNotBlank() }?.let { availableCountByLotId[it] }
                    val availableCount = resolveAvailableCount(spot, availableSpot, lotAvailableCount)
                    val total = resolveSpotTotal(spot, availableSpot, availableCount)
                    AvailabilitySummary(
                        name = displayName,
                        available = availableCount,
                        total = total
                    )
                }.sortedBy { it.name }

                showResults(summaries)
            } catch (_: Exception) {
                showEmptyState("Unable to load availability")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadSingleSpotAvailability(selection: SpotOption, startTime: String, endTime: String) {
        val lotId = selection.lotId
        val spotId = selection.id
        if (lotId.isNullOrBlank() || spotId.isNullOrBlank()) {
            showToast("Select a valid parking spot")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val availableResp = parkingRepository.getAvailableSpots(lotId, startTime, endTime)
                val spot = spotCache[spotId]
                if (!availableResp.isSuccessful) {
                    if (spot == null) {
                        showEmptyState("Unable to check availability")
                        return@launch
                    }
                    val fallbackAvailable = spot.available
                    val fallbackTotal = resolveSpotTotal(spot, null, fallbackAvailable)
                    showResults(
                        listOf(
                            AvailabilitySummary(
                                name = selection.name,
                                available = fallbackAvailable,
                                total = fallbackTotal
                            )
                        )
                    )
                    return@launch
                }
                val availableSpots = availableResp.body().orEmpty()
                val availableSpot = availableSpots.firstOrNull { it.id == spotId }
                val lotAvailableCount = availableSpots.count { it.available > 0 }
                val availableCount = resolveAvailableCount(spot, availableSpot, lotAvailableCount)
                val total = resolveSpotTotal(spot, availableSpot, availableCount)
                showResults(
                    listOf(
                        AvailabilitySummary(
                            name = selection.name,
                            available = availableCount,
                            total = total
                        )
                    )
                )
            } catch (_: Exception) {
                showEmptyState("Unable to check availability")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun formatDateTime(time: Calendar): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
        return df.format(time.time)
    }

    private fun resolveSpotTotal(
        spot: ParkingSpot?,
        availableSpot: ParkingSpot?,
        availableCount: Int
    ): Int {
        val total = listOf(
            spot?.capacity ?: 0,
            spot?.available ?: 0,
            availableSpot?.capacity ?: 0,
            availableSpot?.available ?: 0,
            availableCount
        ).maxOrNull() ?: 0
        return if (total > 0) total else 1
    }

    private fun resolveAvailableCount(
        spot: ParkingSpot?,
        availableSpot: ParkingSpot?,
        lotAvailableCount: Int?
    ): Int {
        if (availableSpot != null) {
            val availableValue = availableSpot.available
            return if (availableValue > 0) availableValue else 0
        } else {
            return if (shouldUseLotFallback(spot)) {
                when {
                    lotAvailableCount != null && lotAvailableCount > 0 -> lotAvailableCount
                    (spot?.available ?: 0) > 0 -> spot?.available ?: 0
                    else -> 0
                }
            } else {
                spot?.available ?: 0
            }
        }
    }

    private fun shouldUseLotFallback(spot: ParkingSpot?): Boolean {
        val capacity = spot?.capacity ?: 0
        val available = spot?.available ?: 0
        return capacity > 1 || available > 1
    }

    private fun showResults(items: List<AvailabilitySummary>) {
        if (items.isEmpty()) {
            showEmptyState("No availability found")
            return
        }
        binding.tvAvailabilityEmpty.visibility = android.view.View.GONE
        binding.rvAvailabilitySummary.visibility = android.view.View.VISIBLE
        availabilityAdapter.submitList(items)
    }

    private fun showEmptyState(message: String) {
        binding.tvAvailabilityEmpty.text = message
        binding.tvAvailabilityEmpty.visibility = android.view.View.VISIBLE
        binding.rvAvailabilitySummary.visibility = android.view.View.GONE
    }

    private fun clearResults() {
        binding.tvAvailabilityEmpty.visibility = android.view.View.GONE
        binding.rvAvailabilitySummary.visibility = android.view.View.GONE
        availabilityAdapter.submitList(emptyList())
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressAvailability.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnSearchSpot.isEnabled = !isLoading
        binding.btnSearchSpot.alpha = if (isLoading) 0.7f else 1f
        if (isLoading) {
            binding.tvAvailabilityEmpty.visibility = android.view.View.GONE
            binding.rvAvailabilitySummary.visibility = android.view.View.GONE
        }
    }
}
