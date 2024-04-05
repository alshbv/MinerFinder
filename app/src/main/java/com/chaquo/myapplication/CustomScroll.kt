package com.chaquo.myapplication

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

class CustomNSV : NestedScrollView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Always intercept the touch events to prioritize NestedScrollView scrolling
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return true
    }
}
