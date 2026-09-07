package org.fossify.calendar.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.fossify.calendar.R
import org.fossify.commons.extensions.getProperTextColor

class CurrentTimeIndicatorView(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {
    companion object {
        private const val LINE_WIDTH_DP = 1.5f
        private const val DOT_OVERLAP_DP = 1f
    }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getProperTextColor()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = LINE_WIDTH_DP * density
    }
    private val dotRadius = resources.getDimension(R.dimen.weekly_view_now_dot_size) / 2f
    private val dotOverlap = DOT_OVERLAP_DP * density

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val dotCenterX = if (isRtl) width - dotRadius - dotOverlap else dotRadius + dotOverlap
        val lineEndX = if (isRtl) 0f else width.toFloat()

        canvas.drawLine(dotCenterX, centerY, lineEndX, centerY, paint)
        canvas.drawCircle(dotCenterX, centerY, dotRadius, paint)
    }
}
