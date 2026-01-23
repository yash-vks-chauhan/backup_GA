package com.gridee.parking.ui.discovery

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.FragmentParkingListBinding
import com.gridee.parking.ui.details.ParkingDetailsActivity

class ParkingListFragment : Fragment() {

    private var _binding: FragmentParkingListBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ParkingDiscoveryViewModel
    private lateinit var adapter: ParkingListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParkingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[ParkingDiscoveryViewModel::class.java]
        
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = ParkingListAdapter { parkingSpot ->
            openParkingDetails(parkingSpot)
        }
        
        binding.recyclerViewParking.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewParking.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.parkingSpots.observe(viewLifecycleOwner) { spots ->
            adapter.submitList(spots)
            
            if (spots.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerViewParking.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerViewParking.visibility = View.VISIBLE
            }
        }
    }

    private fun openParkingDetails(parkingSpot: ParkingSpot) {
        val intent = Intent(requireContext(), ParkingDetailsActivity::class.java)
        intent.putExtra("PARKING_SPOT_ID", parkingSpot.id)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
