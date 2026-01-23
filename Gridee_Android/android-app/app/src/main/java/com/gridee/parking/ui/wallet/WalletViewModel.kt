package com.gridee.parking.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.WalletDetails
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.data.repository.WalletRepository
import kotlinx.coroutines.launch

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    
    private val walletRepository = WalletRepository(application)
    
    private val _walletDetails = MutableLiveData<WalletDetails>()
    val walletDetails: LiveData<WalletDetails> = _walletDetails
    
    private val _transactions = MutableLiveData<List<WalletTransaction>>()
    val transactions: LiveData<List<WalletTransaction>> = _transactions
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _topupSuccess = MutableLiveData<Boolean>()
    val topupSuccess: LiveData<Boolean> = _topupSuccess
    
    init {
        loadWalletData()
    }
    
    fun loadWalletData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            println("WalletViewModel: Loading wallet data")
            
            // Load wallet details
            walletRepository.getWalletDetails().fold(
                onSuccess = { details ->
                    println("WalletViewModel: Wallet details loaded - Balance: ${details.balance}")
                    _walletDetails.value = details
                    _transactions.value = details.transactions
                },
                onFailure = { exception ->
                    println("WalletViewModel: Failed to load wallet details: ${exception.message}")
                    _error.value = exception.message
                }
            )
            
            _isLoading.value = false
        }
    }
    
    fun topUpWallet(amount: Double) {
        if (amount <= 0) {
            _error.value = "Please enter a valid amount"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            println("WalletViewModel: Initiating topup for amount: $amount")
            
            walletRepository.topUpWallet(amount).fold(
                onSuccess = { result ->
                    println("WalletViewModel: Topup successful: $result")
                    _topupSuccess.value = true
                    // Reload wallet data to get updated balance
                    loadWalletData()
                },
                onFailure = { exception ->
                    println("WalletViewModel: Topup failed: ${exception.message}")
                    _error.value = "Topup failed: ${exception.message}"
                    _isLoading.value = false
                }
            )
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearTopupSuccess() {
        _topupSuccess.value = false
    }
}
