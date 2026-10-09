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
     * Minimum width in dp for qualifying as a large screen (Medium/Expanded window size classes).
     * Compact: < 600dp (standard portrait phones, narrow split screen windows)
     * Medium: 600dp - 839dp (foldables unfolded, tablets in portrait, medium split windows)
     * Expanded: >= 840dp (tablets in landscape, desktop windows)
     */
    const val LARGE_SCREEN_MIN_WIDTH_DP = 600

    /**
     * Determines whether the current window configuration qualifies as a large screen.
     * Checks if the available window width is at least 600dp.
     * This covers portrait/landscape tablets, unfolded foldables, and landscape phones,
     * while gracefully falling back to single-pane mode during narrow multi-window / split-screen usage.
     */
    @JvmStatic
    fun isLargeScreen(configuration: Configuration?): Boolean {
        if (configuration == null) return false
        return configuration.screenWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP
    }
}
