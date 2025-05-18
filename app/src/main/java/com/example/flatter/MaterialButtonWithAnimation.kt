
package com.example.flatter

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import com.example.flatter.R
import com.google.android.material.button.MaterialButton

class MaterialButtonWithAnimation : MaterialButton {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val anim = AnimationUtils.loadAnimation(context, R.anim.button_press_elevation)
                startAnimation(anim)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val anim = AnimationUtils.loadAnimation(context, R.anim.button_release_elevation)
                startAnimation(anim)
            }
        }
        return super.onTouchEvent(event)
    }
}