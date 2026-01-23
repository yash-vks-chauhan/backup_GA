package com.gridee.parking.ui.adapters

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gridee.parking.databinding.FragmentMainPageBinding
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.AuthSession

class MainPageFragment : Fragment() {
    
    private var _binding: FragmentMainPageBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var pageType: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageType = arguments?.getString(ARG_PAGE_TYPE) ?: "home"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainPageBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPage()
    }
    
    private fun setupPage() {
        when (pageType) {
            "bookings" -> setupBookingsPage()
            "wallet" -> setupWalletPage() 
            "profile" -> setupProfilePage()
            else -> setupHomePage()
        }
    }
    
    private fun setupBookingsPage() {
        binding.tvPageTitle.text = "My Bookings"
        binding.tvPageDescription.text = "Manage your parking bookings"
        
        // Add booking-specific content
        binding.btnPrimaryAction.text = "View All Bookings"
        binding.btnPrimaryAction.setOnClickListener {
            val intent = Intent(requireContext(), MainContainerActivity::class.java)
            intent.putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_BOOKINGS)
            startActivity(intent)
        }
        
        binding.btnSecondaryAction.text = "Book New Parking"
        binding.btnSecondaryAction.setOnClickListener {
            try {
                val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.booking.ParkingLotSelectionActivity"))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Booking feature coming soon!")
            }
        }
    }
    
    private fun setupWalletPage() {
        binding.tvPageTitle.text = "My Wallet"
        binding.tvPageDescription.text = "Manage your payments and transactions"
        
        binding.btnPrimaryAction.text = "View Transactions"
        binding.btnPrimaryAction.setOnClickListener {
            try {
                val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.activities.TransactionHistoryActivity"))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Transaction history coming soon!")
            }
        }
        
        binding.btnSecondaryAction.text = "Add Payment Method"
        binding.btnSecondaryAction.setOnClickListener {
            showToast("Add payment method coming soon!")
        }
    }
    
    private fun setupProfilePage() {
        binding.tvPageTitle.text = "My Profile"
        
        // Get user info
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        val userName = sharedPref.getString("user_name", "User") ?: "User"
        binding.tvPageDescription.text = "Welcome, $userName"
        
        binding.btnPrimaryAction.text = "Edit Profile"
        binding.btnPrimaryAction.setOnClickListener {
            try {
                val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.profile.EditProfileActivity"))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Edit profile coming soon!")
            }
        }
        
        binding.btnSecondaryAction.text = "Logout"
        binding.btnSecondaryAction.setOnClickListener {
            logout()
        }
    }
    
    private fun setupHomePage() {
        binding.tvPageTitle.text = "Welcome to Gridee"
        binding.tvPageDescription.text = "Find and book parking spots easily"
        
        binding.btnPrimaryAction.text = "Find Parking"
        binding.btnPrimaryAction.setOnClickListener {
            try {
                val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.discovery.ParkingDiscoveryActivity"))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Find parking coming soon!")
            }
        }
        
        binding.btnSecondaryAction.text = "Quick Book"
        binding.btnSecondaryAction.setOnClickListener {
            try {
                val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.booking.ParkingLotSelectionActivity"))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Quick book coming soon!")
            }
        }
    }
    
    private fun logout() {
        AuthSession.clearSession(requireContext())
        
        try {
            val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.auth.LoginActivity"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        } catch (e: Exception) {
            showToast("Unable to logout at this time")
        }
    }
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_PAGE_TYPE = "page_type"
        
        fun newInstance(pageType: String): MainPageFragment {
            return MainPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_TYPE, pageType)
                }
            }
        }
    }
}
