package org.fossify.calendar.fragments

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.databinding.FragmentMonthBinding
import org.fossify.calendar.databinding.TopNavigationBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getViewBitmap
import org.fossify.calendar.extensions.printBitmap
import org.fossify.calendar.helpers.Config
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.MonthlyCalendarImpl
import org.fossify.calendar.interfaces.MonthlyCalendar
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.calendar.models.DayMonthly
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperTextColor
import org.joda.time.DateTime

class MonthFragment : Fragment(), MonthlyCalendar {
    private var mTextColor = 0
    private var mShowWeekNumbers = false
    private var mDayCode = ""
    private var mPackageName = ""
    private var mLastHash = 0L
    private var mCalendar: MonthlyCalendarImpl? = null

    var listener: NavigationListener? = null

    private lateinit var mRes: Resources
    private lateinit var mConfig: Config
    private lateinit var binding: FragmentMonthBinding
    private lateinit var topNavigationBinding: TopNavigationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMonthBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        mRes = resources
        mPackageName = requireActivity().packageName
        mDayCode = requireArguments().getString(DAY_CODE)!!
        mConfig = requireContext().config
        storeStateVariables()

        setupButtons()
        mCalendar = MonthlyCalendarImpl(this, requireContext())

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        if (mConfig.showWeekNumbers != mShowWeekNumbers) {
            mLastHash = -1L
        }

        mCalendar!!.apply {
            mTargetDate = Formatter.getDateTimeFromCode(mDayCode)
            getDays(false)    // prefill the screen asap, even if without events
        }

        storeStateVariables()
        updateCalendar()
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mShowWeekNumbers = showWeekNumbers
        }
    }

    fun updateCalendar() {
        mCalendar?.updateMonthlyCalendar(Formatter.getDateTimeFromCode(mDayCode))
    }

    override fun updateMonthlyCalendar(context: Context, month: String, days: ArrayList<DayMonthly>, checkedEvents: Boolean, currTargetDate: DateTime) {
        val newHash = month.hashCode() + days.hashCode().toLong()
        if ((mLastHash != 0L && !checkedEvents) || mLastHash == newHash) {
            return
        }

        mLastHash = newHash

        activity?.runOnUiThread {
            topNavigationBinding.topValue.apply {
                text = month
                contentDescription = text

                if (activity != null) {
                    setTextColor(requireActivity().getProperTextColor())
                }
            }
            updateDays(days)
        }
    }

    private fun setupButtons() {
        mTextColor = requireContext().getProperTextColor()

        topNavigationBinding.topLeftArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                listener?.goLeft()
            }

            val pointerLeft = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_left_vector)
            pointerLeft?.isAutoMirrored = true
            setImageDrawable(pointerLeft)
            contentDescription = getString(R.string.accessibility_previous_month)
        }

        topNavigationBinding.topRightArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                listener?.goRight()
            }

            val pointerRight = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_right_vector)
            pointerRight?.isAutoMirrored = true
            setImageDrawable(pointerRight)
            contentDescription = getString(R.string.accessibility_next_month)
        }

        topNavigationBinding.topValue.apply {
            setTextColor(requireContext().getProperTextColor())
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }
    }

    private fun updateDays(days: ArrayList<DayMonthly>) {
        binding.monthViewWrapper.updateDays(days, true) {
            (activity as MainActivity).openDayFromMonthly(Formatter.getDateTimeFromCode(it.code))
        }
    }

    fun printCurrentView() {
        topNavigationBinding.apply {
            topLeftArrow.beGone()
            topRightArrow.beGone()
            topValue.setTextColor(resources.getColor(org.fossify.commons.R.color.theme_light_text_color))
            binding.monthViewWrapper.togglePrintMode()

            requireContext().printBitmap(binding.monthCalendarHolder.getViewBitmap())

            topLeftArrow.beVisible()
            topRightArrow.beVisible()
            topValue.setTextColor(requireContext().getProperTextColor())
            binding.monthViewWrapper.togglePrintMode()
        }
    }
}
