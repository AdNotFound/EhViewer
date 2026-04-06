package com.hippo.ehviewer.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.hippo.ehviewer.R

class SidebarWidthSeekBarPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var value = 3
    private var valueTextView: TextView? = null

    init {
        layoutResource = R.layout.preference_scale_seekbar
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(R.id.scale_seekbar) as SeekBar
        valueTextView = holder.findViewById(R.id.scale_value) as TextView

        seekBar.max = getMaxValue()
        seekBar.progress = value
        updateLabel(value)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                if (!callChangeListener(progress)) {
                    seekBar.progress = value
                    return
                }
                setValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, getDefaultValue())

    override fun onSetInitialValue(defaultValue: Any?) {
        val defaultInt = when (defaultValue) {
            is Int -> defaultValue
            is String -> defaultValue.toIntOrNull() ?: getDefaultValue()
            else -> getDefaultValue()
        }
        value = getPersistedWidth(defaultInt)
    }

    private fun setValue(newValue: Int) {
        if (value == newValue) {
            updateLabel(newValue)
            return
        }
        value = newValue
        persistString(newValue.toString())
        updateLabel(newValue)
    }

    private fun getPersistedWidth(defaultValue: Int): Int {
        val storedValue = sharedPreferences?.all?.get(key)
        return (when (storedValue) {
            is Int -> storedValue
            is Long -> storedValue.toInt()
            is String -> storedValue.toIntOrNull() ?: defaultValue
            else -> getPersistedString(defaultValue.toString())?.toIntOrNull() ?: defaultValue
        }).coerceIn(0, getMaxValue())
    }

    private fun getDefaultValue(): Int = when (key) {
        "layout_reader_thumbnail_sidebar_width" -> 11
        else -> 3
    }

    private fun getMaxValue(): Int = when (key) {
        "layout_reader_thumbnail_sidebar_width" -> READER_SIDEBAR_WIDTH_PERCENTAGES.lastIndex
        else -> 6
    }

    private fun updateLabel(value: Int) {
        val percentages = when (key) {
            "layout_reader_thumbnail_sidebar_width" -> READER_SIDEBAR_WIDTH_PERCENTAGES
            "layout_main_persistent_nav_width" -> MAIN_SIDEBAR_WIDTH_PERCENTAGES
            "layout_detail_left_width" -> DETAIL_SIDEBAR_WIDTH_PERCENTAGES
            else -> null
        }
        valueTextView?.text = percentages?.getOrNull(value)?.let { "$it%" } ?: (value + 1).toString()
    }

    companion object {
        private val READER_SIDEBAR_WIDTH_PERCENTAGES = IntArray(21) { 5 + it }
        private val MAIN_SIDEBAR_WIDTH_PERCENTAGES = intArrayOf(10, 14, 18, 22, 27, 31, 35)
        private val DETAIL_SIDEBAR_WIDTH_PERCENTAGES = intArrayOf(25, 31, 37, 43, 49, 55, 60)
    }
}
