package com.hippo.ehviewer.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.hippo.ehviewer.R

class ScaleSeekBarPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var mValue = 1
    private var mValueTextView: TextView? = null

    init {
        layoutResource = R.layout.preference_scale_seekbar
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(R.id.scale_seekbar) as SeekBar
        mValueTextView = holder.findViewById(R.id.scale_value) as TextView

        seekBar.max = 5
        seekBar.progress = mValue
        updateLabelInfo(mValue)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mValue = progress
                    updateLabelInfo(progress)
                    persistInt(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Ensure value is persisted mainly on stop (though onProgressChanged handles it too)
                if (mValue != seekBar.progress) {
                    mValue = seekBar.progress
                    persistInt(mValue)
                }
            }
        })
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val defaultInt = if (defaultValue is Int) defaultValue else 1
        mValue = getPersistedInt(defaultInt)
    }

    private fun updateLabelInfo(value: Int) {
        val text = when (value) {
            0 -> "0.75x"
            1 -> "1x"
            2 -> "1.33x"
            3 -> "1.5x"
            4 -> "2x"
            5 -> "3x"
            else -> "1x"
        }
        mValueTextView?.text = text
    }
}
