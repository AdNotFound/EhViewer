package com.hippo.ehviewer.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.hippo.ehviewer.R
import com.hippo.ehviewer.util.ReaderSidebarLayoutUtils

class ReaderSidebarPreviewLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var isDoublePageMode = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    private lateinit var primarySlot: View
    private lateinit var secondarySlot: View
    private lateinit var primaryImage: ReaderSidebarThumb
    private lateinit var secondaryImage: ReaderSidebarThumb
    private lateinit var leadingSpace: View
    private lateinit var trailingSpace: View
    private lateinit var imageGap: View

    override fun onFinishInflate() {
        super.onFinishInflate()
        primarySlot = findViewById(R.id.image_slot)
        secondarySlot = findViewById(R.id.image_secondary_slot)
        primaryImage = findViewById(R.id.image)
        secondaryImage = findViewById(R.id.image_secondary)
        leadingSpace = findViewById(R.id.image_leading_space)
        trailingSpace = findViewById(R.id.image_trailing_space)
        imageGap = findViewById(R.id.image_gap)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val layout = ReaderSidebarLayoutUtils.calculate(
            width = contentWidth,
            gap = imageGap.layoutParams.width,
            isDoublePageMode = isDoublePageMode,
            primaryAspect = primaryImage.aspect,
            secondaryAspect = secondaryImage.aspect.takeIf { secondarySlot.visibility != GONE },
        )
        setChildWidth(primarySlot, layout.primaryWidth)
        setChildWidth(secondarySlot, layout.secondaryWidth)
        setChildWidth(leadingSpace, layout.leadingSpace)
        setChildWidth(trailingSpace, layout.trailingSpace)
        val height = resolveSize(layout.height + paddingTop + paddingBottom, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    private fun setChildWidth(view: View, width: Int) {
        // Measurement consumes these params immediately; avoid requesting another layout here.
        (view.layoutParams as LayoutParams).apply {
            this.width = width
            weight = 0f
        }
    }
}
