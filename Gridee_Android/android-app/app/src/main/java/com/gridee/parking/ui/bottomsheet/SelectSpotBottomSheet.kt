package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R

data class SpotFilterOption(
    val id: String?,
    val lotId: String?,
    val name: String,
    val isAll: Boolean = false
)

class SelectSpotBottomSheet(
    private val spots: List<SpotFilterOption>,
    private val selectedSpotName: String?,
    private val onSpotSelected: (SpotFilterOption) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog

            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                sheet.fitsSystemWindows = false
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                }

                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            bottomSheetDialog.behavior.state =
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false

                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50
                    window.attributes = window.attributes
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_spot_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            view.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }

        val btnClose = view.findViewById<ImageView>(R.id.btn_close)
        val rvSpots = view.findViewById<RecyclerView>(R.id.rv_spots)

        btnClose.setOnClickListener { dismiss() }

        val adapter = SpotSelectionAdapter(spots, selectedSpotName) { option ->
            onSpotSelected(option)
            dismiss()
        }

        rvSpots.layoutManager = LinearLayoutManager(requireContext())
        rvSpots.isNestedScrollingEnabled = true
        rvSpots.adapter = adapter
    }

    companion object {
        const val TAG = "SelectSpotBottomSheet"
    }
}

// ─── Adapter ───────────────────────────────────────────────

private class SpotSelectionAdapter(
    private val items: List<SpotFilterOption>,
    private val selectedName: String?,
    private val onItemSelected: (SpotFilterOption) -> Unit
) : RecyclerView.Adapter<SpotSelectionAdapter.ViewHolder>() {

    private var selectedPosition: Int = items.indexOfFirst { it.name == selectedName }.coerceAtLeast(0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spot_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_spot_name)
        private val rbSelect: RadioButton = itemView.findViewById(R.id.rb_select_spot)

        fun bind(option: SpotFilterOption, isSelected: Boolean) {
            tvName.text = option.name
            rbSelect.isChecked = isSelected

            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onItemSelected(option)
            }

            rbSelect.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onItemSelected(option)
            }
        }
    }
}
