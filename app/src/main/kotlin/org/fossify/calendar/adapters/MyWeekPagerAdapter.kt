package org.fossify.calendar.adapters

import android.os.Bundle
import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.fossify.calendar.fragments.WeekFragment
import org.fossify.calendar.helpers.WEEK_START_TIMESTAMP
import org.fossify.calendar.interfaces.WeekFragmentListener

class MyWeekPagerAdapter(fm: FragmentManager, private val mWeekTimestamps: List<Long>, private val mListener: WeekFragmentListener) :
    FragmentStatePagerAdapter(fm) {
    private val mFragments = SparseArray<WeekFragment>()

    override fun getCount() = mWeekTimestamps.size

    override fun getItem(position: Int): Fragment {
        val bundle = Bundle()
        val weekTimestamp = mWeekTimestamps[position]
        bundle.putLong(WEEK_START_TIMESTAMP, weekTimestamp)

        val fragment = WeekFragment()
        fragment.arguments = bundle
        fragment.listener = mListener

        mFragments.put(position, fragment)
        return fragment
    }

    fun updateScrollY(pos: Int, y: Int) {
        mFragments[pos - 1]?.updateScrollY(y)
        mFragments[pos + 1]?.updateScrollY(y)
    }

    fun updateCalendars(pos: Int) {
        for (i in -1..1) {
            mFragments[pos + i]?.updateCalendar()
        }
    }

    fun updateNotVisibleScaleLevel(pos: Int) {
        mFragments[pos - 1]?.updateNotVisibleViewScaleLevel()
        mFragments[pos + 1]?.updateNotVisibleViewScaleLevel()
    }

    fun togglePrintMode(pos: Int) {
        mFragments[pos].togglePrintMode()
    }
}
