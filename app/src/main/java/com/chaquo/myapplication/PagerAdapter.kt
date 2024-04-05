package com.chaquo.myapplication

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class ViewPagerAdapter123(supportFragmentManager: FragmentManager) :
    FragmentStatePagerAdapter(supportFragmentManager) {

    // declare arrayList to contain fragments and its title
    private val mFragmentList = ArrayList<Fragment>()
    private val mFragmentTitleList = ArrayList<String>()

    // returns a specific fragment
    override fun getItem(position: Int): Fragment {
        return mFragmentList[position]
    }

    // the total number of tabs
    override fun getCount(): Int {
        return mFragmentList.size
    }

    // return title of the tab
    override fun getPageTitle(position: Int): CharSequence{
        return mFragmentTitleList[position]
    }

    // add each fragment and its title to the arrays that keep track of 'em
    fun addFragment(fragment: Fragment, title: String) {
        Log.d("ViewPagerAdapter", "Adding fragment: $title")
        mFragmentList.add(fragment)
        mFragmentTitleList.add(title)
    }
}
