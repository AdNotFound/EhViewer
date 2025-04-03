/*
 * Copyright 2023 Hippo Seven
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

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.preference.DialogPreference
import com.hippo.yorozuya.ViewUtils

class DoHPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : DialogPreference(context, attrs), View.OnClickListener {

    private var mUrlInputLayout: TextInputLayout? = null
    private var mUrl: EditText? = null

    init {
        dialogLayoutResource = R.layout.preference_dialog_doh
        updateSummary(Settings.doHServer)
    }

    private fun updateSummary(url: String?) {
        summary = if (!TextUtils.isEmpty(url)) {
            context.getString(R.string.settings_advanced_doh_summary_enabled, url)
        } else {
            context.getString(R.string.settings_advanced_doh_summary_disabled)
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setPositiveButton(android.R.string.ok, null)
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this)
        mUrlInputLayout = ViewUtils.`$$`(dialog, R.id.url_input_layout) as TextInputLayout
        mUrl = ViewUtils.`$$`(dialog, R.id.url) as EditText
        mUrl?.setText(Settings.doHServer)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        mUrlInputLayout = null
        mUrl = null
    }

    override fun onClick(v: View) {
        val dialog = dialog ?: return
        val url = mUrl?.text?.toString()?.trim() ?: ""

        if (url.isNotEmpty() && !android.util.Patterns.WEB_URL.matcher(url).matches()) {
            mUrlInputLayout?.error = context.getString(R.string.doh_invalid_url)
            return
        }
        mUrlInputLayout?.error = null

        Settings.putDoHServer(if (url.isEmpty()) null else url)
        updateSummary(Settings.doHServer)
        dialog.dismiss()
    }
}
