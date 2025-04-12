package org.fossify.calendar.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

class SmartSwipeRefreshLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var mInitialDownY = 0f
    private var mInitialDownX = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        // Fail fast if swipe refresh is disabled, child can scroll up, or refresh is in progress
        if (!isEnabled || canChildScrollUp() || isRefreshing) {
            return false
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mInitialDownY = ev.y
                mInitialDownX = ev.x
            }

            MotionEvent.ACTION_MOVE -> {
                val yDiff = abs(ev.y - mInitialDownY)
                val xDiff = abs(ev.x - mInitialDownX)
                // Only start dragging if the vertical difference is greater than the horizontal difference
                if (yDiff < touchSlop || yDiff < xDiff) {
                    return false
                }
            }
        }

        return super.onInterceptTouchEvent(ev)
    }
}
