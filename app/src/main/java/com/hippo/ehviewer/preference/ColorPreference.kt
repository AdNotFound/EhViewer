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
package com.hippo.ehviewer.preference

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.ThemeColors
import com.hippo.yorozuya.LayoutUtils
import com.hippo.widget.ColorView
import kotlin.math.roundToInt

class ColorPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        updateIcon()
    }

    private fun updateIcon() {
        val colorCode = Settings.themeColor
        val colorRes = ThemeColors.fromKey(colorCode).colorRes
        val color = ContextCompat.getColor(context, colorRes)
        
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(color)
        val size = LayoutUtils.dp2pix(context, 24f)
        shape.setSize(size, size)
        
        icon = shape
    }

    override fun onClick() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_color_picker_grid, null)
        val gridView = view.findViewById<GridView>(R.id.card_grid)
        
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val adapter = ColorAdapter(context, dialog)
        gridView.adapter = adapter
        gridView.onItemClickListener = adapter
        
        dialog.show()
    }

    private inner class ColorAdapter(
        private val context: Context,
        private val dialog: Dialog
    ) : BaseAdapter(), android.widget.AdapterView.OnItemClickListener {
        
        private val colors = ThemeColors.values()
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = colors.size

        override fun getItem(position: Int): Any = colors[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_color_picker, parent, false)
            val colorView = view.findViewById<ColorView>(R.id.color_view)
            val checkIcon = view.findViewById<ImageView>(R.id.check_icon)

            val item = colors[position]
            colorView.setColor(ContextCompat.getColor(context, item.colorRes))

            val isSelected = item.key == Settings.themeColor
            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            return view
        }

        override fun onItemClick(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            val item = colors[position]
            if (callChangeListener(item.key)) {
                Settings.putThemeColor(item.key)
                updateIcon()
                // Recreate activity to apply theme? 
                // Settings activity handles recreation mainly on theme change preference.
                // We might need to trigger recreation manually or rely on listener.
                // For now just save. The Activity needs a restart to pick up the new Theme.
                // We'll handle restart logic by result or listener in Fragment.
                dialog.dismiss()
            }
        }
    }
}
