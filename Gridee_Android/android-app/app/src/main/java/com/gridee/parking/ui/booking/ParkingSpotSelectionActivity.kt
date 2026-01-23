package com.gridee.parking.ui.booking

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ActivityParkingSpotSelectionBinding
import com.gridee.parking.ui.discovery.ParkingDiscoveryViewModel

class ParkingSpotSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkingSpotSelectionBinding
    private lateinit var viewModel: ParkingDiscoveryViewModel
    private lateinit var adapter: ParkingSpotSelectionAdapter
    private var lotId: String = ""
    private var lotName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParkingSpotSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get lot info from intent
        lotId = intent.getStringExtra("PARKING_LOT_ID") ?: ""
        lotName = intent.getStringExtra("PARKING_LOT_NAME") ?: "Parking Lot"

        viewModel = ViewModelProvider(this)[ParkingDiscoveryViewModel::class.java]
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        
        // Load parking spots for this specific lot
        viewModel.loadParkingSpotsForLot(lotId, lotName)
    }

    private fun setupUI() {
        binding.tvTitle.text = "Select Parking Spot"
        binding.tvSubtitle.text = "Choose a spot in $lotName"
    }

    private fun setupRecyclerView() {
        adapter = ParkingSpotSelectionAdapter { parkingSpot ->
            onParkingSpotSelected(parkingSpot)
        }
        
        binding.recyclerViewParkingSpots.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewParkingSpots.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        viewModel.parkingSpots.observe(this) { spots ->
            println("DEBUG ParkingSpotSelectionActivity: Received spots size=${spots.size}")
            // Spots are already filtered by lot ID in the ViewModel
            adapter.submitList(spots)
            
            if (spots.isEmpty()) {
                binding.emptyState.visibility = android.view.View.VISIBLE
                binding.recyclerViewParkingSpots.visibility = android.view.View.GONE
                println("DEBUG ParkingSpotSelectionActivity: Showing empty state")
            } else {
                // Ensure RecyclerView is ALWAYS visible when we have data
                binding.emptyState.visibility = android.view.View.GONE
                binding.recyclerViewParkingSpots.visibility = android.view.View.VISIBLE
                println("DEBUG ParkingSpotSelectionActivity: RecyclerView set to VISIBLE with ${spots.size} spots")
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) 
                android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun onParkingSpotSelected(parkingSpot: ParkingSpot) {
        // Navigate to booking flow with selected spot
        val intent = Intent(this, BookingFlowActivity::class.java)
        intent.putExtra("PARKING_SPOT_ID", parkingSpot.id)
        intent.putExtra("PARKING_LOT_ID", lotId)
        intent.putExtra("PARKING_LOT_NAME", lotName)
        startActivity(intent)
        
        // Close both selection activities to return to main screen
        finish()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
