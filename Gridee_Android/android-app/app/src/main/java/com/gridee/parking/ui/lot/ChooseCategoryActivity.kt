package com.gridee.parking.ui.lot

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityChooseCategoryBinding
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.utils.AppForegroundTracker

/** Mandatory organization -> location -> parking-lot picker. */
class ChooseCategoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_ONBOARDING = "onboarding"
        const val MODE_CHANGE = "change"
        private const val EXTRA_FORWARD_EXTRAS = "extra_forward_extras"
        private const val STATE_STAGE = "tenant_picker.stage"
        private const val STATE_ORGANIZATION_ID = "tenant_picker.organization_id"
        private const val STATE_ORGANIZATION_NAME = "tenant_picker.organization_name"
        private const val STATE_ORGANIZATION_TYPE = "tenant_picker.organization_type"

        fun onboardingIntent(context: Context, forwardExtras: Bundle? = null): Intent =
            Intent(context, ChooseCategoryActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_ONBOARDING)
                forwardExtras?.let { putExtra(EXTRA_FORWARD_EXTRAS, it) }
            }

        fun changeIntent(context: Context): Intent =
            Intent(context, ChooseCategoryActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_CHANGE)
            }
    }

    private enum class Stage { ORGANIZATION, LOCATION }

    private lateinit var binding: ActivityChooseCategoryBinding
    private lateinit var viewModel: SelectParkingLotViewModel
    private lateinit var adapter: TenantOptionAdapter
    private var mode = MODE_ONBOARDING
    private var forwardExtras: Bundle? = null
    private var stage = Stage.ORGANIZATION
    private var organizationId: String? = null
    private var organizationName: String? = null
    private var organizationType: String? = null
    private var skeletonBreath: ValueAnimator? = null
    private var handledForegroundGeneration = Long.MIN_VALUE

    private val lotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && mode == MODE_CHANGE) {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ONBOARDING
        forwardExtras = intent.getBundleExtra(EXTRA_FORWARD_EXTRAS)
        stage = savedInstanceState?.getString(STATE_STAGE)
            ?.let { runCatching { Stage.valueOf(it) }.getOrNull() }
            ?: Stage.ORGANIZATION
        organizationId = savedInstanceState?.getString(STATE_ORGANIZATION_ID)
        organizationName = savedInstanceState?.getString(STATE_ORGANIZATION_NAME)
        organizationType = savedInstanceState?.getString(STATE_ORGANIZATION_TYPE)

        viewModel = ViewModelProvider(this)[SelectParkingLotViewModel::class.java]
        adapter = TenantOptionAdapter(::onOptionSelected)
        binding.recyclerCategories.layoutManager = LinearLayoutManager(this)
        binding.recyclerCategories.itemAnimator = null
        binding.recyclerCategories.adapter = adapter
        binding.btnBack.setOnClickListener { navigateBack() }
        binding.btnRetry.setOnClickListener { loadCurrentStage(manualRefresh = true) }

        viewModel.loading.observe(this) { loading ->
            if (loading) showSkeleton() else renderCurrentStage()
        }

        updateHeader()
        handledForegroundGeneration = AppForegroundTracker.currentGeneration()
        loadCurrentStage()
    }

    override fun onResume() {
        super.onResume()
        val generation = AppForegroundTracker.currentGeneration()
        if (generation == handledForegroundGeneration) return
        handledForegroundGeneration = generation
        loadCurrentStage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_STAGE, stage.name)
        outState.putString(STATE_ORGANIZATION_ID, organizationId)
        outState.putString(STATE_ORGANIZATION_NAME, organizationName)
        outState.putString(STATE_ORGANIZATION_TYPE, organizationType)
        super.onSaveInstanceState(outState)
    }

    private fun loadCurrentStage(manualRefresh: Boolean = false) {
        when (stage) {
            Stage.ORGANIZATION -> viewModel.loadOrganizations(manualRefresh)
            Stage.LOCATION -> {
                val id = organizationId
                if (id.isNullOrBlank()) {
                    stage = Stage.ORGANIZATION
                    updateHeader()
                    viewModel.loadOrganizations(manualRefresh)
                } else {
                    viewModel.loadLocations(id, manualRefresh)
                }
            }
        }
    }

    private fun renderCurrentStage() {
        val options = when (stage) {
            Stage.ORGANIZATION -> viewModel.organizations.value.orEmpty().map { organization ->
                TenantOption(
                    id = organization.id,
                    title = organization.name.ifBlank { "Organization" },
                    subtitle = organization.type?.replace('_', ' ')?.lowercase()
                        ?.replaceFirstChar { it.titlecase() }
                        ?: "Parking organization",
                )
            }
            Stage.LOCATION -> viewModel.locations.value.orEmpty().map { location ->
                TenantOption(
                    id = location.id,
                    title = location.name.ifBlank { "Parking location" },
                    subtitle = location.address?.takeIf(String::isNotBlank)
                        ?: organizationName.orEmpty(),
                )
            }
        }

        hideSkeleton()
        if (options.isEmpty()) {
            binding.cardList.visibility = View.GONE
            binding.recyclerCategories.visibility = View.GONE
            binding.tvEmptyMessage.text = viewModel.loadError.value
                ?: if (stage == Stage.ORGANIZATION) {
                    "No organizations are available yet."
                } else {
                    "No parking locations are available yet."
                }
            binding.emptyState.visibility = View.VISIBLE
            SkeletonShimmer.revealView(binding.emptyState)
        } else {
            binding.emptyState.visibility = View.GONE
            binding.cardList.visibility = View.VISIBLE
            binding.recyclerCategories.visibility = View.VISIBLE
            adapter.submitList(options) { SkeletonShimmer.revealStagger(binding.recyclerCategories) }
        }
    }

    private fun onOptionSelected(option: TenantOption) {
        when (stage) {
            Stage.ORGANIZATION -> {
                val selected = viewModel.organizations.value.orEmpty().firstOrNull { it.id == option.id }
                    ?: return
                organizationId = selected.id
                organizationName = selected.name
                organizationType = selected.type
                stage = Stage.LOCATION
                adapter.submitList(emptyList())
                updateHeader()
                viewModel.loadLocations(selected.id)
            }
            Stage.LOCATION -> {
                val selected = viewModel.locations.value.orEmpty().firstOrNull { it.id == option.id }
                    ?: return
                val orgId = organizationId ?: return
                lotLauncher.launch(
                    SelectParkingLotActivity.intentForTenant(
                        context = this,
                        mode = mode,
                        organizationId = orgId,
                        organizationName = organizationName.orEmpty(),
                        organizationType = organizationType,
                        locationId = selected.id,
                        locationName = selected.name,
                        allowBack = true,
                        forwardExtras = forwardExtras,
                    )
                )
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
        }
    }

    private fun updateHeader() {
        binding.btnBack.visibility = if (stage == Stage.LOCATION || mode == MODE_CHANGE) View.VISIBLE else View.GONE
        if (stage == Stage.ORGANIZATION) {
            binding.tvTitle.text = if (mode == MODE_CHANGE) "Change parking organization" else "Choose your organization"
            binding.tvSubtitle.text = "Select the organization that manages your parking."
        } else {
            binding.tvTitle.text = organizationName?.takeIf(String::isNotBlank) ?: "Choose a location"
            binding.tvSubtitle.text = "Select a location to see only its parking lots."
        }
    }

    private fun navigateBack() {
        if (stage == Stage.LOCATION) {
            stage = Stage.ORGANIZATION
            organizationId = null
            organizationName = null
            organizationType = null
            updateHeader()
            renderCurrentStage()
        } else {
            finish()
        }
    }

    private fun showSkeleton() {
        binding.emptyState.visibility = View.GONE
        binding.recyclerCategories.visibility = View.GONE
        if (binding.skeletonContainer.childCount == 0) LotSkeleton.populate(binding.skeletonContainer, 3)
        binding.cardList.visibility = View.VISIBLE
        binding.skeletonContainer.visibility = View.VISIBLE
        if (skeletonBreath == null) skeletonBreath = SkeletonShimmer.start(binding.skeletonContainer)
    }

    private fun hideSkeleton() {
        skeletonBreath?.cancel()
        skeletonBreath = null
        binding.skeletonContainer.visibility = View.GONE
    }

    override fun onDestroy() {
        skeletonBreath?.cancel()
        super.onDestroy()
    }
}
