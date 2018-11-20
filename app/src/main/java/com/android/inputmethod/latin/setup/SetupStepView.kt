package com.android.inputmethod.latin.setup

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.inputmethod.latin.R
import kotlinx.android.synthetic.main.view_setup_step.view.*

class SetupStepView(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): ConstraintLayout(context, attrs, defStyleAttr){
    constructor(context: Context, attrs: AttributeSet? = null): this(context, attrs, 0)
    constructor(context: Context): this(context, null)
    init {
        View.inflate(context, R.layout.view_setup_step, this)

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.SetupStepView, 0, 0)
        a.getString(R.styleable.SetupStepView_header)?.let {
            title = it
        }

        a.getString(R.styleable.SetupStepView_description)?.let {
            description = it
        }

        a.getString(R.styleable.SetupStepView_buttonText)?.let {
            buttonText = it
        }

        a.getDrawable(R.styleable.SetupStepView_icon)?.let {
            icon = it
        }
    }

    var title: String
        get() = tv_step_title.text.toString()
        set(value) {
            tv_step_title.text = value
        }

    var description: String
        get() = tv_step_description.text.toString()
        set(value) {
            tv_step_description.text = value
        }

    var icon: Drawable
        get() = iv_step_image.drawable
        set(value) {
            iv_step_image.setImageDrawable(value)
        }

    var buttonText: String
        get() = b_step_trigger.text.toString()
        set(value) {
            b_step_trigger.text = value
        }

    var active: Boolean = true
        set(value) {
            field = value
            b_step_trigger.isEnabled = field
        }

    override fun setOnClickListener(clickListener: OnClickListener){
        b_step_trigger.setOnClickListener(clickListener)
    }
}