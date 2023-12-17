package org.fossify.calendar.fragments

import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.databinding.FragmentYearBinding
import org.fossify.calendar.databinding.SmallMonthViewHolderBinding
import org.fossify.calendar.databinding.TopNavigationBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getProperDayIndexInWeek
import org.fossify.calendar.extensions.getViewBitmap
import org.fossify.calendar.extensions.printBitmap
import org.fossify.calendar.helpers.YEAR_LABEL
import org.fossify.calendar.helpers.YearlyCalendarImpl
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.calendar.interfaces.YearlyCalendar
import org.fossify.calendar.models.DayYearly
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.joda.time.DateTime

class YearFragment : Fragment(), YearlyCalendar {
    private var mYear = 0
    private var mFirstDayOfWeek = 0
    private var isPrintVersion = false
    private var lastHash = 0
    private var mCalendar: YearlyCalendarImpl? = null

    var listener: NavigationListener? = null

    private lateinit var binding: FragmentYearBinding
    private lateinit var topNavigationBinding: TopNavigationBinding
    private lateinit var monthHolders: List<SmallMonthViewHolderBinding>

    private val monthResIds = arrayOf(
        org.fossify.commons.R.string.january,
        org.fossify.commons.R.string.february,
        org.fossify.commons.R.string.march,
        org.fossify.commons.R.string.april,
        org.fossify.commons.R.string.may,
        org.fossify.commons.R.string.june,
        org.fossify.commons.R.string.july,
        org.fossify.commons.R.string.august,
        org.fossify.commons.R.string.september,
        org.fossify.commons.R.string.october,
        org.fossify.commons.R.string.november,
        org.fossify.commons.R.string.december
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentYearBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        monthHolders = arrayListOf(
            binding.month1Holder, binding.month2Holder, binding.month3Holder, binding.month4Holder, binding.month5Holder, binding.month6Holder,
            binding.month7Holder, binding.month8Holder, binding.month9Holder, binding.month10Holder, binding.month11Holder, binding.month12Holder
        ).apply {
            forEachIndexed { index, it ->
                it.monthLabel.text = getString(monthResIds[index])
            }
        }

        mYear = requireArguments().getInt(YEAR_LABEL)
        requireContext().updateTextColors(binding.calendarWrapper)
        setupMonths()
        setupButtons()

        mCalendar = YearlyCalendarImpl(this, requireContext(), mYear)
        return binding.root
    }

    override fun onPause() {
        super.onPause()
        mFirstDayOfWeek = requireContext().config.firstDayOfWeek
    }

    override fun onResume() {
        super.onResume()
        val firstDayOfWeek = requireContext().config.firstDayOfWeek
        if (firstDayOfWeek != mFirstDayOfWeek) {
            mFirstDayOfWeek = firstDayOfWeek
            setupMonths()
        }
        updateCalendar()
    }

    fun updateCalendar() {
        mCalendar?.getEvents(mYear)
    }

    private fun setupMonths() {
        val dateTime = DateTime().withYear(mYear).withHourOfDay(12)
        monthHolders.forEachIndexed { index, monthHolder ->
            val monthOfYear = index + 1
            val monthView = monthHolder.smallMonthView
            val curTextColor = when {
                isPrintVersion -> resources.getColor(org.fossify.commons.R.color.theme_light_text_color)
                else -> requireContext().getProperTextColor()
            }

            monthHolder.monthLabel.setTextColor(curTextColor)
            val firstDayOfMonth = dateTime.withMonthOfYear(monthOfYear).withDayOfMonth(1)
            monthView.firstDay = requireContext().getProperDayIndexInWeek(firstDayOfMonth)
            val numberOfDays = dateTime.withMonthOfYear(monthOfYear).dayOfMonth().maximumValue
            monthView.setDays(numberOfDays)
            monthView.setOnClickListener {
                (activity as MainActivity).openMonthFromYearly(DateTime().withDate(mYear, monthOfYear, 1))
            }
        }

        if (!isPrintVersion) {
            val now = DateTime()
            markCurrentMonth(now)
        }
    }

    private fun setupButtons() {
        val textColor = requireContext().getProperTextColor()
        topNavigationBinding.topLeftArrow.apply {
            applyColorFilter(textColor)
            background = null
            setOnClickListener {
                listener?.goLeft()
            }

            val pointerLeft = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_left_vector)
            pointerLeft?.isAutoMirrored = true
            setImageDrawable(pointerLeft)
        }

        topNavigationBinding.topRightArrow.apply {
            applyColorFilter(textColor)
            background = null
            setOnClickListener {
                listener?.goRight()
            }

            val pointerRight = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_right_vector)
            pointerRight?.isAutoMirrored = true
            setImageDrawable(pointerRight)
        }

        topNavigationBinding.topValue.apply {
            setTextColor(requireContext().getProperTextColor())
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }
    }

    private fun markCurrentMonth(now: DateTime) {
        if (now.year == mYear) {
            val monthOfYear = now.monthOfYear
            val monthHolder = monthHolders[monthOfYear - 1]
            monthHolder.monthLabel.setTextColor(requireContext().getProperPrimaryColor())
            monthHolder.smallMonthView.todaysId = now.dayOfMonth
        }
    }

    override fun updateYearlyCalendar(events: SparseArray<ArrayList<DayYearly>>, hashCode: Int) {
        if (!isAdded) {
            return
        }

        if (hashCode == lastHash) {
            return
        }

        lastHash = hashCode
        monthHolders.forEachIndexed { index, monthHolder ->
            val monthView = monthHolder.smallMonthView
            val monthOfYear = index + 1
            monthView.setEvents(events.get(monthOfYear))
        }

        topNavigationBinding.topValue.post {
            topNavigationBinding.topValue.text = mYear.toString()
        }
    }

    fun printCurrentView() {
        isPrintVersion = true
        setupMonths()
        toggleSmallMonthPrintModes()

        requireContext().printBitmap(binding.calendarWrapper.getViewBitmap())

        isPrintVersion = false
        setupMonths()
        toggleSmallMonthPrintModes()
    }

    private fun toggleSmallMonthPrintModes() {
        monthHolders.forEach {
            it.smallMonthView.togglePrintMode()
        }
    }
}
