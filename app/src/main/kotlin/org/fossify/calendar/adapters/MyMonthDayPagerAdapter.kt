package org.fossify.calendar.adapters

import android.os.Bundle
import android.util.SparseArray
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.fossify.calendar.fragments.MonthDayFragment
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.interfaces.NavigationListener

class MyMonthDayPagerAdapter(fm: FragmentManager, private val mCodes: List<String>, private val mListener: NavigationListener) : FragmentStatePagerAdapter(fm) {
    private val mFragments = SparseArray<MonthDayFragment>()

    override fun getCount() = mCodes.size

    override fun getItem(position: Int): Fragment {
        val bundle = Bundle()
        val code = mCodes[position]
        bundle.putString(DAY_CODE, code)

        val fragment = MonthDayFragment()
        fragment.arguments = bundle
        fragment.listener = mListener

        return fragment
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = super.instantiateItem(container, position)
        if (item is MonthDayFragment) {
            mFragments.put(position, item)
        }
        return item
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        mFragments.remove(position)
        super.destroyItem(container, position, `object`)
    }

    fun updateCalendars(pos: Int) {
        for (i in -1..1) {
            mFragments[pos + i]?.updateCalendar()
        }
    }

    fun printCurrentView(pos: Int) {
        mFragments[pos].printCurrentView()
    }

    fun getNewEventDayCode(pos: Int): String? = mFragments[pos].getNewEventDayCode()
}
