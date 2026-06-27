package com.hippo.ehviewer.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.hippo.ehviewer.R

class SpeedSeekBarPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var mValue = 64
    private var mValueTextView: TextView? = null

    init {
        layoutResource = R.layout.preference_speed_seekbar
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(R.id.speed_seekbar) as SeekBar
        mValueTextView = holder.findViewById(R.id.speed_value) as TextView

        seekBar.progress = valueToProgress(mValue)
        updateLabelInfo(mValue)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val v = progressToValue(progress)
                    mValue = v
                    updateLabelInfo(v)
                    persistInt(v)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val v = progressToValue(seekBar.progress)
                if (mValue != v) {
                    mValue = v
                    persistInt(mValue)
                }
            }
        })
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val defaultInt = if (defaultValue is Int) defaultValue else 64
        mValue = getPersistedInt(defaultInt)
    }

    private fun updateLabelInfo(value: Int) {
        mValueTextView?.text = if (value == 0) "OFF" else "${value}KB"
    }

    companion object {
        private val VALUES = intArrayOf(0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)

        private fun valueToProgress(value: Int): Int {
            val idx = VALUES.indexOf(value)
            return if (idx >= 0) idx else 7
        }

        private fun progressToValue(progress: Int): Int {
            return VALUES[progress.coerceIn(0, VALUES.size - 1)]
        }
    }
}
