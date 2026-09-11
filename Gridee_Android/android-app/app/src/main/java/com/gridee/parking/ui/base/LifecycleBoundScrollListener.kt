package com.gridee.parking.ui.base

import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.annotation.MainThread
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView

/**
 * One replaceable scroll subscription, owned by a screen's *view* lifecycle.
 *
 * ViewTreeObserver is shared by the attached window, including hidden tabs. Only a resumed,
 * attached view may drive its host's chrome. Suspending the listener also avoids retaining a
 * detached view through the window observer. This stays additive to any listener owned by
 * the screen itself (for example Profile's frosted toolbar).
 */
@MainThread
internal class LifecycleBoundScrollListener {
    private var subscription: Subscription? = null

    fun bind(
        view: View,
        owner: LifecycleOwner,
        onScroll: (Int) -> Unit,
        onActivated: (Int) -> Unit = onScroll,
    ) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED ||
            (view !is NestedScrollView && view !is ScrollView && view !is RecyclerView)
        ) {
            clear()
            return
        }

        val existing = subscription
        if (existing?.matches(view, owner.lifecycle) == true) {
            // Reconcile a restored scroll offset without registering another callback.
            existing.synchronize()
            return
        }

        clear()
        val next = Subscription(view, owner.lifecycle, onScroll, onActivated)
        subscription = next
        next.start()
    }

    fun clear() {
        subscription?.dispose()
        subscription = null
    }

    private inner class Subscription(
        private var view: View?,
        private var lifecycle: Lifecycle?,
        private var onScroll: ((Int) -> Unit)?,
        private var onActivated: ((Int) -> Unit)?,
    ) : DefaultLifecycleObserver, View.OnAttachStateChangeListener {
        private var registeredObserver: ViewTreeObserver? = null
        private var lastScrollY = 0
        private var disposed = false
        private val listener = ViewTreeObserver.OnScrollChangedListener {
            val target = view
            if (!disposed && registeredObserver != null && target != null &&
                target.isAttachedToWindow && lifecycle?.currentState == Lifecycle.State.RESUMED
            ) {
                val y = readScrollY(target)
                // Another view in the same window may have scrolled. Do not repeat chrome work.
                if (y != lastScrollY) {
                    lastScrollY = y
                    onScroll?.invoke(y)
                }
            }
        }

        fun matches(target: View, targetLifecycle: Lifecycle): Boolean =
            !disposed && view === target && lifecycle === targetLifecycle

        fun start() {
            view?.addOnAttachStateChangeListener(this)
            // addObserver synchronously catches up to RESUMED when binding an existing tab.
            lifecycle?.addObserver(this)
        }

        override fun onResume(owner: LifecycleOwner) = synchronize()

        override fun onPause(owner: LifecycleOwner) = stopObserving()

        override fun onDestroy(owner: LifecycleOwner) = dispose()

        override fun onViewAttachedToWindow(v: View) = synchronize()

        override fun onViewDetachedFromWindow(v: View) = stopObserving()

        fun synchronize() {
            val target = view ?: return
            if (disposed || !target.isAttachedToWindow ||
                lifecycle?.currentState != Lifecycle.State.RESUMED
            ) return

            val observer = target.viewTreeObserver
            if (registeredObserver !== observer || !observer.isAlive) {
                stopObserving()
                if (!observer.isAlive) return
                registeredObserver = observer
                observer.addOnScrollChangedListener(listener)
            }
            val y = readScrollY(target)
            lastScrollY = y
            onActivated?.invoke(y)
        }

        private fun stopObserving() {
            val previous = registeredObserver
            registeredObserver = null
            lastScrollY = 0
            if (previous?.isAlive == true) previous.removeOnScrollChangedListener(listener)
            // Android can merge a floating observer into the window observer on attachment.
            // Remove from the current observer too if the original observer has been replaced.
            val current = view?.viewTreeObserver
            if (previous != null && current !== previous && current?.isAlive == true) {
                current.removeOnScrollChangedListener(listener)
            }
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            stopObserving()
            view?.removeOnAttachStateChangeListener(this)
            lifecycle?.removeObserver(this)
            view = null
            lifecycle = null
            onScroll = null
            onActivated = null
            if (subscription === this) subscription = null
        }
    }

    private fun readScrollY(view: View): Int = when (view) {
        is RecyclerView -> view.computeVerticalScrollOffset()
        else -> view.scrollY
    }
}
