package com.gridee.parking.ui.lot

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityChooseCategoryBinding
import com.gridee.parking.ui.views.SkeletonShimmer

/**
 * Step 1 of picking a parking location: choose a place. Categories are rows in a
 * grouped card (the Profile page's anatomy) whose 44dp tile is a live dial showing
 * how many spots are free across that category. Only categories that actually have
 * lots are shown; if there is just one, this step is skipped straight to the lots.
 *
 * Reuses [SelectParkingLotViewModel] to load all lots, then hands off to
 * [SelectParkingLotActivity] filtered by the chosen category — passing the tapped
 * dial's on-screen position so the next screen can carry it up into its header.
 */
class ChooseCategoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_ONBOARDING = "onboarding"
        const val MODE_CHANGE = "change"
        private const val EXTRA_FORWARD_EXTRAS = "extra_forward_extras"
        private const val KEY_AUTOSKIP = "autoskip_launched"

        /** Mandatory onboarding gate; [forwardExtras] flow through to the app after save. */
        fun onboardingIntent(context: Context, forwardExtras: Bundle? = null): Intent =
            Intent(context, ChooseCategoryActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_ONBOARDING)
                forwardExtras?.let { putExtra(EXTRA_FORWARD_EXTRAS, it) }
            }

        /** Change-lot flow from Profile; returns RESULT_OK once a lot is saved. */
        fun changeIntent(context: Context): Intent =
            Intent(context, ChooseCategoryActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_CHANGE)
            }
    }

    private lateinit var binding: ActivityChooseCategoryBinding
    private lateinit var viewModel: SelectParkingLotViewModel
    private lateinit var adapter: CategoryAdapter

    private var mode: String = MODE_ONBOARDING
    private var forwardExtras: Bundle? = null
    private var autoSkipLaunched = false
    private var skeletonBreath: ValueAnimator? = null

    private val lotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when {
            result.resultCode == RESULT_OK && mode == MODE_CHANGE -> {
                // A lot was saved — propagate up to Profile.
                setResult(RESULT_OK)
                finish()
            }
            result.resultCode == RESULT_OK -> {
                // Onboarding already routed into the app (this task was cleared).
            }
            autoSkipLaunched -> {
                // We skipped the category step; the user backed out of the lot page.
                // Don't strand them on an empty category screen.
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ONBOARDING
        forwardExtras = intent.getBundleExtra(EXTRA_FORWARD_EXTRAS)
        autoSkipLaunched = savedInstanceState?.getBoolean(KEY_AUTOSKIP) ?: false
        viewModel = ViewModelProvider(this)[SelectParkingLotViewModel::class.java]

        setupUi()
        setupObservers()
        if (savedInstanceState == null) viewModel.loadLots()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_AUTOSKIP, autoSkipLaunched)
    }

    private fun setupUi() {
        binding.btnBack.visibility = if (mode == MODE_CHANGE) View.VISIBLE else View.GONE
        binding.tvTitle.text =
            if (mode == MODE_CHANGE) "Change your parking lot" else "Choose your place"

        adapter = CategoryAdapter { category ->
            openLotsFor(category, allowBack = true)
        }
        binding.recyclerCategories.layoutManager = LinearLayoutManager(this)
        binding.recyclerCategories.itemAnimator = null
        binding.recyclerCategories.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.loadLots() }
    }

    private fun setupObservers() {
        viewModel.loading.observe(this) { loading ->
            if (loading) {
                showSkeleton()
                binding.recyclerCategories.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            } else {
                renderResult()
            }
        }
    }

    private fun renderResult() {
        // Keep the skeleton breathing while the auto-skipped lot page is in front.
        if (autoSkipLaunched) return

        val lots = viewModel.lots.value ?: emptyList()
        if (lots.isEmpty()) {
            hideSkeleton()
            binding.cardList.visibility = View.GONE
            binding.tvEmptyMessage.text =
                viewModel.loadError.value ?: "No parking locations are available yet."
            binding.emptyState.visibility = View.VISIBLE
            SkeletonShimmer.revealView(binding.emptyState)
            return
        }

        val categories = LotCategories.fromLots(lots)
        if (categories.size == 1) {
            // Only one category — skip this step straight to its lots.
            autoSkipLaunched = true
            openLotsFor(categories.first(), allowBack = mode == MODE_CHANGE)
        } else {
            adapter.submitList(categories) {
                hideSkeleton()
                binding.cardList.visibility = View.VISIBLE
                binding.recyclerCategories.visibility = View.VISIBLE
                SkeletonShimmer.revealStagger(binding.recyclerCategories)
            }
        }
    }

    /** Hands off to the lot list filtered by [category]. */
    private fun openLotsFor(category: LotCategory, allowBack: Boolean) {
        lotLauncher.launch(
            SelectParkingLotActivity.intentFor(
                context = this,
                mode = mode,
                category = category,
                allowBack = allowBack,
                forwardExtras = forwardExtras
            )
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun showSkeleton() {
        if (binding.skeletonContainer.childCount == 0) {
            LotSkeleton.populate(binding.skeletonContainer, 3)
        }
        binding.cardList.visibility = View.VISIBLE
        binding.skeletonContainer.visibility = View.VISIBLE
        if (skeletonBreath == null) {
            skeletonBreath = SkeletonShimmer.start(binding.skeletonContainer)
        }
    }

    private fun hideSkeleton() {
        skeletonBreath?.cancel()
        skeletonBreath = null
        binding.skeletonContainer.visibility = View.GONE
    }

    override fun onDestroy() {
        skeletonBreath?.cancel()
        skeletonBreath = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mode == MODE_CHANGE) {
            super.onBackPressed()
        } else {
            // Mandatory onboarding gate — a lot must be chosen.
            Toast.makeText(
                this,
                getString(R.string.please_choose_your_parking_location_to),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
