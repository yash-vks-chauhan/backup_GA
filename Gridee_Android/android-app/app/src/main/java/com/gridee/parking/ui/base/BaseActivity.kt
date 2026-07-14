package com.gridee.parking.ui.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.gridee.parking.ui.utils.configureEdgeToEdge

abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {
    
    private var _binding: T? = null
    protected val binding get() = _binding!!
    
    abstract fun getViewBinding(): T
    
    override fun onCreate(savedInstanceState: Bundle?) {
        configureEdgeToEdge()
        super.onCreate(savedInstanceState)
        _binding = getViewBinding()
        setContentView(binding.root)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
