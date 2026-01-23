package com.gridee.parking.ui.discovery

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ActivityParkingDiscoveryBinding
import com.gridee.parking.ui.base.BaseActivityWithBottomNav
import com.gridee.parking.ui.components.CustomBottomNavigation

class ParkingDiscoveryActivity : BaseActivityWithBottomNav<ActivityParkingDiscoveryBinding>() {

    private lateinit var viewModel: ParkingDiscoveryViewModel
    private var isMapView = true

    override fun getViewBinding(): ActivityParkingDiscoveryBinding {
        return ActivityParkingDiscoveryBinding.inflate(layoutInflater)
    }

    override fun getCurrentTab(): Int {
        return CustomBottomNavigation.TAB_HOME // This will be accessed from home
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ParkingDiscoveryViewModel::class.java]
        
        setupClickListeners()
        setupObservers()
        loadMapFragment()
    }

    override fun setupUI() {
        binding.tvTitle.text = "Find Parking"
        binding.etSearch.hint = "Search by location or address"
        
        // Set default view to map
        updateViewToggle()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString()
            if (query.isNotEmpty()) {
                viewModel.searchParking(query)
            }
        }

        binding.btnMapView.setOnClickListener {
            if (!isMapView) {
                isMapView = true
                updateViewToggle()
                loadMapFragment()
            }
        }

        binding.btnListView.setOnClickListener {
            if (isMapView) {
                isMapView = false
                updateViewToggle()
                loadListFragment()
            }
        }

        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.fabMyLocation.setOnClickListener {
            viewModel.getCurrentLocation()
        }
    }

    private fun setupObservers() {
        viewModel.parkingSpots.observe(this) { spots ->
            // Update both map and list with new data
            if (isMapView) {
                updateMapMarkers(spots)
            } else {
                updateListView(spots)
            }
        }

        viewModel.currentLocation.observe(this) { location ->
            // Center map on current location
            centerMapOnLocation(location)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) 
                android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun updateViewToggle() {
        if (isMapView) {
            binding.btnMapView.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, com.gridee.parking.R.color.brand_primary)
            )
            binding.btnMapView.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
            )
            binding.btnListView.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.transparent)
            )
            binding.btnListView.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, com.gridee.parking.R.color.brand_primary)
            )
        } else {
            binding.btnListView.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, com.gridee.parking.R.color.brand_primary)
            )
            binding.btnListView.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
            )
            binding.btnMapView.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.transparent)
            )
            binding.btnMapView.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, com.gridee.parking.R.color.brand_primary)
            )
        }
    }

    private fun loadMapFragment() {
        val fragment = ParkingMapFragment()
        supportFragmentManager.beginTransaction()
            .replace(com.gridee.parking.R.id.fragment_container, fragment)
            .commit()
    }

    private fun loadListFragment() {
        val fragment = ParkingListFragment()
        supportFragmentManager.beginTransaction()
            .replace(com.gridee.parking.R.id.fragment_container, fragment)
            .commit()
    }

    private fun showFilterDialog() {
        // TODO: Implement filter dialog
        showToast("Filter options - Coming Soon!")
    }

    private fun updateMapMarkers(spots: List<ParkingSpot>) {
        // TODO: Update map markers with real parking spot data
        showToast("Found ${spots.size} parking spots")
    }

    private fun updateListView(spots: List<ParkingSpot>) {
        // TODO: Update list view with real parking spot data
        showToast("Found ${spots.size} parking spots")
    }

    private fun centerMapOnLocation(location: Any) {
        // TODO: Center map on location
    }
}
