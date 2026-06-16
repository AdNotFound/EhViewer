package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider

class ReaderSidebarThumb @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FixedThumb(context, attrs, defStyleAttr) {

    private var lastDrawableHash = 0
    private var pendingFadeIn = false

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radiusPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    CORNER_RADIUS_DP,
                    view.resources.displayMetrics,
                )
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        clipToOutline = true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        val newHash = drawable?.hashCode() ?: 0
        if (newHash != lastDrawableHash && drawable != null && pendingFadeIn) {
            lastDrawableHash = newHash
            pendingFadeIn = false
            alpha = 0f
            // Call LoadImageView.setImageDrawable (not ImageView) so clip and loading
            // state listener are properly handled through the inheritance chain.
            super.setImageDrawable(drawable)
            animate().alpha(1f).setDuration(FADE_IN_DURATION).start()
        } else {
            lastDrawableHash = newHash
            super.setImageDrawable(drawable)
        }
    }

    fun resetForReuse() {
        pendingFadeIn = true
        lastDrawableHash = 0
    }

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
        private const val CORNER_RADIUS_DP = 4f
        private const val FADE_IN_DURATION = 200L
    }
}
