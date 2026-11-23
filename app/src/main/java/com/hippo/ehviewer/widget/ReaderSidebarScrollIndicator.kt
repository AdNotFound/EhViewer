package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ReaderSidebarScrollIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal)).let {
            val color = it.getColor(0, 0)
            it.recycle()
            color
        }
        alpha = 128
    }
    private val minimumThumbHeight = 24f * resources.displayMetrics.density
    private var recyclerView: RecyclerView? = null
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            postInvalidateOnAnimation()
        }
    }
    private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }

    fun bind(view: RecyclerView) {
        removeListeners()
        recyclerView = view
        if (isAttachedToWindow) addListeners()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addListeners()
    }

    override fun onDetachedFromWindow() {
        removeListeners()
        super.onDetachedFromWindow()
    }

    private fun addListeners() {
        recyclerView?.addOnScrollListener(scrollListener)
        recyclerView?.addOnLayoutChangeListener(layoutListener)
    }

    private fun removeListeners() {
        recyclerView?.removeOnScrollListener(scrollListener)
        recyclerView?.removeOnLayoutChangeListener(layoutListener)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val view = recyclerView ?: return
        val range = view.computeVerticalScrollRange()
        val extent = view.computeVerticalScrollExtent()
        if (extent <= 0 || range <= extent || height <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).coerceIn(minimumThumbHeight.coerceAtMost(height.toFloat()), height.toFloat())
        val offset = view.computeVerticalScrollOffset().coerceIn(0, range - extent)
        val top = (height - thumbHeight) * offset / (range - extent)
        val radius = width / 2f
        canvas.drawRoundRect(0f, top, width.toFloat(), top + thumbHeight, radius, radius, paint)
    }
}
