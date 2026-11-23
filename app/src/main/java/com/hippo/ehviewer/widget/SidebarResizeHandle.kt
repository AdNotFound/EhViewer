package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R
import com.hippo.ehviewer.util.SidebarWidthUtils
import kotlin.math.abs
import kotlin.math.roundToInt

class SidebarResizeHandle(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleColor = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal)).let {
        val color = it.getColor(0, 0)
        it.recycle()
        color
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var startWidth = 0
    private var originalPercent = 0
    private var previewPercent = 0
    private var dragging = false
    private var tracking = false
    private var dialog: AlertDialog? = null
    private var getPercent: () -> Int = { 0 }
    private var getPaneWidth: () -> Int = { 0 }
    private var getAvailableWidth: () -> Int = { 0 }
    private var getDirection: () -> Int = { 1 }
    private var preview: (Int) -> Unit = {}
    private var commit: (Int) -> Unit = {}
    private var percentRange = 0..0
    private var titleRes = 0

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.sidebar_resize_handle)
    }

    fun bind(
        @StringRes title: Int,
        range: IntRange,
        percent: () -> Int,
        paneWidth: () -> Int,
        availableWidth: () -> Int,
        direction: () -> Int,
        onPreview: (Int) -> Unit,
        onCommit: (Int) -> Unit,
    ) {
        titleRes = title
        percentRange = range
        getPercent = percent
        getPaneWidth = paneWidth
        getAvailableWidth = availableWidth
        getDirection = direction
        preview = onPreview
        commit = onCommit
        setOnClickListener { showWidthDialog() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = handleColor
        paint.alpha = 50
        val centerX = width / 2f
        canvas.drawRect(centerX - density / 2, 0f, centerX + density / 2, height.toFloat(), paint)
        paint.alpha = if (isPressed || isFocused) 255 else 160
        val centerY = height / 2f
        canvas.drawRoundRect(centerX - 2 * density, centerY - 24 * density, centerX + 2 * density, centerY + 24 * density, 2 * density, 2 * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                startWidth = getPaneWidth()
                originalPercent = getPercent()
                previewPercent = originalPercent
                dragging = false
                tracking = true
                isPressed = true
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    val deltaX = event.rawX - downX
                    if (abs(deltaX) > touchSlop) dragging = true
                    if (dragging) updatePreview(deltaX)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (tracking) {
                    if (dragging) {
                        updatePreview(event.rawX - downX)
                        commit(previewPercent)
                    } else {
                        performClick()
                    }
                }
                finishTracking()
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                if (tracking && dragging) preview(originalPercent)
                finishTracking()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    private fun updatePreview(deltaX: Float) {
        val percent = SidebarWidthUtils.draggedPercent(startWidth, deltaX, getAvailableWidth(), getDirection(), percentRange)
        if (percent != previewPercent) {
            previewPercent = percent
            preview(percent)
        }
    }

    private fun finishTracking() {
        tracking = false
        dragging = false
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun showWidthDialog() {
        if (dialog != null) return
        val original = getPercent()
        var selected = original.coerceIn(percentRange)
        var saved = false
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).roundToInt(), (12 * density).roundToInt(), (24 * density).roundToInt(), 0)
        }
        val valueText = TextView(context)
        fun updateLabel() {
            val percent = (getPaneWidth() * 100f / getAvailableWidth().coerceAtLeast(1)).roundToInt()
            valueText.text = context.getString(R.string.sidebar_width_percent, percent)
        }
        val seekBar = SeekBar(context).apply {
            max = percentRange.last - percentRange.first
            progress = selected - percentRange.first
            contentDescription = context.getString(titleRes)
        }
        content.addView(valueText)
        content.addView(seekBar)
        updateLabel()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                selected = percentRange.first + progress
                preview(selected)
                updateLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        dialog = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                saved = true
                commit(selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create().also { widthDialog ->
                widthDialog.setOnDismissListener {
                    if (!saved) preview(original)
                    dialog = null
                }
                widthDialog.show()
            }
    }

    override fun onDetachedFromWindow() {
        if (tracking && dragging) preview(originalPercent)
        finishTracking()
        dialog?.dismiss()
        super.onDetachedFromWindow()
    }
}
