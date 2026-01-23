package com.gridee.parking.ui.fragments

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.FragmentHomeBinding
import com.gridee.parking.ui.adapters.ParkingSpotHomeAdapter
import com.gridee.parking.ui.MainViewModel
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.bottomsheet.ParkingSpotBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class HomeFragment : BaseTabFragment<FragmentHomeBinding>() {

    private lateinit var viewModel: MainViewModel
    private val parkingRepository = ParkingRepository()
    private lateinit var parkingSpotAdapter: ParkingSpotHomeAdapter
    private var isLoadingParkingSpots: Boolean = false
    private var allParkingSpots: List<ParkingSpot> = emptyList()
    private var currentSearchQuery: String? = null
    private var defaultSearchHint: String = "Find a parking spot..."
    private var autoRefreshJob: Job? = null
    private val autoRefreshIntervalMs: Long = 10_000L

    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()

        if (spokenText.isNullOrBlank()) {
            showToast("No speech detected")
            return@registerForActivityResult
        }

        applySearchQuery(spokenText)
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
        return FragmentHomeBinding.inflate(inflater, container, false)
    }

    override fun getScrollableView(): View? {
        return try {
            binding.scrollContent
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun setupUI() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        defaultSearchHint = binding.tvSearchHint.text?.toString()?.ifBlank { defaultSearchHint }
            ?: defaultSearchHint
        setupClickListeners()
        setupParkingSpots()
        refreshParkingSpots(showBlockingLoading = true)
        
        applyHeroGradient()
    }

    override fun onStart() {
        super.onStart()
        if (!isHidden) {
            startAutoRefresh()
        }
    }

    override fun onStop() {
        super.onStop()
        stopAutoRefresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopAutoRefresh()
        } else {
            startAutoRefresh()
        }
    }
    
    override fun onResume() {
        super.onResume()
        applyHeroGradient()
    }

    private fun applyHeroGradient() {
        try {
            val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
            val selected = sharedPref.getString("hero_gradient_key", "obsidian_dip")
            
            val drawableRes = when(selected) {
                "midnight_slate" -> com.gridee.parking.R.drawable.bg_home_hero_midnight_slate
                "obsidian_dip" -> com.gridee.parking.R.drawable.bg_home_hero_obsidian_dip
                "carbon_mist" -> com.gridee.parking.R.drawable.bg_home_hero_carbon_mist
                "lavender_whisper" -> com.gridee.parking.R.drawable.bg_home_hero_lavender_whisper
                else -> com.gridee.parking.R.drawable.bg_home_hero_obsidian_dip
            }
            
            binding.heroBackground.setBackgroundResource(drawableRes)
        } catch (e: Exception) {
            // Fallback safely
        }
    }

    private fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            refreshParkingSpots(showBlockingLoading = false)
            while (isActive) {
                delay(autoRefreshIntervalMs)
                refreshParkingSpots(showBlockingLoading = false)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun setupParkingSpots() {
        parkingSpotAdapter = ParkingSpotHomeAdapter { spot ->
            openParkingSpotBottomSheet(spot)
        }
        binding.recyclerParkingSpots.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = parkingSpotAdapter
            edgeEffectFactory = com.gridee.parking.ui.utils.BounceEdgeEffectFactory()
            
            // Add Scale Transformer for "Alive" feel
            // Add Scale Transformer for "Alive" feel
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                private var lastCenterPos = -1

                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    val mid = recyclerView.width / 2.0f
                    var nearestChild: View? = null
                    var minDistance = Float.MAX_VALUE

                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)
                        val childMid = (child.left + child.right) / 2.0f
                        val dist = abs(mid - childMid)
                        
                        // Track nearest for haptics
                        if (dist < minDistance) {
                            minDistance = dist
                            nearestChild = child
                        }

                        // NON-LINEAR Scaling for "Buttery" smooth pop
                        // Using a cosine-like curve or quadratic for smoother ease-in/out
                        val progress = (dist / recyclerView.width).coerceIn(0f, 1f)
                        val scaleFactor = 1f - (progress * progress * 0.15f) // Quadratic falloff
                        
                        val clampedScale = scaleFactor.coerceIn(0.90f, 1.0f)
                        
                        child.scaleX = clampedScale
                        child.scaleY = clampedScale
                        child.alpha = clampedScale.coerceIn(0.6f, 1.0f) // Deeper fade for focus
                    }

                    // Haptic "Tick" when snapping to a new item
                    val centerPos = nearestChild?.let { recyclerView.getChildAdapterPosition(it) } ?: -1
                    if (centerPos != lastCenterPos && centerPos != -1) {
                        if (lastCenterPos != -1) { // Don't tick on initial load
                            // Use CLOCK_TICK for the subtle "tick" feel (widely supported)
                            recyclerView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                        lastCenterPos = centerPos
                    }
                }
            })
            
            // Performance & Experience Tuning
            setHasFixedSize(true)
            setItemViewCacheSize(4) // Keep more items in memory for smoothness
            
            // Add iOS-like snappy scrolling physics
            val snapHelper = com.gridee.parking.ui.utils.IOSSnapHelper()
            onFlingListener = null // Clear existing listener if any
            snapHelper.attachToRecyclerView(this)
            
            // Remove any existing ItemDecorations if we added them previously (safe guard)
            // if (itemDecorationCount > 0) removeItemDecorationAt(0)
        }
        setupParkingSpotSwipeHandling()
    }

    private fun setupParkingSpotSwipeHandling() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val verticalDominance = 1.5f
        var startX = 0f
        var startY = 0f
        var hasGestureDirection = false

        binding.recyclerParkingSpots.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    hasGestureDirection = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (!hasGestureDirection && (dx > touchSlop || dy > touchSlop)) {
                        val isVerticalGesture = dy > dx * verticalDominance
                        view.parent?.requestDisallowInterceptTouchEvent(!isVerticalGesture)
                        hasGestureDirection = true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    hasGestureDirection = false
                }
            }
            false
        }
    }
    
    private fun openParkingSpotBottomSheet(spot: ParkingSpot) {
        // Match the logic in ParkingSpotHomeAdapter for consistent naming
        val lotName = spot.name?.takeIf { it.isNotBlank() }
            ?: spot.zoneName?.takeIf { it.isNotBlank() }
            ?: spot.spotCode?.takeIf { it.isNotBlank() }
            ?: spot.id
            ?: ""
        
        val sheet = ParkingSpotBottomSheet.newInstance(
            parkingSpotId = spot.id,
            parkingLotId = spot.lotId,
            parkingLotName = lotName
        )
        sheet.show(parentFragmentManager, ParkingSpotBottomSheet.TAG)
    }

    private fun refreshParkingSpots(showBlockingLoading: Boolean) {
        if (isLoadingParkingSpots) {
            if (!showBlockingLoading) {
                setParkingSpotsRefreshing(isRefreshing = false, showBlockingLoading = false)
            }
            return
        }
        isLoadingParkingSpots = true

        setParkingSpotsRefreshing(isRefreshing = true, showBlockingLoading = showBlockingLoading)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val spots = fetchAllParkingSpots()
                println("DEBUG HomeFragment.refreshParkingSpots: Fetched spots size=${spots.size}")
                binding.tvParkingSpotsTitle.text = "Nearby Parking"
                allParkingSpots = spots
                renderParkingSpotResults()
                println("DEBUG HomeFragment.refreshParkingSpots: Spots list updated with ${spots.size} spots")
            } catch (e: Exception) {
                println("DEBUG HomeFragment.refreshParkingSpots: Exception - ${e.message}")
                showEmptyState("Unable to load parking spots")
            } finally {
                isLoadingParkingSpots = false
                setParkingSpotsRefreshing(isRefreshing = false, showBlockingLoading = showBlockingLoading)
            }
        }
    }

    private fun setParkingSpotsRefreshing(isRefreshing: Boolean, showBlockingLoading: Boolean) {
        if (showBlockingLoading) {
            binding.progressParkingSpots.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            if (isRefreshing) {
                binding.recyclerParkingSpots.visibility = View.GONE
                binding.tvParkingSpotsEmpty.visibility = View.GONE
            }
        }
    }

    private fun startVoiceSearch() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            showToast("Voice search is not available on this device")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search parking spots")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        if (intent.resolveActivity(requireContext().packageManager) == null) {
            showToast("Voice search is not available")
            return
        }

        runCatching { voiceInputLauncher.launch(intent) }
            .onFailure { showToast("Unable to start voice search") }
    }

    private fun applySearchQuery(query: String) {
        val trimmed = query.trim()
        currentSearchQuery = trimmed.ifBlank { null }
        binding.tvSearchHint.text = if (trimmed.isBlank()) defaultSearchHint else trimmed
        if (isLoadingParkingSpots && allParkingSpots.isEmpty()) {
            return
        }
        renderParkingSpotResults()
    }

    private fun renderParkingSpotResults() {
        val query = currentSearchQuery?.trim().orEmpty()
        val filtered = filterParkingSpots(allParkingSpots, query)

        if (filtered.isEmpty()) {
            val message = if (query.isNotEmpty()) {
                "No parking spots match \"$query\""
            } else {
                "No parking spots available"
            }
            showEmptyState(message)
        } else {
            binding.progressParkingSpots.visibility = View.GONE
            binding.tvParkingSpotsEmpty.visibility = View.GONE
            binding.recyclerParkingSpots.visibility = View.VISIBLE
            parkingSpotAdapter.submitList(filtered)
        }

        val count = filtered.size
        binding.tvParkingSpotsSubtitle.text = if (query.isNotEmpty()) {
            "$count results for \"$query\""
        } else {
            "$count spots available near you"
        }
    }

    private fun filterParkingSpots(spots: List<ParkingSpot>, query: String): List<ParkingSpot> {
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isEmpty()) return spots

        return spots.filter { spot ->
            val candidates = listOf(
                spot.name,
                spot.zoneName,
                spot.lotName,
                spot.spotCode,
                spot.lotId
            )
            candidates.any { it?.lowercase(Locale.getDefault())?.contains(normalized) == true }
        }
    }

    private suspend fun fetchAllParkingSpots(): List<com.gridee.parking.data.model.ParkingSpot> {
        println("DEBUG HomeFragment.fetchAllParkingSpots: Starting to fetch all parking spots")
        
        // ✅ CURRENT WORKING SOLUTION: Fetch all spots directly
        try {
            val resp = parkingRepository.getParkingSpots()
            println("DEBUG HomeFragment.fetchAllParkingSpots: Primary endpoint status=${resp.code()}, success=${resp.isSuccessful}")
            
            if (resp.isSuccessful) {
                val spots = resp.body() ?: emptyList()
                println("DEBUG HomeFragment.fetchAllParkingSpots: Primary /api/parking-spots returned ${spots.size} spots")
                if (spots.isNotEmpty()) {
                    spots.take(3).forEach { spot ->
                        println("DEBUG HomeFragment.fetchAllParkingSpots: Sample spot - id=${spot.id}, name=${spot.name}, available=${spot.available}")
                    }
                    return spots
                }
                println("DEBUG HomeFragment.fetchAllParkingSpots: Primary endpoint returned empty")
            } else {
                val errorBody = resp.errorBody()?.string()
                println("DEBUG HomeFragment.fetchAllParkingSpots: Primary endpoint failed - status=${resp.code()}, error=$errorBody")
            }
        } catch (e: Exception) {
            println("DEBUG HomeFragment.fetchAllParkingSpots: Primary endpoint exception - ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }

        // Return empty if primary fails
        return emptyList()

        /* ========================================
         * 📦 OLD LOT-BASED AGGREGATION APPROACH
         * ========================================
         * This code fetches parking lots first, then aggregates spots from each lot.
         * Currently COMMENTED OUT because direct /api/parking-spots endpoint works.
         * Keeping this for reference in case we need lot-based filtering in future.
         * 
         * To re-enable: Uncomment this block and remove the "return emptyList()" above.
         */
        
        /*
        // Fallback: aggregate by lot
        println("DEBUG HomeFragment.fetchAllParkingSpots: Using lot-based aggregation fallback")
        return try {
            val lotsResp = parkingRepository.getParkingLots()
            if (!lotsResp.isSuccessful) {
                println("DEBUG HomeFragment.fetchAllParkingSpots: Failed to get lots - status=${lotsResp.code()}")
                return emptyList()
            }

            val lots = lotsResp.body() ?: emptyList()
            println("DEBUG HomeFragment.fetchAllParkingSpots: Got ${lots.size} parking lots")
            
            val combined = mutableListOf<com.gridee.parking.data.model.ParkingSpot>()

            for (lot in lots) {
                println("DEBUG HomeFragment.fetchAllParkingSpots: Processing lot: id=${lot.id}, name=${lot.name}")
                
                val attempts = listOf(lot.name, lot.id).filter { it.isNotBlank() }.distinct()
                var lotSpots: List<com.gridee.parking.data.model.ParkingSpot> = emptyList()

                for (key in attempts) {
                    try {
                        val resp = parkingRepository.getParkingSpotsByLot(key)
                        if (resp.isSuccessful) {
                            val body = resp.body() ?: emptyList()
                            println("DEBUG HomeFragment.fetchAllParkingSpots: Lot '$key' returned ${body.size} spots")
                            if (body.isNotEmpty()) {
                                lotSpots = body
                                break
                            }
                        } else {
                            val errorBody = resp.errorBody()?.string()
                            println("DEBUG HomeFragment.fetchAllParkingSpots: Lot '$key' API failed - status=${resp.code()}, error=$errorBody")
                        }
                    } catch (e: Exception) {
                        println("DEBUG HomeFragment.fetchAllParkingSpots: Lot '$key' exception - ${e.javaClass.simpleName}: ${e.message}")
                    }
                }

                combined.addAll(lotSpots)
            }

            println("DEBUG HomeFragment.fetchAllParkingSpots: Total aggregated spots: ${combined.size}")
            combined
        } catch (e: Exception) {
            println("DEBUG HomeFragment.fetchAllParkingSpots: Fallback exception - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
        */
    }

    private fun showEmptyState(message: String) {
        binding.progressParkingSpots.visibility = View.GONE
        binding.recyclerParkingSpots.visibility = View.GONE
        binding.tvParkingSpotsEmpty.text = message
        binding.tvParkingSpotsEmpty.visibility = View.VISIBLE
    }

    private fun setupClickListeners() {
        // Hero Animation Click Listener
        binding.heroAnimation.setOnClickListener {
            val bottomSheet = com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.newInstance(
                lottieFileName = "premium_crown.json",
                isRewardMode = true
            )
            bottomSheet.show(parentFragmentManager, com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.TAG)
        }

        binding.ivMicIcon.setOnClickListener {
            startVoiceSearch()
        }

        binding.searchBarContainer.setOnClickListener {
            val intent = Intent(requireContext(), com.gridee.parking.ui.search.SearchActivity::class.java)
            val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                androidx.core.util.Pair.create(binding.searchBarContainer, "search_bar_transition")
            )
            startActivity(intent, options.toBundle())
        }
    }

    companion object {
        fun newInstance(userName: String? = null): HomeFragment {
            val fragment = HomeFragment()
            userName?.let {
                val args = android.os.Bundle()
                args.putString("USER_NAME", it)
                fragment.arguments = args
            }
            return fragment
        }
    }
}
