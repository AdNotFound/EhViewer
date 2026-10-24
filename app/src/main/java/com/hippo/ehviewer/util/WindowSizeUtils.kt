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

package com.hippo.ehviewer.util

import android.content.res.Configuration

object WindowSizeUtils {
    /**
     * Minimum width in dp for qualifying as a large landscape screen.
     * Compact: < 600dp (standard portrait phones, narrow split screen windows)
     * Medium/Expanded: >= 600dp (tablets, foldables, and wide landscape windows)
     */
    const val LARGE_SCREEN_MIN_WIDTH_DP = 600

    /**
     * Determines whether the current window configuration qualifies as a large screen
     * suitable for two-pane / persistent sidebar layout.
     *
     * Only landscape orientations with sufficient width (screenWidthDp >= 600dp) qualify.
     * In portrait mode, devices maintain single-pane layout to avoid cramped content panels.
     * During split-screen or multi-window mode where available landscape width drops below 600dp,
     * it gracefully falls back to single-pane mode.
     */
    @JvmStatic
    fun isLargeScreen(configuration: Configuration?): Boolean {
        if (configuration == null) return false
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            (configuration.orientation == Configuration.ORIENTATION_UNDEFINED && configuration.screenWidthDp > configuration.screenHeightDp)
        return isLandscape && configuration.screenWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP
    }
}
