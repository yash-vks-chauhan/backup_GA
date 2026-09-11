package com.gridee.parking.ui.lot

import com.gridee.parking.R

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.databinding.ActivitySelectParkingLotBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.motion.AnimatorSettingsCompat
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.utils.AppForegroundTracker
import com.gridee.parking.utils.AuthSession

/**
 * Lets the user pick their parking lot. Rows are quiet identity lines (live dial +
 * name + place); the chosen row gets the brand check and opens like an accordion to
 * reveal its detail, while the slim glass dock at the bottom echoes the name and
 * holds the confirm button, which solidifies from glass to brand once a choice
 * exists. Used in two modes:
 *  - ONBOARDING: a mandatory gate right after sign-up / for an existing user with no
 *    lot. Cannot be skipped; on save it enters the app (MainContainerActivity).
 *  - CHANGE: opened from Profile to switch lots; on save it returns RESULT_OK.
 */
class SelectParkingLotActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_ONBOARDING = "onboarding"
        const val MODE_CHANGE = "change"
        const val EXTRA_ORG_TYPE = "extra_org_type"
        const val EXTRA_ORGANIZATION_ID = "extra_organization_id"
        const val EXTRA_ORGANIZATION_NAME = "extra_organization_name"
        const val EXTRA_LOCATION_ID = "extra_location_id"
        const val EXTRA_LOCATION_NAME = "extra_location_name"
        const val EXTRA_ALLOW_BACK = "extra_allow_back"
        private const val EXTRA_CATEGORY_LABEL = "extra_category_label"
        private const val EXTRA_CATEGORY_TOTAL = "extra_category_total"
        private const val EXTRA_CATEGORY_AVAILABLE = "extra_category_available"
        private const val EXTRA_CATEGORY_COUNT = "extra_category_count"
        private const val EXTRA_FORWARD_EXTRAS = "extra_forward_extras"
        private const val KEY_SELECTED_LOT_ID = "key_selected_lot_id"

        private const val DOCK_REVEAL_MS = 220L
        // Accordion expand (280ms) + a small buffer before measuring overlap.
        private const val ROW_SETTLE_MS = 320L

        /**
         * @param category the chosen place category — its label titles this screen and
         *   its live totals seed the subtitle before the filtered lots arrive.
         * @param allowBack whether the user may go back (true when a category step
         *   precedes this, or in change mode). When false in onboarding mode, back is
         *   blocked so the mandatory gate can't be skipped.
         */
        fun intentFor(
            context: Context,
            mode: String,
            category: LotCategory,
            allowBack: Boolean,
            forwardExtras: Bundle? = null
        ): Intent = Intent(context, SelectParkingLotActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_ORG_TYPE, category.type)
            putExtra(EXTRA_ALLOW_BACK, allowBack)
            putExtra(EXTRA_CATEGORY_LABEL, category.label)
            putExtra(EXTRA_CATEGORY_TOTAL, category.totalSpots)
            putExtra(EXTRA_CATEGORY_AVAILABLE, category.availableSpots)
            putExtra(EXTRA_CATEGORY_COUNT, category.count)
            forwardExtras?.let { putExtra(EXTRA_FORWARD_EXTRAS, it) }
        }

        fun intentForTenant(
            context: Context,
            mode: String,
            organizationId: String,
            organizationName: String,
            organizationType: String?,
            locationId: String,
            locationName: String,
            allowBack: Boolean,
            forwardExtras: Bundle? = null,
        ): Intent = Intent(context, SelectParkingLotActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_ORGANIZATION_ID, organizationId)
            putExtra(EXTRA_ORGANIZATION_NAME, organizationName)
            putExtra(EXTRA_ORG_TYPE, organizationType)
            putExtra(EXTRA_LOCATION_ID, locationId)
            putExtra(EXTRA_LOCATION_NAME, locationName)
            putExtra(EXTRA_ALLOW_BACK, allowBack)
            putExtra(EXTRA_CATEGORY_LABEL, locationName)
            forwardExtras?.let { putExtra(EXTRA_FORWARD_EXTRAS, it) }
        }
    }

    private lateinit var binding: ActivitySelectParkingLotBinding
    private lateinit var viewModel: SelectParkingLotViewModel
    private lateinit var adapter: SelectLotAdapter

    private var mode: String = MODE_ONBOARDING
    private var organizationType: String? = null
    private var organizationId: String? = null
    private var locationId: String? = null
    private var locationName: String? = null
    private var allowBack: Boolean = false
    private var selectedLot: ParkingLot? = null
    private var currentLotId: String? = null
    private var pendingSelectId: String? = null
    private var skeletonBreath: ValueAnimator? = null
    private var handledForegroundGeneration = Long.MIN_VALUE

    // easeOutCubic — the app's content-reveal curve.
    private val smoothDecelerate = PathInterpolator(0.33f, 1f, 0.68f, 1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectParkingLotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ONBOARDING
        organizationType = intent.getStringExtra(EXTRA_ORG_TYPE)
        organizationId = intent.getStringExtra(EXTRA_ORGANIZATION_ID)?.trim()?.takeIf(String::isNotEmpty)
        locationId = intent.getStringExtra(EXTRA_LOCATION_ID)?.trim()?.takeIf(String::isNotEmpty)
        locationName = intent.getStringExtra(EXTRA_LOCATION_NAME)?.trim()?.takeIf(String::isNotEmpty)
        allowBack = intent.getBooleanExtra(EXTRA_ALLOW_BACK, false)
        pendingSelectId = savedInstanceState?.getString(KEY_SELECTED_LOT_ID)
        viewModel = ViewModelProvider(this)[SelectParkingLotViewModel::class.java]

        setupUi()
        setupObservers()
        handledForegroundGeneration = AppForegroundTracker.currentGeneration()
        loadScopedLots()
    }

    override fun onResume() {
        super.onResume()
        val generation = AppForegroundTracker.currentGeneration()
        if (generation == handledForegroundGeneration) return
        handledForegroundGeneration = generation
        // This is non-forced: the repository serves the current list immediately and only goes
        // to the network when its five-minute parking-lot TTL has actually expired.
        loadScopedLots()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Keep the choice across rotation / process death; re-applied once lots load.
        selectedLot?.let { outState.putString(KEY_SELECTED_LOT_ID, it.id) }
    }

    private fun setupUi() {
        val isChange = mode == MODE_CHANGE
        binding.btnBack.visibility = if (allowBack) View.VISIBLE else View.GONE
        if (isChange) currentLotId = AuthSession.getParkingLotId(this)

        // The category's own name titles this page, and the subtitle is seeded with
        // the same live numbers the index row showed, so the page is truthful before
        // the filtered lots arrive.
        binding.tvTitle.text = locationName ?: intent.getStringExtra(EXTRA_CATEGORY_LABEL)
            ?: if (isChange) "Change your parking lot" else "Select your parking lot"
        binding.tvSubtitle.text = subtitleFor(
            available = intent.getIntExtra(EXTRA_CATEGORY_AVAILABLE, 0),
            total = intent.getIntExtra(EXTRA_CATEGORY_TOTAL, 0),
            locations = intent.getIntExtra(EXTRA_CATEGORY_COUNT, 0)
        )
        binding.btnConfirm.text = if (isChange) "Save" else "Continue"

        adapter = SelectLotAdapter(currentLotId) { lot, row ->
            selectedLot = lot
            updateConfirm()
            showDockFor(lot, fromTap = true)
            keepRowVisibleAboveDock(row)
        }
        binding.recyclerLots.layoutManager = LinearLayoutManager(this)
        binding.recyclerLots.itemAnimator = null
        binding.recyclerLots.adapter = adapter

        // The list must always be scrollable past the floating glass dock.
        binding.dock.addOnLayoutChangeListener { dock, _, _, _, _, _, _, _, _ ->
            val margin = (28 * resources.displayMetrics.density).toInt()
            binding.scrollContent.updatePadding(bottom = dock.height + margin)
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener {
            loadScopedLots(manualRefresh = true)
        }
        binding.btnConfirm.setOnClickListener {
            selectedLot?.let { viewModel.assignLot(this, it) }
        }
    }

    private fun loadScopedLots(manualRefresh: Boolean = false) {
        viewModel.loadLots(
            organizationType = organizationType,
            organizationId = organizationId,
            locationId = locationId,
            manualRefresh = manualRefresh,
        )
    }

    private fun subtitleFor(available: Int, total: Int, locations: Int): String {
        val places = if (locations == 1) "1 location" else "$locations locations"
        val head = when {
            total <= 0 || locations <= 0 -> "Choose the lot you park at."
            available <= 0 -> "No spots free right now across $places."
            else -> "$available of $total spots free right now across $places."
        }
        val tail = if (mode == MODE_CHANGE) {
            "Your spots and bookings follow the lot you pick."
        } else {
            "You can change this anytime in your profile."
        }
        return "$head $tail"
    }

    private fun updateConfirm() {
        val lot = selectedLot
        // Save is enabled only once a lot is chosen that differs from the current one.
        binding.btnConfirm.isEnabled = lot != null && lot.id != currentLotId
    }

    // ---- Choice dock ----

    /**
     * Echo the chosen lot's name in the slim dock. The row's accordion carries the
     * detail; the dock only confirms what the button below will commit.
     */
    private fun showDockFor(lot: ParkingLot, fromTap: Boolean) {
        val motion = fromTap && AnimatorSettingsCompat.areEnabled(this)
        setDockName(lot.name, animate = motion)
        binding.dockChipCurrent.visibility =
            if (!currentLotId.isNullOrBlank() && lot.id == currentLotId) View.VISIBLE else View.GONE
        revealDockContent(animate = motion)
    }

    /** Cross-fade the name when the choice changes; set it plainly otherwise. */
    private fun setDockName(name: String, animate: Boolean) {
        val view = binding.dockName
        view.animate().cancel()
        if (!animate || binding.dockContent.visibility != View.VISIBLE || view.text == name) {
            view.text = name
            view.alpha = 1f
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(DOCK_REVEAL_MS / 2)
            .withEndAction {
                view.text = name
                view.animate()
                    .alpha(1f)
                    .setDuration(DOCK_REVEAL_MS / 2)
                    .setInterpolator(smoothDecelerate)
                    .start()
            }
            .start()
    }

    /** First selection swaps the "choose a lot" hint for the chosen name. */
    private fun revealDockContent(animate: Boolean) {
        if (binding.dockContent.visibility == View.VISIBLE) return
        binding.dockHint.visibility = View.GONE
        binding.dockContent.visibility = View.VISIBLE
        if (!animate) return
        binding.dockContent.alpha = 0f
        binding.dockContent.translationY = 8 * resources.displayMetrics.density
        binding.dockContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DOCK_REVEAL_MS)
            .setInterpolator(smoothDecelerate)
            .start()
    }

    /**
     * If the tapped row ends up under the floating dock once its accordion has
     * opened, scroll it back into view. Waits out the expansion so the measured
     * overlap includes the revealed detail.
     */
    private fun keepRowVisibleAboveDock(row: View) {
        binding.dock.postDelayed({
            if (!row.isAttachedToWindow) return@postDelayed
            val rowLoc = IntArray(2)
            val dockLoc = IntArray(2)
            row.getLocationInWindow(rowLoc)
            binding.dock.getLocationInWindow(dockLoc)
            val margin = (8 * resources.displayMetrics.density).toInt()
            val overlap = rowLoc[1] + row.height - (dockLoc[1] - margin)
            if (overlap > 0) binding.scrollContent.smoothScrollBy(0, overlap)
        }, ROW_SETTLE_MS)
    }

    // ---- Data ----

    private fun setupObservers() {
        viewModel.lots.observe(this) { lots ->
            adapter.submitList(lots) {
                if (lots.isEmpty()) return@submitList
                hideSkeleton()
                binding.cardList.visibility = View.VISIBLE
                binding.recyclerLots.visibility = View.VISIBLE
                binding.dock.visibility = View.VISIBLE
                SkeletonShimmer.revealStagger(binding.recyclerLots)

                // Refresh the subtitle from the lots actually in this category.
                val total = lots.sumOf { it.totalSpots.coerceAtLeast(0) }
                val available = lots.sumOf { it.availableSpots.coerceAtLeast(0) }
                binding.tvSubtitle.text = subtitleFor(available, total, lots.size)

                // Re-apply a rotation-restored choice, else pre-select the current
                // lot in change mode. Inside the commit callback so the diffed list
                // is queryable.
                if (selectedLot == null) {
                    val restoreId = pendingSelectId ?: currentLotId
                    pendingSelectId = null
                    lots.firstOrNull { it.id == restoreId }?.let { restored ->
                        selectedLot = restored
                        adapter.setSelectedLotId(restored.id)
                        updateConfirm()
                        showDockFor(restored, fromTap = false)
                    }
                }
            }
        }

        viewModel.loading.observe(this) { loading ->
            if (loading) {
                showSkeleton()
                binding.recyclerLots.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            } else if (viewModel.lots.value.isNullOrEmpty()) {
                hideSkeleton()
                binding.cardList.visibility = View.GONE
            }
        }

        viewModel.loadError.observe(this) { error ->
            if (error != null && (viewModel.lots.value?.isEmpty() != false)) {
                binding.tvEmptyMessage.text = error
                binding.emptyState.visibility = View.VISIBLE
                binding.dock.visibility = View.GONE
                SkeletonShimmer.revealView(binding.emptyState)
            } else {
                binding.emptyState.visibility = View.GONE
            }
        }

        viewModel.saveState.observe(this) { state ->
            when (state) {
                is SelectParkingLotViewModel.SaveState.Saving -> {
                    binding.btnConfirm.isEnabled = false
                    binding.btnConfirm.text = getString(R.string.saving)
                }
                is SelectParkingLotViewModel.SaveState.Success -> onSaved()
                is SelectParkingLotViewModel.SaveState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    updateConfirm()
                    binding.btnConfirm.text = if (mode == MODE_CHANGE) "Save" else "Continue"
                    viewModel.clearSaveError()
                }
                else -> Unit
            }
        }
    }

    private fun showSkeleton() {
        if (binding.skeletonContainer.childCount == 0) {
            LotSkeleton.populate(binding.skeletonContainer, 4)
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

    private fun onSaved() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.btnConfirm.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        if (mode == MODE_CHANGE) {
            Toast.makeText(this, getString(R.string.parking_location_updated), Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
            return
        }
        // Onboarding: enter the app, forwarding any extras intended for the home screen.
        val next = Intent(this, MainContainerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.getBundleExtra(EXTRA_FORWARD_EXTRAS)?.let { putExtras(it) }
        }
        startActivity(next)
        finish()
    }

    override fun onDestroy() {
        skeletonBreath?.cancel()
        skeletonBreath = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (allowBack || mode == MODE_CHANGE) {
            // A category step precedes this (or we're changing lots): go back to it.
            super.onBackPressed()
        } else {
            // Mandatory onboarding gate with no earlier step — can't skip choosing a lot.
            Toast.makeText(
                this,
                getString(R.string.please_choose_your_parking_location_to),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
