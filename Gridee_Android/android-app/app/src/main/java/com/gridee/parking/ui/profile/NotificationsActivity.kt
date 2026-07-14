package com.gridee.parking.ui.profile

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityNotificationsBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.ThemeManager
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter

class NotificationsActivity : BaseActivity<ActivityNotificationsBinding>() {

    override fun getViewBinding(): ActivityNotificationsBinding {
        return ActivityNotificationsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !ThemeManager.isDarkMode(this)

        binding.btnBackToProfile.setOnClickListener {
            finish()
        }

        if (ThemeManager.isDarkMode(this)) {
            val whiteColor = android.graphics.Color.WHITE
            binding.lottieNotification.addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR_FILTER,
                { SimpleColorFilter(whiteColor) }
            )
        }
    }
}

