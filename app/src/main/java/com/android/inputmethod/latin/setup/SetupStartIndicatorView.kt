/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.setup

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

import com.android.inputmethod.latin.R

class SetupStartIndicatorView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    init {
        orientation = LinearLayout.HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.setup_start_indicator_label, this)

        val labelView = findViewById<View>(R.id.setup_start_label) as LabelView
        labelView.setIndicatorView(findViewById(R.id.setup_start_indicator))
    }

    class LabelView(context: Context, attrs: AttributeSet) : TextView(context, attrs) {
        private var mIndicatorView: View? = null

        fun setIndicatorView(indicatorView: View) {
            mIndicatorView = indicatorView
        }

        // TODO: Once we stop supporting ICS, uncomment {@link #setPressed(boolean)} method and
        // remove this method.
        override fun drawableStateChanged() {
            super.drawableStateChanged()
            for (state in drawableState) {
                if (state == android.R.attr.state_pressed) {
                    updateIndicatorView(true /* pressed */)
                    return
                }
            }
            updateIndicatorView(false /* pressed */)
        }

        // TODO: Once we stop supporting ICS, uncomment this method and remove
        // {@link #drawableStateChanged()} method.
        //        @Override
        //        public void setPressed(final boolean pressed) {
        //            super.setPressed(pressed);
        //            updateIndicatorView(pressed);
        //        }

        private fun updateIndicatorView(pressed: Boolean) {
            if (mIndicatorView != null) {
                mIndicatorView!!.isPressed = pressed
                mIndicatorView!!.invalidate()
            }
        }
    }

    class IndicatorView(context: Context, attrs: AttributeSet) : View(context, attrs) {
        private val mIndicatorPath = Path()
        private val mIndicatorPaint = Paint()
        private val mIndicatorColor: ColorStateList

        init {
            mIndicatorColor = resources.getColorStateList(
                    R.color.setup_step_action_background)
            mIndicatorPaint.style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val layoutDirection = ViewCompat.getLayoutDirection(this)
            val width = width
            val height = height
            val halfHeight = height / 2.0f
            val path = mIndicatorPath
            path.rewind()
            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
                // Left arrow
                path.moveTo(width.toFloat(), 0.0f)
                path.lineTo(0.0f, halfHeight)
                path.lineTo(width.toFloat(), height.toFloat())
            } else { // LAYOUT_DIRECTION_LTR
                // Right arrow
                path.moveTo(0.0f, 0.0f)
                path.lineTo(width.toFloat(), halfHeight)
                path.lineTo(0.0f, height.toFloat())
            }
            path.close()
            val stateSet = drawableState
            val color = mIndicatorColor.getColorForState(stateSet, 0)
            mIndicatorPaint.color = color
            canvas.drawPath(path, mIndicatorPaint)
        }
    }
}
