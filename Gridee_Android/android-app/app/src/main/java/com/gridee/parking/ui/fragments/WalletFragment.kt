package com.gridee.parking.ui.fragments

import android.content.Intent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.databinding.FragmentWalletNewBinding
import com.gridee.parking.ui.activities.TransactionHistoryActivity
import com.gridee.parking.ui.adapters.Transaction
import com.gridee.parking.ui.adapters.TransactionType
import com.gridee.parking.ui.adapters.TransactionsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser
import kotlinx.coroutines.launch
import java.util.*

class WalletFragment : BaseTabFragment<FragmentWalletNewBinding>() {

    private lateinit var transactionsAdapter: TransactionsAdapter
    private var currentBalance = 0.0
    private var userTransactions = mutableListOf<Transaction>()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentWalletNewBinding {
        return FragmentWalletNewBinding.inflate(inflater, container, false)
    }

    override fun getScrollableView(): View? {
        return try {
            binding.scrollContent
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun setupUI() {
        setupRecyclerView()
        setupClickListeners()
        loadWalletData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh wallet data after returning from Razorpay checkout
        loadWalletData()
    }

    private fun setupRecyclerView() {
        transactionsAdapter = TransactionsAdapter(emptyList())
        
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        // Add money button
        binding.btnAddMoney.setOnClickListener {
            showTopUpDialog()
        }
        
        // Tap balance to show/hide (privacy feature)
        binding.tvBalanceAmount.setOnClickListener {
            showTopUpDialog()
        }
        
        // Quick add buttons
        binding.btnQuickAdd10.setOnClickListener {
            processTopUp(50.0)
        }
        
        binding.btnQuickAdd20.setOnClickListener {
            processTopUp(100.0)
        }
        
        binding.btnQuickAdd100.setOnClickListener {
            processTopUp(200.0)
        }
        
        binding.tvViewAll.setOnClickListener {
            // Navigate to TransactionHistoryActivity to show all transactions
            val intent = Intent(requireContext(), TransactionHistoryActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadWalletData() {
        val userId = getUserId()
        if (userId == null) {
            showToast("Please login to view wallet")
            return
        }

        lifecycleScope.launch {
            try {
                // Fetch wallet balance
                val balanceResponse = ApiClient.apiService.getWalletDetails(userId)
                if (balanceResponse.isSuccessful) {
                    val walletDetails = balanceResponse.body()
                    if (walletDetails != null) {
                        currentBalance = walletDetails.balance ?: 0.0
                        updateBalanceDisplay()
                    } else {
                        showToast("Wallet details response is null")
                    }
                } else {
                    if (balanceResponse.code() == 401) {
                        handleUnauthorized()
                        return@launch
                    } else if (balanceResponse.code() == 404) {
                        // Wallet not found — treat as zero balance for resilience
                        currentBalance = 0.0
                        updateBalanceDisplay()
                        // Continue to load transactions
                    } else {
                        showToast("Failed to load wallet balance: ${balanceResponse.code()}")
                    }
                }

                // Fetch wallet transactions separately
                val transactionsResponse = ApiClient.apiService.getWalletTransactions(userId)
                if (transactionsResponse.isSuccessful) {
                    val transactions = transactionsResponse.body()
                    if (transactions != null && transactions.isNotEmpty()) {
                        userTransactions.clear()
                        try {
                            userTransactions.addAll(transactions.mapNotNull { transaction ->
                                // Skip null transactions and handle conversion errors
                                try {
                                    convertToUITransaction(transaction)
                                } catch (e: Exception) {
                                    showToast("Error converting transaction: ${e.message}")
                                    null
                                }
                            })
                            updateTransactionsList()
                            if (userTransactions.isNotEmpty()) {
                                showToast("Loaded ${userTransactions.size} transactions")
                            } else {
                                showToast("No valid transactions found")
                            }
                        } catch (e: Exception) {
                            showToast("Error processing transactions: ${e.message}")
                            userTransactions.clear()
                            updateTransactionsList()
                        }
                    } else {
                        // Empty transactions list
                        userTransactions.clear()
                        updateTransactionsList()
                        showToast("No transactions found")
                    }
                } else {
                    if (transactionsResponse.code() == 401) {
                        handleUnauthorized()
                        return@launch
                    }
                    showToast("Failed to load transactions: ${transactionsResponse.code()}")
                    userTransactions.clear()
                    updateTransactionsList() // Show empty state
                }
                
            } catch (e: Exception) {
                showToast("Error loading wallet: ${e.message}")
                // Load cached balance as fallback
                loadCachedBalance()
                userTransactions.clear()
                updateTransactionsList() // Show empty state for transactions
            }
        }
    }

    private fun loadCachedBalance() {
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        currentBalance = sharedPref.getFloat("wallet_balance", 0.0f).toDouble()
        updateBalanceDisplay()
    }

    private fun updateBalanceDisplay() {
        binding.tvBalanceAmount.text = "₹${String.format("%.2f", currentBalance)}"
        
        // Update last updated time
        binding.tvLastUpdated.text = "Updated now"
        
        // Save balance to cache
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putFloat("wallet_balance", currentBalance.toFloat()).apply()
    }

    private fun updateTransactionsList() {
        if (userTransactions.isEmpty()) {
            binding.rvTransactions.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvTransactions.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            
            // Show only recent transactions (last 5)
            val recentTransactions = userTransactions.take(5)
            transactionsAdapter.updateTransactions(recentTransactions)
        }
    }

    private fun convertToUITransaction(walletTransaction: WalletTransaction): Transaction {
        // Validate required fields
        val id = walletTransaction.id?.trim()
        val timestamp = walletTransaction.timestamp?.trim()
        
        if (id.isNullOrBlank() || timestamp.isNullOrBlank()) {
            throw IllegalArgumentException("Transaction missing required fields (id or timestamp)")
        }
        
        val parsedTimestamp = BackendTimestampParser.parse(timestamp)

        // Normalize fields
        val rawDescription = walletTransaction.description?.trim()
        val normalizedDescription = rawDescription?.ifBlank { null }
        val descriptionLower = normalizedDescription?.lowercase(Locale.getDefault())
        val typeNorm = walletTransaction.type?.trim()?.lowercase(Locale.getDefault())
        val backendType = walletTransaction.type?.trim()?.uppercase(Locale.getDefault())
        val statusNorm = walletTransaction.status?.trim()?.lowercase(Locale.getDefault())
        val amountValue = walletTransaction.amount ?: 0.0

        val backendUiType = when (backendType) {
            "BOOKING_FEE" -> TransactionType.PARKING_PAYMENT
            "BOOKING_REFUND" -> TransactionType.REFUND
            "WALLET_TOP_UP", "AD_TOP_UP" -> TransactionType.TOP_UP
            "REFUND" -> TransactionType.REFUND
            "PENALTY_FEE", "LATE_CHECK_IN_PENALTY", "LATE_CHECK_OUT_PENALTY" -> TransactionType.PARKING_PAYMENT
            else -> null
        }
        val backendBaseDescription = when (backendType) {
            "BOOKING_FEE" -> "Booking Fee"
            "BOOKING_REFUND" -> "Booking Refund"
            "WALLET_TOP_UP" -> "Wallet Top-up"
            "AD_TOP_UP" -> "Ad Top-up"
            "REFUND" -> "Refund"
            "PENALTY_FEE" -> "Penalty Fee"
            "LATE_CHECK_IN_PENALTY" -> "Late Check-in Penalty"
            "LATE_CHECK_OUT_PENALTY" -> "Late Check-out Penalty"
            else -> null
        }
        val backendIsCredit = when (backendType) {
            "BOOKING_REFUND", "WALLET_TOP_UP", "AD_TOP_UP", "REFUND" -> true
            "BOOKING_FEE", "PENALTY_FEE", "LATE_CHECK_IN_PENALTY", "LATE_CHECK_OUT_PENALTY" -> false
            else -> null
        }
        val backendIsBookingRelated = backendType in setOf(
            "BOOKING_FEE",
            "BOOKING_REFUND",
            "PENALTY_FEE",
            "LATE_CHECK_IN_PENALTY",
            "LATE_CHECK_OUT_PENALTY"
        )

        val isRewardByText = descriptionLower?.contains("reward") == true
        val isRewardByAmount = amountValue > 0 &&
            kotlin.math.abs(amountValue - REWARD_AMOUNT_RUPEES) < 0.01
        val isReward = isRewardByText || isRewardByAmount || typeNorm == "bonus"

        val isTopUpByType = typeNorm in listOf(
            "top_up",
            "topup",
            "wallet_topup",
            "wallet_recharge",
            "recharge",
            "top-up"
        )
        val isTopUpByText = descriptionLower?.let {
            it.contains("top up") ||
                it.contains("topup") ||
                it.contains("top-up") ||
                it.contains("recharge") ||
                it.contains("add money") ||
                it.contains("wallet top")
        } == true
        val isTopUp = isTopUpByType || isTopUpByText

        val isRefundByType = typeNorm?.contains("refund") == true ||
            typeNorm?.contains("reversal") == true ||
            typeNorm?.contains("cancel") == true ||
            typeNorm?.contains("return") == true ||
            typeNorm?.contains("adjust") == true
        val isRefundByText = descriptionLower?.let {
            it.contains("refund") ||
                it.contains("reversal") ||
                it.contains("cancel") ||
                it.contains("checkout") ||
                it.contains("check out") ||
                it.contains("check-out") ||
                it.contains("early") ||
                it.contains("unused") ||
                it.contains("overpaid") ||
                it.contains("adjust")
        } == true
        val isBookingRelated = descriptionLower?.let {
            it.contains("booking") ||
                it.contains("parking") ||
                it.contains("session") ||
                it.contains("check in") ||
                it.contains("check-in") ||
                it.contains("checkout") ||
                it.contains("check out") ||
                it.contains("check-out") ||
                it.contains("reservation") ||
                it.contains("slot")
        } == true || typeNorm?.contains("booking") == true || typeNorm?.contains("parking") == true

        val isDebit = typeNorm in listOf("debit", "payment", "penalty_deduction", "penalty", "charge") ||
            typeNorm?.contains("debit") == true
        val isCredit = typeNorm == "credit" || typeNorm?.contains("credit") == true

        val isRefund = isRefundByType || isRefundByText ||
            (!isReward && !isTopUp && isCredit) ||
            (isBookingRelated && !isReward && !isDebit && !isTopUpByText)
        val isBookingCharge = isBookingRelated && !isRefund
        val transactionType = backendUiType ?: when {
            isReward -> TransactionType.BONUS
            isRefund -> TransactionType.REFUND
            isTopUp -> TransactionType.TOP_UP
            isBookingCharge || isDebit -> TransactionType.PARKING_PAYMENT
            else -> TransactionType.TOP_UP
        }

        val displayAmount = if (backendUiType != null && backendIsCredit != null) {
            val absolute = kotlin.math.abs(amountValue)
            if (backendIsCredit) absolute else -absolute
        } else {
            when (transactionType) {
                TransactionType.PARKING_PAYMENT ->
                    if (amountValue > 0) -amountValue else amountValue
                else ->
                    if (amountValue < 0) -amountValue else amountValue
            }
        }

        val resolvedBookingRelated = if (backendUiType != null) backendIsBookingRelated else isBookingRelated
        val baseDescription = backendBaseDescription ?: when {
            isReward -> "Reward Added"
            transactionType == TransactionType.TOP_UP -> "Wallet Top-up"
            transactionType == TransactionType.PARKING_PAYMENT -> "Booking Charge"
            transactionType == TransactionType.REFUND && resolvedBookingRelated -> "Booking Refund"
            transactionType == TransactionType.REFUND -> "Refund"
            else -> "Wallet Top-up"
        }
        val description = when (statusNorm) {
            "failed" -> "$baseDescription Failed"
            "cancelled", "canceled" -> "$baseDescription Cancelled"
            else -> when {
                isReward -> baseDescription
                transactionType == TransactionType.REFUND ->
                    if (descriptionLower?.contains("refund") == true) normalizedDescription ?: baseDescription else baseDescription
                else -> normalizedDescription ?: baseDescription
            }
        }

        return Transaction(
            id = id,
            type = transactionType,
            amount = displayAmount,
            description = description,
            timestamp = parsedTimestamp,
            balanceAfter = walletTransaction.balanceAfter ?: 0.0,
            status = walletTransaction.status
        )
    }

    companion object {
        private const val REWARD_AMOUNT_RUPEES = 20.0
    }

    private fun getUserId(): String? {
        return AuthSession.getUserId(requireContext())
    }

    private fun showTopUpDialog() {
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Enter amount (₹)"

        AlertDialog.Builder(requireContext())
            .setTitle("Add Money")
            .setView(input)
            .setPositiveButton("Add") { dialog, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    startRazorpayCheckout(amount)
                } else {
                    showToast("Please enter a valid amount")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun processTopUp(amount: Double) {
        // Route all top-ups via Razorpay
        startRazorpayCheckout(amount)
    }

    private fun startRazorpayCheckout(amount: Double) {
        val userId = getUserId()
        if (userId == null) {
            showToast("Please login to add money")
            return
        }

        lifecycleScope.launch {
            try {
                val initResp = ApiClient.apiService.initiatePayment(
                    com.gridee.parking.data.model.PaymentInitiateRequest(
                        userId = userId,
                        amount = amount
                    )
                )

                if (!initResp.isSuccessful) {
                    showToast("Failed to initiate payment: ${initResp.code()}")
                    return@launch
                }

                val body = initResp.body()
                val orderId = body?.orderId
                val keyId = body?.keyId
                if (orderId.isNullOrBlank()) {
                    showToast("Invalid payment order from server")
                    return@launch
                }

                val intent = Intent(requireContext(), com.gridee.parking.ui.wallet.WalletTopUpActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("AMOUNT", amount)
                intent.putExtra("ORDER_ID", orderId)
                keyId?.let { intent.putExtra("KEY_ID", it) }
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            }
        }
    }

    private fun handleUnauthorized() {
        showToast("Session expired. Please log in again.")
        AuthSession.clearSession(requireContext())
        val intent = android.content.Intent(requireContext(), com.gridee.parking.ui.auth.LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}
