package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet

class ReaderSidebarThumb @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FixedThumb(context, attrs, defStyleAttr) {

    fun resetSidebarAspect() {
        aspect = DEFAULT_ASPECT
    }

    fun applySidebarAspect(width: Int, height: Int) {
        aspect = resolveAspect(width, height)
    }

    override fun onPreSetImageDrawable(drawable: Drawable?, isTarget: Boolean) {
        if (isTarget && drawable != null) {
            applySidebarAspect(drawable.intrinsicWidth, drawable.intrinsicHeight)
        } else if (drawable == null) {
            resetSidebarAspect()
        }
        super.onPreSetImageDrawable(drawable, isTarget)
    }

    override fun onPreSetImageResource(resId: Int, isTarget: Boolean) {
        resetSidebarAspect()
        super.onPreSetImageResource(resId, isTarget)
    }

    private fun resolveAspect(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) {
            return DEFAULT_ASPECT
        }
        return (width.toFloat() / height.toFloat()).coerceIn(MIN_ASPECT, MAX_ASPECT)
    }

    companion object {
        private const val DEFAULT_ASPECT = 1f
        private const val MIN_ASPECT = 0.7f
        private const val MAX_ASPECT = 1.6f
    }
}
