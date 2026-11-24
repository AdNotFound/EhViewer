package com.hippo.ehviewer.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

class ReaderSidebarScrollIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val normalBarWidth = 3f * density
    private val draggingBarWidth = 5f * density
    private val minimumThumbHeight = 24f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal)).let {
            val color = it.getColor(0, 0)
            it.recycle()
            color
        }
        alpha = 128
    }

    private var recyclerView: RecyclerView? = null
    private var isDragging = false
    private var downY = 0f
    private var isDragPending = false

    var onDragStateChanged: ((isDragging: Boolean) -> Unit)? = null

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            postInvalidateOnAnimation()
        }
    }
    private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }

    init {
        isClickable = true
        isFocusable = false
    }

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

        val thumbHeight = (height.toFloat() * extent / range).coerceIn(
            minimumThumbHeight.coerceAtMost(height.toFloat()),
            height.toFloat(),
        )
        val offset = view.computeVerticalScrollOffset().coerceIn(0, range - extent)
        val top = (height - thumbHeight) * offset / (range - extent)

        paint.alpha = if (isDragging) 255 else 128
        val currentBarWidth = if (isDragging) draggingBarWidth else normalBarWidth
        val left = (width - currentBarWidth) / 2f
        val right = left + currentBarWidth
        val radius = currentBarWidth / 2f

        canvas.drawRoundRect(left, top, right, top + thumbHeight, radius, radius, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val view = recyclerView ?: return super.onTouchEvent(event)
        val adapter = view.adapter ?: return super.onTouchEvent(event)
        val itemCount = adapter.itemCount
        if (itemCount <= 0 || height <= 0) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                val range = view.computeVerticalScrollRange()
                val extent = view.computeVerticalScrollExtent()
                if (extent <= 0 || range <= extent) return false

                val thumbHeight = (height.toFloat() * extent / range).coerceIn(
                    minimumThumbHeight.coerceAtMost(height.toFloat()),
                    height.toFloat(),
                )
                val offset = view.computeVerticalScrollOffset().coerceIn(0, range - extent)
                val thumbTop = (height - thumbHeight) * offset / (range - extent)
                val thumbBottom = thumbTop + thumbHeight

                // Expanded vertical hit area around the thumb
                val hitPadding = 12f * density
                val isNearThumb = event.y in (thumbTop - hitPadding)..(thumbBottom + hitPadding)

                isDragPending = true
                if (isNearThumb) {
                    startDrag()
                    scrollToPositionAt(event.y, thumbHeight)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val range = view.computeVerticalScrollRange()
                val extent = view.computeVerticalScrollExtent()
                if (extent <= 0 || range <= extent) return false
                val thumbHeight = (height.toFloat() * extent / range).coerceIn(
                    minimumThumbHeight.coerceAtMost(height.toFloat()),
                    height.toFloat(),
                )

                if (isDragPending && !isDragging) {
                    if (abs(event.y - downY) > touchSlop) {
                        startDrag()
                    }
                }

                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    scrollToPositionAt(event.y, thumbHeight)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragPending && !isDragging) {
                    // Tap on track to jump directly
                    val range = view.computeVerticalScrollRange()
                    val extent = view.computeVerticalScrollExtent()
                    if (extent > 0 && range > extent) {
                        val thumbHeight = (height.toFloat() * extent / range).coerceIn(
                            minimumThumbHeight.coerceAtMost(height.toFloat()),
                            height.toFloat(),
                        )
                        scrollToPositionAt(event.y, thumbHeight)
                    }
                }
                stopDrag()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                stopDrag()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startDrag() {
        if (!isDragging) {
            isDragging = true
            isDragPending = false
            parent?.requestDisallowInterceptTouchEvent(true)
            onDragStateChanged?.invoke(true)
            invalidate()
        }
    }

    private fun stopDrag() {
        if (isDragging || isDragPending) {
            isDragging = false
            isDragPending = false
            parent?.requestDisallowInterceptTouchEvent(false)
            onDragStateChanged?.invoke(false)
            invalidate()
        }
    }

    private fun scrollToPositionAt(touchY: Float, thumbHeight: Float) {
        val view = recyclerView ?: return
        val adapter = view.adapter ?: return
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return

        val scrollableHeight = height.toFloat() - thumbHeight
        if (scrollableHeight <= 0f) return

        val clampedTop = (touchY - thumbHeight / 2f).coerceIn(0f, scrollableHeight)
        val ratio = clampedTop / scrollableHeight

        val targetPosition = when {
            ratio <= 0.02f -> 0
            ratio >= 0.98f -> itemCount - 1
            else -> (ratio * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        }

        val lm = view.layoutManager
        if (lm is LinearLayoutManager) {
            lm.scrollToPositionWithOffset(targetPosition, 0)
        } else {
            view.scrollToPosition(targetPosition)
        }
        postInvalidateOnAnimation()
    }
}
