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

        seekBar.max = 6
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

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, 3)

    override fun onSetInitialValue(defaultValue: Any?) {
        val defaultInt = when (defaultValue) {
            is Int -> defaultValue
            is String -> defaultValue.toIntOrNull() ?: 3
            else -> 3
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
        }).coerceIn(0, 6)
    }

    private fun updateLabel(value: Int) {
        valueTextView?.text = (value + 1).toString()
    }
}
