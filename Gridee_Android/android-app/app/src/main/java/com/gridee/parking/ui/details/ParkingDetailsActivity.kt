package com.gridee.parking.ui.details

import com.gridee.parking.R

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.databinding.ActivityParkingDetailsBinding

class ParkingDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkingDetailsBinding
    private lateinit var viewModel: ParkingDetailsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParkingDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ParkingDetailsViewModel::class.java]

        val parkingSpotId = intent.getStringExtra("PARKING_SPOT_ID") ?: ""
        
        setupUI()
        setupClickListeners()
        loadParkingDetails(parkingSpotId)
    }

    private fun setupUI() {
        binding.tvTitle.text = getString(R.string.parking_details)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnBookNow.setOnClickListener {
            // TODO: Navigate to booking screen
            showToast(getString(R.string.booking_feature_coming_soon))
        }
    }

    private fun loadParkingDetails(spotId: String) {
        // TODO: Load actual parking details
        binding.tvDetailsPlaceholder.text = "🅿️ Detailed Parking Information\n\nSpot ID: $spotId\n\n• Location details & photos\n• Pricing information\n• Available spots count\n• Amenities overview\n• User reviews & ratings\n• Real-time availability"
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
