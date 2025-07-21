package org.fossify.calendar.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.isWeekendIndex
import org.fossify.calendar.models.DayYearly
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.MEDIUM_ALPHA

// used for displaying months at Yearly view
class SmallMonthView(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {
    private var paint: Paint
    private var todayCirclePaint: Paint
    private var dayWidth = 0f
    private var textColor = 0
    private var weekendsTextColor = 0
    private var days = 31
    private var isLandscape = false
    private var highlightWeekends = false
    private var isPrintVersion = false
    private var mEvents: ArrayList<DayYearly>? = null

    var firstDay = 0
    var todaysId = 0

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    fun setDays(days: Int) {
        this.days = days
        invalidate()
    }

    fun setEvents(events: ArrayList<DayYearly>?) {
        mEvents = events
        post { invalidate() }
    }

    init {
        val attributes = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SmallMonthView,
            0, 0
        )

        try {
            days = attributes.getInt(R.styleable.SmallMonthView_days, 31)
        } finally {
            attributes.recycle()
        }

        val baseColor = context.getProperTextColor()
        textColor = baseColor.adjustAlpha(MEDIUM_ALPHA)
        weekendsTextColor = context.config.highlightWeekendsColor.adjustAlpha(MEDIUM_ALPHA)
        highlightWeekends = context.config.highlightWeekends

        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = resources.getDimensionPixelSize(R.dimen.year_view_day_text_size).toFloat()
            textAlign = Paint.Align.CENTER
        }

        todayCirclePaint = Paint(paint)
        todayCirclePaint.color = context.getProperPrimaryColor().adjustAlpha(MEDIUM_ALPHA)
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dayWidth == 0f) {
            dayWidth = if (isLandscape) {
                width / 9f
            } else {
                width / 7f
            }
        }

        val fm = paint.fontMetrics
        val radius = dayWidth * 0.41f

        var curId = 1 - firstDay
        for (y in 1..6) {
            for (x in 1..7) {
                if (curId in 1..days) {
                    val textPaint = getPaint(curId, x, highlightWeekends)
                    val centerX = x * dayWidth - dayWidth / 2
                    val centerY = y * dayWidth - dayWidth / 2
                    val baselineY = centerY - (fm.ascent + fm.descent) / 2

                    canvas.drawText(curId.toString(), centerX, baselineY, textPaint)
                    if (curId == todaysId && !isPrintVersion) {
                        canvas.drawCircle(centerX, centerY, radius, todayCirclePaint)
                    }
                }
                curId++
            }
        }
    }

    private fun getPaint(curId: Int, weekDay: Int, highlightWeekends: Boolean): Paint {
        val colors = mEvents?.get(curId)?.eventColors ?: HashSet()
        if (colors.isNotEmpty()) {
            val curPaint = Paint(paint)
            curPaint.color = colors.first()
            return curPaint
        } else if (highlightWeekends && context.isWeekendIndex(weekDay - 1)) {
            val curPaint = Paint(paint)
            curPaint.color = weekendsTextColor
            return curPaint
        }

        return paint
    }

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        textColor = if (isPrintVersion) {
            resources.getColor(org.fossify.commons.R.color.theme_light_text_color, null)
        } else {
            context.getProperTextColor().adjustAlpha(MEDIUM_ALPHA)
        }

        paint.color = textColor
        invalidate()
    }
}
