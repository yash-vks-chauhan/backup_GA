package com.gridee.parking.ui.booking

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.databinding.ActivityParkingLotSelectionBinding
import com.gridee.parking.ui.discovery.ParkingDiscoveryViewModel
import com.gridee.parking.ui.discovery.ParkingLotAdapter

class ParkingLotSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkingLotSelectionBinding
    private lateinit var viewModel: ParkingDiscoveryViewModel
    private lateinit var adapter: ParkingLotAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParkingLotSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ParkingDiscoveryViewModel::class.java]
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        
        // Load parking lots
        viewModel.loadParkingData()
    }

    private fun setupUI() {
        binding.tvTitle.text = "Select Parking Location"
        binding.tvSubtitle.text = "Choose a parking lot to view available spots"
    }

    private fun setupRecyclerView() {
        adapter = ParkingLotAdapter { parkingLot ->
            onParkingLotSelected(parkingLot)
        }
        
        binding.recyclerViewParkingLots.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewParkingLots.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        viewModel.parkingLots.observe(this) { lots ->
            adapter.submitList(lots)
            
            if (lots.isEmpty()) {
                binding.emptyState.visibility = android.view.View.VISIBLE
                binding.recyclerViewParkingLots.visibility = android.view.View.GONE
            } else {
                binding.emptyState.visibility = android.view.View.GONE
                binding.recyclerViewParkingLots.visibility = android.view.View.VISIBLE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) 
                android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun onParkingLotSelected(parkingLot: ParkingLot) {
        // Navigate to spot selection for this lot
        val intent = Intent(this, ParkingSpotSelectionActivity::class.java)
        intent.putExtra("PARKING_LOT_ID", parkingLot.id)
        intent.putExtra("PARKING_LOT_NAME", parkingLot.name)
        startActivity(intent)
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
