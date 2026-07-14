package com.gridee.parking.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetTopUpBinding

class TopUpBottomSheetFragment : BottomSheetDialogFragment() {

    interface OnTopUpListener {
        fun onTopUpRequested(amount: Double)
    }

    private var _binding: BottomSheetTopUpBinding? = null
    private val binding get() = _binding!!
    
    private var currentBalance: Double = 0.0
    private var topUpListener: OnTopUpListener? = null

    companion object {
        fun newInstance(currentBalance: Double): TopUpBottomSheetFragment {
            val fragment = TopUpBottomSheetFragment()
            val args = Bundle()
            args.putDouble("current_balance", currentBalance)
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnTopUpListener(listener: OnTopUpListener) {
        this.topUpListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        currentBalance = arguments?.getDouble("current_balance", 0.0) ?: 0.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTopUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        // Set current balance
        updateBalanceDisplay(currentBalance)
    }

    private fun updateBalanceDisplay(balance: Double) {
        binding.tvCurrentBalance.text = "₹${String.format("%.2f", balance)}"
    }

    private fun setupClickListeners() {
        // Quick amount buttons
        binding.btnAmount50.setOnClickListener {
            binding.etAmount.setText("50.00")
        }
        
        binding.btnAmount100.setOnClickListener {
            binding.etAmount.setText("100.00")
        }
        
        binding.btnAmount200.setOnClickListener {
            binding.etAmount.setText("200.00")
        }

        binding.btnAmount500.setOnClickListener {
            binding.etAmount.setText("500.00")
        }



        // Close button
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // Add money button
        binding.btnAddMoneyConfirm.setOnClickListener {
            handleTopUp()
        }
    }

    private fun handleTopUp() {
        val amountText = binding.etAmount.text.toString().trim()
        
        if (amountText.isEmpty()) {
            Toast.makeText(context, "Please enter an amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = try {
            amountText.toDouble()
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount <= 0) {
            Toast.makeText(context, "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount < 10) {
            Toast.makeText(context, "Minimum top-up amount is ₹10", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount > 1000) {
            Toast.makeText(context, "Maximum top-up amount is ₹1000", Toast.LENGTH_SHORT).show()
            return
        }

        // Call the listener to handle the top-up
        topUpListener?.onTopUpRequested(amount)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
