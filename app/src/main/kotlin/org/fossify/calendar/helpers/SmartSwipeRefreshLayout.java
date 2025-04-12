package org.fossify.calendar.helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class SmartSwipeRefreshLayout extends SwipeRefreshLayout {

    private float mInitialDownY;
    private float mInitialDownX;
    private boolean mIsBeingDragged = false;

    private final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    public SmartSwipeRefreshLayout(Context context) {
        super(context);
    }

    public SmartSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        // Fail fast if swipe refresh is disabled, child can scroll up, or refresh is in progress
        if (!isEnabled() || canChildScrollUp() || isRefreshing()) {
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mIsBeingDragged = true;
                mInitialDownY = ev.getY();
                mInitialDownX = ev.getX();
                break;

            case MotionEvent.ACTION_MOVE:
                float yDiff = Math.abs(ev.getY() - mInitialDownY);
                float xDiff = Math.abs(ev.getX() - mInitialDownX);
                // Only start dragging if the vertical difference is greater than the horizontal difference
                mIsBeingDragged = yDiff > touchSlop && yDiff > xDiff;
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mIsBeingDragged = true;
                break;
        }

        return mIsBeingDragged && super.onInterceptTouchEvent(ev);
    }
}
