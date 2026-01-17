/*
 * Copyright 2022 Tarsin Norbin
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
package com.hippo.ehviewer.ui

import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import com.hippo.ehviewer.R

enum class ThemeColors(
    val key: String,
    @ColorRes val colorRes: Int,
    @StyleRes val styleRes: Int
) {
    TEAL("teal", R.color.teal_500, R.style.ThemeOverlay_EhViewer_Accent_Teal),
    RED("red", R.color.red_500, R.style.ThemeOverlay_EhViewer_Accent_Red),
    PINK("pink", R.color.pink_500, R.style.ThemeOverlay_EhViewer_Accent_Pink),
    PURPLE("purple", R.color.purple_500, R.style.ThemeOverlay_EhViewer_Accent_Purple),
    DEEP_PURPLE("deep_purple", R.color.deep_purple_500, R.style.ThemeOverlay_EhViewer_Accent_DeepPurple),
    INDIGO("indigo", R.color.indigo_500, R.style.ThemeOverlay_EhViewer_Accent_Indigo),
    BLUE("blue", R.color.blue_500, R.style.ThemeOverlay_EhViewer_Accent_Blue),
    LIGHT_BLUE("light_blue", R.color.light_blue_500, R.style.ThemeOverlay_EhViewer_Accent_LightBlue),
    CYAN("cyan", R.color.cyan_500, R.style.ThemeOverlay_EhViewer_Accent_Cyan),
    GREEN("green", R.color.green_500, R.style.ThemeOverlay_EhViewer_Accent_Green),
    LIGHT_GREEN("light_green", R.color.light_green_500, R.style.ThemeOverlay_EhViewer_Accent_LightGreen),
    LIME("lime", R.color.lime_500, R.style.ThemeOverlay_EhViewer_Accent_Lime),
    YELLOW("yellow", R.color.yellow_500, R.style.ThemeOverlay_EhViewer_Accent_Yellow),
    AMBER("amber", R.color.amber_500, R.style.ThemeOverlay_EhViewer_Accent_Amber),
    ORANGE("orange", R.color.orange_500, R.style.ThemeOverlay_EhViewer_Accent_Orange),
    DEEP_ORANGE("deep_orange", R.color.deep_orange_500, R.style.ThemeOverlay_EhViewer_Accent_DeepOrange),
    BROWN("brown", R.color.brown_700, R.style.ThemeOverlay_EhViewer_Accent_Brown),
    GREY("grey", R.color.grey_500, R.style.ThemeOverlay_EhViewer_Accent_Grey),
    BLUE_GREY("blue_grey", R.color.blue_grey_500, R.style.ThemeOverlay_EhViewer_Accent_BlueGrey);

    companion object {
        fun fromKey(key: String): ThemeColors =
            entries.find { it.key == key } ?: TEAL
    }
}
