/*
 * Copyright 2026 AdNotFound *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.hippo.ehviewer.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.data.GalleryPreview
import com.hippo.ehviewer.widget.ReaderSidebarThumb
import com.hippo.glgallery.GalleryView

class ReaderSidebarAdapter(
    context: Context,
    private val callbacks: Callbacks,
) : RecyclerView.Adapter<ReaderSidebarHolder>() {
    private val inflater = LayoutInflater.from(context)
    private val res = context.resources
    private var pageCount = 0
    private var currentIndex = -1
    private var previews: Map<Int, GalleryPreview> = emptyMap()
    private var pageStarts: List<Int> = emptyList()
    private var pageStartToPosition: Map<Int, Int> = emptyMap()
    private var pendingPreviewUpdate = false

    init {
        setHasStableIds(true)
    }

    interface Callbacks {
        val galleryView: GalleryView?
        val isDoublePageMode: Boolean
    }

    override fun getItemId(position: Int): Long = pageStarts[position].toLong()

    private fun updateSlotLayout(view: View, width: Int, weight: Float) {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.width == width && params.weight == weight) {
            return
        }
        params.width = width
        params.weight = weight
        view.layoutParams = params
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReaderSidebarHolder = ReaderSidebarHolder(
        inflater.inflate(R.layout.item_gallery_sidebar_preview, parent, false),
    )

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ReaderSidebarHolder, position: Int) {
        bindSidebarFull(holder, position)
    }

    override fun onViewRecycled(holder: ReaderSidebarHolder) {
        holder.image.setImageDrawable(null)
        holder.image.tag = null
        holder.imageSecondary.setImageDrawable(null)
        holder.imageSecondary.tag = null
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ReaderSidebarHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            val hasPreview = payloads.contains(PREVIEW_PAYLOAD)
            val hasIndex = payloads.contains(INDEX_PAYLOAD)
            if (hasPreview) {
                val pageStart = pageStarts[position]
                val pairSize = (callbacks.galleryView?.getPagePairSize(pageStart) ?: 1).coerceAtLeast(1)
                val pageEnd = minOf(pageCount, pageStart + pairSize)
                bindSidebarPreview(holder.image, previews[pageStart], holder.imageLoading)
                if (pageEnd - pageStart > 1) {
                    bindSidebarPreview(holder.imageSecondary, previews[pageStart + 1], holder.imageSecondaryLoading)
                }
            }
            if (hasIndex) {
                val activated = position == currentIndex
                holder.indicator.visibility = if (activated) View.VISIBLE else View.GONE
                holder.text.setTypeface(null, if (activated) Typeface.BOLD else Typeface.NORMAL)
            }
        } else {
            bindSidebarFull(holder, position)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindSidebarFull(holder: ReaderSidebarHolder, position: Int) {
        val pageStart = pageStarts[position]
        val pairSize = (callbacks.galleryView?.getPagePairSize(pageStart) ?: 1).coerceAtLeast(1)
        val pageEnd = minOf(pageCount, pageStart + pairSize)
        bindSidebarPreview(holder.image, previews[pageStart], holder.imageLoading)
        holder.image.contentDescription = res.getString(R.string.reader_sidebar_page, pageStart + 1)
        if (pageEnd - pageStart > 1) {
            holder.imageLeadingSpace.visibility = View.GONE
            holder.imageTrailingSpace.visibility = View.GONE
            updateSlotLayout(holder.imageSlot, 0, 1f)
            updateSlotLayout(holder.imageSecondarySlot, 0, 1f)
            holder.imageSecondarySlot.visibility = View.VISIBLE
            holder.imageGap.visibility = View.VISIBLE
            bindSidebarPreview(holder.imageSecondary, previews[pageStart + 1], holder.imageSecondaryLoading)
            holder.imageSecondary.contentDescription = res.getString(R.string.reader_sidebar_page, pageStart + 2)
        } else {
            holder.imageSecondary.resetClip()
            holder.imageSecondary.setImageDrawable(null)
            holder.imageSecondary.setBackgroundResource(0)
            holder.imageSecondaryLoading.visibility = View.GONE
            if (callbacks.isDoublePageMode) {
                holder.imageLeadingSpace.visibility = View.VISIBLE
                holder.imageTrailingSpace.visibility = View.VISIBLE
                updateSlotLayout(holder.imageLeadingSpace, 0, 1f)
                updateSlotLayout(holder.imageSlot, 0, 2f)
                updateSlotLayout(holder.imageTrailingSpace, 0, 1f)
                holder.imageSecondarySlot.visibility = View.GONE
                holder.imageGap.visibility = View.GONE
            } else {
                holder.imageLeadingSpace.visibility = View.GONE
                holder.imageTrailingSpace.visibility = View.GONE
                updateSlotLayout(holder.imageSlot, 0, 1f)
                updateSlotLayout(holder.imageSecondarySlot, 0, 1f)
                holder.imageSecondarySlot.visibility = View.GONE
                holder.imageGap.visibility = View.GONE
            }
        }
        holder.text.text = if (pageEnd - pageStart > 1) {
            "${pageStart + 1}-$pageEnd"
        } else {
            (pageStart + 1).toString()
        }
        val activated = position == currentIndex
        holder.indicator.visibility = if (activated) View.VISIBLE else View.GONE
        holder.itemView.alpha = 1f
        holder.text.setTypeface(null, if (activated) Typeface.BOLD else Typeface.NORMAL)
        holder.itemView.setOnClickListener {
            callbacks.galleryView?.setCurrentPage(pageStart)
        }
    }

    override fun getItemCount(): Int = pageStarts.size

    fun updateData(
        pageCount: Int,
        previews: Map<Int, GalleryPreview>,
        pageStarts: List<Int>,
        isUserScrolling: Boolean,
        currentPageIndex: Int,
    ): Boolean {
        val oldPageStarts = this.pageStarts
        this.pageCount = pageCount
        this.previews = previews
        if (oldPageStarts == pageStarts) {
            if (isUserScrolling) {
                pendingPreviewUpdate = true
            } else {
                pendingPreviewUpdate = false
                notifyItemRangeChanged(0, pageStarts.size, PREVIEW_PAYLOAD)
            }
            return false
        } else if (isUserScrolling) {
            return true
        } else {
            applyDiff(pageStarts, currentPageIndex)
            return false
        }
    }

    fun flushPendingData(newPageStarts: List<Int>?, currentPageIndex: Int) {
        if (newPageStarts != null) {
            applyDiff(newPageStarts, currentPageIndex)
            notifyItemRangeChanged(0, pageStarts.size, PREVIEW_PAYLOAD)
        } else if (pendingPreviewUpdate) {
            pendingPreviewUpdate = false
            notifyItemRangeChanged(0, pageStarts.size, PREVIEW_PAYLOAD)
        }
    }

    fun hasPendingPreviewUpdate(): Boolean = pendingPreviewUpdate

    private fun applyDiff(newPageStarts: List<Int>, currentPageIndex: Int) {
        val oldPageStarts = this.pageStarts
        val oldCurrentIndex = this.currentIndex
        val oldPageCount = this.pageCount
        val currentAlignedIndex = callbacks.galleryView?.getPagePairStart(currentPageIndex) ?: currentPageIndex
        this.pageStarts = newPageStarts
        this.pageStartToPosition = newPageStarts.withIndex().associate { (position, pageStart) -> pageStart to position }
        this.currentIndex = pageStartToPosition[currentAlignedIndex] ?: -1
        val newCurrentIndex = this.currentIndex
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = oldPageStarts.size
                override fun getNewListSize() = newPageStarts.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldPageStarts[oldPos] == newPageStarts[newPos]
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val wasActive = oldPos == oldCurrentIndex
                    val isActive = newPos == newCurrentIndex
                    if (wasActive != isActive) return false
                    val oldStart = oldPageStarts[oldPos]
                    val newStart = newPageStarts[newPos]
                    val oldPairSize = if (oldPos + 1 < oldPageStarts.size) oldPageStarts[oldPos + 1] - oldStart else oldPageCount - oldStart
                    val newPairSize = if (newPos + 1 < newPageStarts.size) newPageStarts[newPos + 1] - newStart else pageCount - newStart
                    return oldPairSize == newPairSize
                }
            },
            true,
        )
        diff.dispatchUpdatesTo(this)
    }

    fun updateCurrentIndex(index: Int) {
        val newIndex = pageStartToPosition[index] ?: -1
        if (currentIndex == newIndex) return
        val oldIndex = currentIndex
        currentIndex = newIndex
        if (oldIndex in pageStarts.indices) {
            notifyItemChanged(oldIndex, INDEX_PAYLOAD)
        }
        if (currentIndex in pageStarts.indices) {
            notifyItemChanged(currentIndex, INDEX_PAYLOAD)
        }
    }

    fun getCurrentAdapterPosition(): Int = currentIndex

    fun restoreVisiblePreviews(recyclerView: RecyclerView) {
        if (pageStarts.isEmpty()) {
            return
        }
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val holder = recyclerView.getChildViewHolder(child) as? ReaderSidebarHolder ?: continue
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || position !in pageStarts.indices) {
                continue
            }
            val pageStart = pageStarts[position]
            restoreSidebarPreviewIfNeeded(holder.image, previews[pageStart])
            val pairSize = (callbacks.galleryView?.getPagePairSize(pageStart) ?: 1).coerceAtLeast(1)
            if (pairSize > 1) {
                restoreSidebarPreviewIfNeeded(holder.imageSecondary, previews[pageStart + 1])
            }
        }
    }

    private fun restoreSidebarPreviewIfNeeded(view: ReaderSidebarThumb, preview: GalleryPreview?) {
        if (preview == null || (view.drawable != null && !isErrorState(view))) {
            return
        }
        bindSidebarPreview(view, preview, null)
    }

    private fun isErrorState(view: ReaderSidebarThumb): Boolean = view.isClickable || view.isLongClickable

    private fun bindSidebarPreview(
        view: ReaderSidebarThumb,
        preview: GalleryPreview?,
        loadingView: ProgressBar? = null,
    ) {
        if (preview != null) {
            if (view.tag == preview.position && view.drawable != null && !isErrorState(view)) return
            view.tag = preview.position
            if (view.drawable != null) view.setImageDrawable(null)
            view.resetForReuse()
            view.visibility = View.VISIBLE
            view.setBackgroundResource(0)
            if (preview.hasClipAspect()) {
                view.applySidebarAspect(preview.clipWidth, preview.clipHeight)
            } else if (preview.hasPreviewAspect()) {
                view.applySidebarAspect(preview.previewWidth, preview.previewHeight)
            } else {
                view.resetSidebarAspect()
            }
            loadingView?.visibility = View.GONE
            view.setOnLoadingStateChangeListener { isLoading ->
                loadingView?.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
            if (preview.hasClipAspect()) {
                preview.load(view)
            } else {
                view.resetClip()
                view.setImageDrawable(null)
            }
        } else {
            view.visibility = View.VISIBLE
            view.resetSidebarAspect()
            view.resetClip()
            view.setImageDrawable(null)
            view.setBackgroundResource(0)
            loadingView?.visibility = View.GONE
            view.setOnLoadingStateChangeListener(null)
        }
    }

    companion object {
        private const val PREVIEW_PAYLOAD = "preview"
        private const val INDEX_PAYLOAD = "index"
    }
}
