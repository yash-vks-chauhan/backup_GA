package com.gridee.parking.ui.discovery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.FragmentParkingMapBinding

class ParkingMapFragment : Fragment() {

    private var _binding: FragmentParkingMapBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ParkingDiscoveryViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParkingMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[ParkingDiscoveryViewModel::class.java]
        
        setupMapView()
        observeViewModel()
    }

    private fun setupMapView() {
        // TODO: Initialize map (Google Maps or OpenStreetMap)
        // For now, show a placeholder
        binding.tvMapPlaceholder.text = "🗺️ Interactive Map View\n\nShowing parking locations with:\n• Real-time availability\n• Price indicators\n• Distance markers\n• Filter results"
    }

    private fun observeViewModel() {
        viewModel.parkingSpots.observe(viewLifecycleOwner) { spots ->
            updateMapMarkers(spots)
        }

        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            centerMapOnLocation(location)
        }
    }

    private fun updateMapMarkers(spots: List<ParkingSpot>) {
        // TODO: Update map markers with parking spots
        val availableCount = spots.count { it.available > 0 }
        binding.tvMapInfo.text = "Found ${spots.size} parking locations\n$availableCount currently available"
    }

    private fun centerMapOnLocation(location: Location) {
        // TODO: Center map on user's location
        binding.tvLocationInfo.text = "📍 Current: ${location.address}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
