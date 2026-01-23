package com.gridee.parking.ui.profile

import android.graphics.Color
import android.os.Bundle
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.databinding.ActivityHelpSupportBinding
import com.gridee.parking.ui.base.BaseActivity

class HelpSupportActivity : BaseActivity<ActivityHelpSupportBinding>() {

    override fun getViewBinding(): ActivityHelpSupportBinding {
        return ActivityHelpSupportBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#F5F5F5")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnBackToProfile.setOnClickListener {
            finish()
        }
    }
}

