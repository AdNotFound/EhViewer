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

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hippo.ehviewer.R
import com.hippo.ehviewer.widget.ReaderSidebarThumb

class ReaderSidebarHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val imageLeadingSpace: View = itemView.findViewById(R.id.image_leading_space)
    val imageSlot: View = itemView.findViewById(R.id.image_slot)
    val image: ReaderSidebarThumb = itemView.findViewById(R.id.image)
    val imageLoading: ProgressBar = itemView.findViewById(R.id.image_loading)
    val imageSecondarySlot: View = itemView.findViewById(R.id.image_secondary_slot)
    val imageSecondary: ReaderSidebarThumb = itemView.findViewById(R.id.image_secondary)
    val imageSecondaryLoading: ProgressBar = itemView.findViewById(R.id.image_secondary_loading)
    val imageGap: View = itemView.findViewById(R.id.image_gap)
    val imageTrailingSpace: View = itemView.findViewById(R.id.image_trailing_space)
    val text: TextView = itemView.findViewById(R.id.text)
    val indicator: View = itemView.findViewById(R.id.indicator)
}
