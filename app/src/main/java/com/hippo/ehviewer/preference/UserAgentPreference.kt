package com.hippo.ehviewer.preference

import android.content.Context
import android.content.DialogInterface
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.preference.DialogPreference
import com.hippo.widget.CuteSpinner
import com.hippo.yorozuya.ViewUtils

class UserAgentPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : DialogPreference(context, attrs), AdapterView.OnItemSelectedListener {
    private var mSpinner: CuteSpinner? = null
    private var mCustomInputLayout: TextInputLayout? = null
    private var mCustomInput: EditText? = null

    init {
        dialogLayoutResource = R.layout.preference_dialog_useragent
        updateSummary(Settings.userAgent)
    }

    private fun updateSummary(userAgent: String) {
        summary = userAgent
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setPositiveButton(android.R.string.ok, null)
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)
        mSpinner = ViewUtils.`$$`(dialog, R.id.type) as CuteSpinner
        mCustomInputLayout = ViewUtils.`$$`(dialog, R.id.custom_useragent_input_layout) as TextInputLayout
        mCustomInput = ViewUtils.`$$`(dialog, R.id.custom_useragent) as EditText

        val currentUA = Settings.userAgent
        val builtInIndex = Settings.builtInUserAgents.indexOf(currentUA)

        if (builtInIndex >= 0) {
            mSpinner!!.setSelection(builtInIndex)
        } else {
            mSpinner!!.setSelection(Settings.builtInUserAgents.size)
            mCustomInputLayout!!.visibility = View.VISIBLE
            mCustomInput!!.setText(currentUA)
        }

        mSpinner!!.onItemSelectedListener = this

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val position = mSpinner!!.selectedItemPosition
            val userAgent = if (position < Settings.builtInUserAgents.size) {
                Settings.builtInUserAgents[position]
            } else {
                val custom = mCustomInput!!.text.toString().trim()
                if (custom.isEmpty()) {
                    mCustomInputLayout!!.error = context.getString(R.string.text_is_empty)
                    return@setOnClickListener
                }
                custom
            }
            Settings.putUserAgent(userAgent)
            updateSummary(userAgent)
            dialog.dismiss()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        TransitionManager.beginDelayedTransition(mSpinner!!.parent as ViewGroup)
        mCustomInputLayout!!.visibility = if (position == Settings.builtInUserAgents.size) View.VISIBLE else View.GONE
        mCustomInputLayout!!.error = null
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        mSpinner = null
        mCustomInputLayout = null
        mCustomInput = null
    }
}
