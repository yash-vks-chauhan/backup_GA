package com.gridee.parking.ui.booking

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.databinding.ActivityPaymentBinding

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var viewModel: PaymentViewModel
    private var selectedPaymentMethod: PaymentMethod? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PaymentViewModel::class.java]

        getBookingDataFromIntent()
        setupUI()
        setupClickListeners()
        setupObservers()
    }

    private fun getBookingDataFromIntent() {
        val spotId = intent.getStringExtra("PARKING_SPOT_ID") ?: ""
        val startTime = intent.getLongExtra("START_TIME", 0)
        val endTime = intent.getLongExtra("END_TIME", 0)
        val totalPrice = intent.getDoubleExtra("TOTAL_PRICE", 0.0)
        val selectedSpot = intent.getStringExtra("SELECTED_SPOT")

        viewModel.setBookingData(spotId, startTime, endTime, totalPrice, selectedSpot)
    }

    private fun setupUI() {
        binding.tvTitle.text = "Payment"
        
        // Load available payment methods
        viewModel.loadPaymentMethods()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Payment method cards
        binding.cardCreditCard.setOnClickListener {
            selectPaymentMethod(PaymentMethod.CREDIT_CARD)
        }

        binding.cardDigitalWallet.setOnClickListener {
            selectPaymentMethod(PaymentMethod.DIGITAL_WALLET)
        }

        binding.cardUpi.setOnClickListener {
            selectPaymentMethod(PaymentMethod.UPI)
        }

        // Future payment methods (ready for Razorpay integration)
        binding.cardNetBanking.setOnClickListener {
            selectPaymentMethod(PaymentMethod.NET_BANKING)
        }

        binding.btnPayNow.setOnClickListener {
            processPayment()
        }
    }

    private fun setupObservers() {
        viewModel.totalAmount.observe(this) { amount ->
            binding.tvTotalAmount.text = "$${"%.2f".format(amount)}"
            binding.tvPayButtonAmount.text = "Pay $${"%.2f".format(amount)}"
        }

        viewModel.paymentMethods.observe(this) { methods ->
            updatePaymentMethodsDisplay(methods)
        }

        viewModel.isProcessing.observe(this) { isProcessing ->
            binding.btnPayNow.isEnabled = !isProcessing
            binding.progressBar.visibility = if (isProcessing) 
                android.view.View.VISIBLE else android.view.View.GONE
            
            if (isProcessing) {
                binding.tvPayButtonAmount.text = "Processing..."
            }
        }

        viewModel.paymentResult.observe(this) { result ->
            handlePaymentResult(result)
        }
    }

    private fun selectPaymentMethod(method: PaymentMethod) {
        selectedPaymentMethod = method
        updatePaymentMethodSelection(method)
        viewModel.selectPaymentMethod(method)
    }

    private fun updatePaymentMethodSelection(selectedMethod: PaymentMethod) {
        // Reset all selections
        binding.cardCreditCard.setCardBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
        )
        binding.cardDigitalWallet.setCardBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
        )
        binding.cardUpi.setCardBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
        )
        binding.cardNetBanking.setCardBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
        )

        // Highlight selected method
        val selectedColor = androidx.core.content.ContextCompat.getColor(this, com.gridee.parking.R.color.brand_surface)
        when (selectedMethod) {
            PaymentMethod.CREDIT_CARD -> binding.cardCreditCard.setCardBackgroundColor(selectedColor)
            PaymentMethod.DIGITAL_WALLET -> binding.cardDigitalWallet.setCardBackgroundColor(selectedColor)
            PaymentMethod.UPI -> binding.cardUpi.setCardBackgroundColor(selectedColor)
            PaymentMethod.NET_BANKING -> binding.cardNetBanking.setCardBackgroundColor(selectedColor)
        }

        binding.btnPayNow.isEnabled = true
    }

    private fun updatePaymentMethodsDisplay(methods: List<PaymentMethodInfo>) {
        // TODO: Update payment methods display based on available options
    }

    private fun processPayment() {
        selectedPaymentMethod?.let { method ->
            when (method) {
                PaymentMethod.CREDIT_CARD -> showCreditCardDialog()
                PaymentMethod.DIGITAL_WALLET -> processDigitalWalletPayment()
                PaymentMethod.UPI -> showUpiDialog()
                PaymentMethod.NET_BANKING -> processNetBankingPayment()
            }
        } ?: run {
            showToast("Please select a payment method")
        }
    }

    private fun showCreditCardDialog() {
        // TODO: Show credit card input dialog
        // For now, simulate payment
        simulatePayment()
    }

    private fun processDigitalWalletPayment() {
        // TODO: Integrate with digital wallet APIs
        simulatePayment()
    }

    private fun showUpiDialog() {
        // TODO: Show UPI payment dialog
        simulatePayment()
    }

    private fun processNetBankingPayment() {
        // TODO: Integrate with net banking
        simulatePayment()
    }

    private fun simulatePayment() {
        viewModel.processPayment(selectedPaymentMethod!!)
    }

    private fun handlePaymentResult(result: PaymentResult) {
        when (result.status) {
            PaymentStatus.SUCCESS -> {
                val intent = Intent(this, BookingConfirmationActivity::class.java)
                intent.putExtra("BOOKING_ID", result.bookingId)
                intent.putExtra("TRANSACTION_ID", result.transactionId)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            PaymentStatus.FAILED -> {
                showToast("Payment failed: ${result.errorMessage}")
            }
            PaymentStatus.CANCELLED -> {
                showToast("Payment cancelled")
            }
            PaymentStatus.PENDING -> {
                showToast("Payment is pending")
            }
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
