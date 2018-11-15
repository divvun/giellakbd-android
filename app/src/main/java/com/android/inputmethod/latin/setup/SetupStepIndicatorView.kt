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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View

import com.android.inputmethod.latin.R

class SetupStepIndicatorView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val mIndicatorPath = Path()
    private val mIndicatorPaint = Paint()
    private var mXRatio: Float = 0.toFloat()

    init {
        mIndicatorPaint.color = resources.getColor(R.color.setup_step_background)
        mIndicatorPaint.style = Paint.Style.FILL
    }

    fun setIndicatorPosition(stepPos: Int, totalStepNum: Int) {
        val layoutDirection = ViewCompat.getLayoutDirection(this)
        // The indicator position is the center of the partition that is equally divided into
        // the total step number.
        val partionWidth = 1.0f / totalStepNum
        val pos = stepPos * partionWidth + partionWidth / 2.0f
        mXRatio = if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) 1.0f - pos else pos
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val xPos = (width * mXRatio).toInt()
        val height = height
        mIndicatorPath.rewind()
        mIndicatorPath.moveTo(xPos.toFloat(), 0f)
        mIndicatorPath.lineTo((xPos + height).toFloat(), height.toFloat())
        mIndicatorPath.lineTo((xPos - height).toFloat(), height.toFloat())
        mIndicatorPath.close()
        canvas.drawPath(mIndicatorPath, mIndicatorPaint)
    }
}
