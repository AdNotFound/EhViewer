package com.hippo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.hippo.yorozuya.LayoutUtils

class CheckableColorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var mColor: Int = 0
    private var mChecked: Boolean = false
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var size = if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            Math.min(widthSize, heightSize)
        } else if (widthMode == MeasureSpec.EXACTLY) {
            widthSize
        } else if (heightMode == MeasureSpec.EXACTLY) {
            heightSize
        } else {
            Math.min(widthSize, heightSize).coerceAtLeast(LayoutUtils.dp2pix(context, 32f))
        }

        setMeasuredDimension(size, size)
    }

    fun setColor(@ColorInt color: Int) {
        if (mColor != color) {
            mColor = color
            invalidate()
        }
    }

    fun setChecked(checked: Boolean) {
        if (mChecked != checked) {
            mChecked = checked
            invalidate()
        }
    }

    private fun isLightColor(@ColorInt color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance > 190.0 // 略高一点阈值，确保只在很浅的颜色上才用黑钩
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.coerceAtMost(height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        
        val margin = LayoutUtils.dp2pix(context, 1f).toFloat()
        val radius = size / 2f - margin

        // 绘制基础实心圆
        mPaint.isAntiAlias = true
        mPaint.style = Paint.Style.FILL
        mPaint.color = mColor
        mPaint.alpha = 255
        canvas.drawCircle(cx, cy, radius, mPaint)

        if (mChecked) {
            // 智能选择打钩颜色
            mPaint.color = if (isLightColor(mColor)) Color.BLACK else Color.WHITE
            mPaint.style = Paint.Style.STROKE
            mPaint.strokeWidth = LayoutUtils.dp2pix(context, 2.5f).toFloat()
            mPaint.strokeCap = Paint.Cap.ROUND
            mPaint.strokeJoin = Paint.Join.ROUND
            
            // 采用 MD 标准比例的打钩路径
            // 坐标基于圆心偏置，比例适配半径
            val unit = radius * 0.5f 
            val path = android.graphics.Path()
            path.moveTo(cx - unit * 0.45f, cy + unit * 0.05f)
            path.lineTo(cx - unit * 0.1f, cy + unit * 0.4f)
            path.lineTo(cx + unit * 0.55f, cy - unit * 0.35f)
            canvas.drawPath(path, mPaint)
        }
    }
}
