package com.gridee.parking.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseTabFragment<T : ViewBinding> : Fragment() {

    private var _binding: T? = null
    protected val binding get() = _binding ?: throw IllegalStateException("Fragment binding is not available yet")
    protected val bindingOrNull: T? get() = _binding

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): T
    abstract fun setupUI()
    abstract fun getScrollableView(): View?

    protected fun hasViewBinding(): Boolean = _binding != null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Called when user double-taps the current tab to scroll to top
     */
    open fun scrollToTop() {
        getScrollableView()?.let { scrollView ->
            when (scrollView) {
                is androidx.core.widget.NestedScrollView -> {
                    scrollView.smoothScrollTo(0, 0)
                }
                is android.widget.ScrollView -> {
                    scrollView.smoothScrollTo(0, 0)
                }
                is androidx.recyclerview.widget.RecyclerView -> {
                    scrollView.smoothScrollToPosition(0)
                }
            }
        }
    }

    protected fun showToast(message: String) {
        val parentView = activity?.findViewById<android.view.ViewGroup>(com.gridee.parking.R.id.fragment_container)
            ?: activity?.findViewById<android.view.ViewGroup>(android.R.id.content)
            
        if (parentView != null) {
            com.gridee.parking.utils.NotificationHelper.showInfoNoIcon(
                parent = parentView,
                title = "Notification",
                message = message,
                duration = 3000L
            )
        } else {
            context?.let {
                android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
