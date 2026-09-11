package com.gridee.parking.ui.discovery

import com.gridee.parking.R

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ActivityParkingDiscoveryBinding
import com.gridee.parking.ui.base.BaseActivityWithBottomNav
import com.gridee.parking.ui.components.CustomBottomNavigation

class ParkingDiscoveryActivity : BaseActivityWithBottomNav<ActivityParkingDiscoveryBinding>() {

    private companion object {
        const val STATE_IS_MAP_VIEW = "parking_discovery.is_map_view"
        const val MAP_FRAGMENT_TAG = "gridee.discovery.map"
        const val LIST_FRAGMENT_TAG = "gridee.discovery.list"
    }

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
        // Base redirected to login (no auth session) and finished; binding is null.
        if (!isViewReady) return
        isMapView = savedInstanceState?.getBoolean(STATE_IS_MAP_VIEW, true) ?: true
        viewModel = ViewModelProvider(this)[ParkingDiscoveryViewModel::class.java]
        
        setupClickListeners()
        setupObservers()
        updateViewToggle()
        showSelectedFragment()
    }

    override fun setupUI() {
        binding.tvTitle.text = getString(R.string.find_parking)
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
        showFragment(
            stableTag = MAP_FRAGMENT_TAG,
            fragmentClass = ParkingMapFragment::class.java,
            factory = ::ParkingMapFragment,
        )
    }

    private fun loadListFragment() {
        showFragment(
            stableTag = LIST_FRAGMENT_TAG,
            fragmentClass = ParkingListFragment::class.java,
            factory = ::ParkingListFragment,
        )
    }

    private fun showSelectedFragment() {
        if (isMapView) loadMapFragment() else loadListFragment()
    }

    private fun showFragment(
        stableTag: String,
        fragmentClass: Class<out Fragment>,
        factory: () -> Fragment,
    ) {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (current?.javaClass == fragmentClass) return
        if (supportFragmentManager.isStateSaved) return

        // FragmentManager restores the screen during super.onCreate(). Prefer its stable tag;
        // exact-class fallback preserves state saved by app versions that predate these tags.
        val fragment = supportFragmentManager.findFragmentByTag(stableTag)
            ?.takeIf {
                it.isAdded &&
                    !it.isRemoving &&
                    it.id == R.id.fragment_container &&
                    it.javaClass == fragmentClass
            }
            ?: supportFragmentManager.fragments.lastOrNull {
                it.isAdded &&
                    !it.isRemoving &&
                    it.id == R.id.fragment_container &&
                    it.javaClass == fragmentClass
            }
            ?: factory()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment, stableTag)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_IS_MAP_VIEW, isMapView)
        super.onSaveInstanceState(outState)
    }

    private fun showFilterDialog() {
        // TODO: Implement filter dialog
        showToast(getString(R.string.filter_options_coming_soon))
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
