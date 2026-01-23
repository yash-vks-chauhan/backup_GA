package com.gridee.parking.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gridee.parking.ui.fragments.BookingsFragmentNew
import com.gridee.parking.ui.fragments.HomeFragment
import com.gridee.parking.ui.fragments.ProfileFragment
import com.gridee.parking.ui.fragments.WalletFragmentNew

class MainPagerAdapter(private val fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 4 // Home, Bookings, Wallet, Profile
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment() // Use the existing home fragment with search
            1 -> BookingsFragmentNew() // Use the new bookings fragment  
            2 -> WalletFragmentNew() // Use the new wallet fragment
            3 -> ProfileFragment() // Use existing profile fragment
            else -> HomeFragment()
        }
    }

    fun getCurrentFragment(position: Int): Fragment? {
        return fragmentActivity.supportFragmentManager.findFragmentByTag("f$position")
    }
}
