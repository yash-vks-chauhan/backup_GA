package com.gridee.parking.ui.booking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PaymentViewModel : ViewModel() {

    private val _totalAmount = MutableLiveData<Double>()
    val totalAmount: LiveData<Double> = _totalAmount

    private val _paymentMethods = MutableLiveData<List<PaymentMethodInfo>>()
    val paymentMethods: LiveData<List<PaymentMethodInfo>> = _paymentMethods

    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _paymentResult = MutableLiveData<PaymentResult>()
    val paymentResult: LiveData<PaymentResult> = _paymentResult

    private var currentBookingData: BookingPaymentData? = null
    private var selectedMethod: PaymentMethod? = null

    fun setBookingData(spotId: String, startTime: Long, endTime: Long, totalPrice: Double, selectedSpot: String?) {
        currentBookingData = BookingPaymentData(
            spotId = spotId,
            startTime = startTime,
            endTime = endTime,
            totalPrice = totalPrice,
            selectedSpot = selectedSpot
        )
        _totalAmount.value = totalPrice
    }

    fun loadPaymentMethods() {
        // Load available payment methods
        val availableMethods = listOf(
            PaymentMethodInfo(
                method = PaymentMethod.CREDIT_CARD,
                title = "Credit/Debit Card",
                subtitle = "Visa, Mastercard, American Express",
                isEnabled = true
            ),
            PaymentMethodInfo(
                method = PaymentMethod.DIGITAL_WALLET,
                title = "Digital Wallet",
                subtitle = "Apple Pay, Google Pay, PayPal",
                isEnabled = true
            ),
            PaymentMethodInfo(
                method = PaymentMethod.UPI,
                title = "UPI Payment",
                subtitle = "PhonePe, Google Pay, Paytm",
                isEnabled = true
            ),
            PaymentMethodInfo(
                method = PaymentMethod.NET_BANKING,
                title = "Net Banking",
                subtitle = "All major banks supported",
                isEnabled = true
            )
        )
        _paymentMethods.value = availableMethods
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        selectedMethod = method
    }

    fun processPayment(paymentMethod: PaymentMethod) {
        viewModelScope.launch {
            _isProcessing.value = true
            
            try {
                // Simulate payment processing
                delay(2000)
                
                // For demo purposes, simulate success
                val result = PaymentResult(
                    status = PaymentStatus.SUCCESS,
                    transactionId = generateTransactionId(),
                    bookingId = generateBookingId(),
                    amount = _totalAmount.value ?: 0.0,
                    paymentMethod = paymentMethod,
                    timestamp = System.currentTimeMillis()
                )
                
                _paymentResult.value = result
                
            } catch (e: Exception) {
                _paymentResult.value = PaymentResult(
                    status = PaymentStatus.FAILED,
                    errorMessage = e.message ?: "Payment processing failed",
                    paymentMethod = paymentMethod,
                    timestamp = System.currentTimeMillis()
                )
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun generateTransactionId(): String {
        return "TXN${System.currentTimeMillis()}"
    }

    private fun generateBookingId(): String {
        return "BK${System.currentTimeMillis()}"
    }
}

// Data classes for payment system
data class BookingPaymentData(
    val spotId: String,
    val startTime: Long,
    val endTime: Long,
    val totalPrice: Double,
    val selectedSpot: String?
)

data class PaymentMethodInfo(
    val method: PaymentMethod,
    val title: String,
    val subtitle: String,
    val isEnabled: Boolean,
    val icon: Int? = null
)

data class PaymentResult(
    val status: PaymentStatus,
    val transactionId: String? = null,
    val bookingId: String? = null,
    val amount: Double = 0.0,
    val paymentMethod: PaymentMethod? = null,
    val timestamp: Long,
    val errorMessage: String? = null
)

enum class PaymentMethod {
    CREDIT_CARD,
    DIGITAL_WALLET,
    UPI,
    NET_BANKING
}

enum class PaymentStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
    PENDING
}
