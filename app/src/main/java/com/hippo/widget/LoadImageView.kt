/*
 * Copyright 2015-2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import coil3.load
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.SizeResolver
import com.hippo.drawable.PreciselyClipDrawable
import com.hippo.ehviewer.R

open class LoadImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FixedAspectImageView(context, attrs, defStyleAttr),
    View.OnClickListener,
    View.OnLongClickListener {
    private var mOffsetX = Int.MIN_VALUE
    private var mOffsetY = Int.MIN_VALUE
    private var mClipWidth = Int.MIN_VALUE
    private var mClipHeight = Int.MIN_VALUE
    private var mKey: String? = null
    private var mUrl: String? = null
    private var mCrossfade = true
    private var mHardware = true
    private var mOnLoadingStateChangeListener: OnLoadingStateChangeListener? = null

    // Generation counter for guarding against stale Coil deliveries.
    // Each load() call increments this. setImageDrawable() only applies the clip
    // when the generation matches, preventing a recycled ViewHolder from displaying
    // a sprite sheet from a previous binding (clipped to the wrong position).
    private var mLoadGeneration = 0L
    private var mPendingClipGeneration = 0L
    private var mPendingClipOffsetX = Int.MIN_VALUE
    private var mPendingClipOffsetY = Int.MIN_VALUE
    private var mPendingClipWidth = Int.MIN_VALUE
    private var mPendingClipHeight = Int.MIN_VALUE

    fun interface OnLoadingStateChangeListener {
        fun onLoadingStateChanged(isLoading: Boolean)
    }

    fun setOnLoadingStateChangeListener(listener: OnLoadingStateChangeListener?) {
        mOnLoadingStateChangeListener = listener
    }

    @RetryType
    private val mRetryType: Int =
        context.obtainStyledAttributes(attrs, R.styleable.LoadImageView, defStyleAttr, 0).run {
            getInt(R.styleable.LoadImageView_retryType, 0).also { recycle() }
        }

    private fun setRetry(canRetry: Boolean) {
        when (mRetryType) {
            RETRY_TYPE_CLICK -> {
                setOnClickListener(if (canRetry) this else null)
                isClickable = canRetry
            }

            RETRY_TYPE_LONG_CLICK -> {
                setOnLongClickListener(if (canRetry) this else null)
                isLongClickable = canRetry
            }

            RETRY_TYPE_NONE -> {}
        }
    }

    fun setClip(offsetX: Int, offsetY: Int, clipWidth: Int, clipHeight: Int) {
        mOffsetX = offsetX
        mOffsetY = offsetY
        mClipWidth = clipWidth
        mClipHeight = clipHeight
    }

    fun resetClip() {
        mOffsetX = Int.MIN_VALUE
        mOffsetY = Int.MIN_VALUE
        mClipWidth = Int.MIN_VALUE
        mClipHeight = Int.MIN_VALUE
        mPendingClipOffsetX = Int.MIN_VALUE
        mPendingClipOffsetY = Int.MIN_VALUE
        mPendingClipWidth = Int.MIN_VALUE
        mPendingClipHeight = Int.MIN_VALUE
        // Bump only mLoadGeneration (NOT mPendingClipGeneration) so that any
        // in-flight Coil delivery from a previous load() sees a generation
        // mismatch and is rejected. Without this, a stale delivery would pass
        // the mPendingClipGeneration == mLoadGeneration check and display
        // the sprite sheet unclipped because mPendingClipOffsetX was just
        // cleared to Int.MIN_VALUE.
        mLoadGeneration++
    }

    fun load(
        key: String,
        url: String,
        crossfade: Boolean = true,
        hardware: Boolean = true,
    ) {
        val oldUrl = mUrl
        mKey = key
        mUrl = url
        mCrossfade = crossfade
        mHardware = hardware
        if (oldUrl != url) {
            oldUrl?.let { removeFromUrlMap(it, this) }
            addToUrlMap(url, this)
        }
        // Snapshot the current clip state and generation for this request.
        // setImageDrawable() will use these values to guard against stale
        // Coil deliveries (e.g. from a recycled ViewHolder's previous binding).
        mLoadGeneration++
        mPendingClipGeneration = mLoadGeneration
        mPendingClipOffsetX = mOffsetX
        mPendingClipOffsetY = mOffsetY
        mPendingClipWidth = mClipWidth
        mPendingClipHeight = mClipHeight
        load(url) {
            // https://coil-kt.github.io/coil/recipes/#shared-element-transitions
            allowHardware(hardware)
            placeholderMemoryCacheKey(key)
            memoryCacheKey(key)
            diskCacheKey(key)
            size(SizeResolver.ORIGINAL)
            if (!crossfade) crossfade(false)
            listener(
                {
                    setRetry(false)
                    mOnLoadingStateChangeListener?.onLoadingStateChanged(true)
                },
                {
                    setRetry(true)
                    mOnLoadingStateChangeListener?.onLoadingStateChanged(false)
                },
                { _, _ ->
                    val errorDrawable = ContextCompat.getDrawable(context, R.drawable.image_failed)
                    onPreSetImageDrawable(errorDrawable, true)
                    super.setImageDrawable(errorDrawable)
                    setRetry(true)
                    mOnLoadingStateChangeListener?.onLoadingStateChanged(false)
                },
                { _, _ ->
                    setRetry(false)
                    mOnLoadingStateChangeListener?.onLoadingStateChanged(false)
                },
            )
        }
    }

    fun load(@DrawableRes id: Int) {
        onPreSetImageResource(id, true)
        setImageResource(id)
    }

    private fun reload() {
        mUrl?.let { reloadAll(it) }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        var newDrawable = drawable
        if (newDrawable != null) {
            // Guard against stale Coil deliveries from a previous load() call.
            // When a ViewHolder is recycled and reused, the old Coil request may
            // complete after the new load() has started. The old request delivers
            // a sprite sheet clipped to the NEW position (wrong content).
            // Detect this via generation mismatch and clear the drawable instead.
            if (mPendingClipGeneration != mLoadGeneration) {
                super.setImageDrawable(null)
                mOnLoadingStateChangeListener?.onLoadingStateChanged(true)
                return
            }
            if (Int.MIN_VALUE != mPendingClipOffsetX) {
                newDrawable =
                    PreciselyClipDrawable(newDrawable, mPendingClipOffsetX, mPendingClipOffsetY, mPendingClipWidth, mPendingClipHeight)
            }
            onPreSetImageDrawable(newDrawable, true)
        }
        super.setImageDrawable(newDrawable)
        if (newDrawable != null) {
            mOnLoadingStateChangeListener?.onLoadingStateChanged(false)
        }
    }

    override fun getDrawable(): Drawable? {
        var newDrawable = super.getDrawable()
        if (newDrawable is PreciselyClipDrawable) {
            newDrawable = newDrawable.drawable
        }
        return newDrawable
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mUrl?.let { addToUrlMap(it, this) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mUrl?.let { removeFromUrlMap(it, this) }
    }

    override fun onClick(v: View) {
        reload()
    }

    override fun onLongClick(v: View): Boolean {
        reload()
        return true
    }

    open fun onPreSetImageDrawable(drawable: Drawable?, isTarget: Boolean) {}

    open fun onPreSetImageResource(resId: Int, isTarget: Boolean) {}

    @IntDef(RETRY_TYPE_NONE, RETRY_TYPE_CLICK, RETRY_TYPE_LONG_CLICK)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class RetryType

    companion object {
        const val RETRY_TYPE_NONE = 0
        const val RETRY_TYPE_CLICK = 1
        const val RETRY_TYPE_LONG_CLICK = 2

        private val urlMap = HashMap<String, MutableSet<java.lang.ref.WeakReference<LoadImageView>>>()

        private fun addToUrlMap(url: String, view: LoadImageView) {
            val set = urlMap.getOrPut(url) { java.util.Collections.newSetFromMap(java.util.WeakHashMap()) }
            set.removeAll { it.get() == null }
            set.add(java.lang.ref.WeakReference(view))
        }

        private fun removeFromUrlMap(url: String, view: LoadImageView) {
            urlMap[url]?.let { set ->
                set.removeAll { it.get() == null || it.get() == view }
                if (set.isEmpty()) urlMap.remove(url)
            }
        }

        private fun reloadAll(url: String) {
            urlMap[url]?.forEach { ref ->
                ref.get()?.let { view ->
                    if (view.isClickable || view.isLongClickable) {
                        view.mKey?.let { view.load(it, url, view.mCrossfade, view.mHardware) }
                    }
                }
            }
        }
    }
}
