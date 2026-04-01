package com.hippo.ehviewer.ui.fragment

import android.app.Activity
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import com.hippo.ehviewer.R
import com.hippo.ehviewer.ui.SettingsActivity

class LayoutFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.layout_settings)
        findPreference<Preference>("layout_enabled")?.onPreferenceChangeListener = this
        findPreference<Preference>("layout_main_persistent_nav")?.onPreferenceChangeListener = this
        findPreference<Preference>("layout_settings_two_pane")?.onPreferenceChangeListener = this
        findPreference<Preference>("layout_reader_thumbnail_sidebar")?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        requireActivity().setResult(Activity.RESULT_OK, SettingsActivity.recreateMainActivityResult())
        return true
    }

    @get:StringRes
    override val fragmentTitle: Int
        get() = R.string.settings_layout
}
