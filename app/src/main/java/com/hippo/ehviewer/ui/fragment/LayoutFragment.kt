package com.hippo.ehviewer.ui.fragment

import android.app.Activity
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.hippo.ehviewer.R
import com.hippo.ehviewer.ui.SettingsActivity

class LayoutFragment : BasePreferenceFragment() {
    private var layoutEnabledPreference: SwitchPreferenceCompat? = null
    private var settingsTwoPanePreference: SwitchPreferenceCompat? = null
    private var detailTwoPanePreference: SwitchPreferenceCompat? = null
    private var detailLeftWidthPreference: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.layout_settings)
        layoutEnabledPreference = findPreference("layout_enabled")
        settingsTwoPanePreference = findPreference("layout_settings_two_pane")
        detailTwoPanePreference = findPreference("layout_detail_two_pane")
        detailLeftWidthPreference = findPreference("layout_detail_left_width")

        layoutEnabledPreference?.onPreferenceChangeListener = this
        settingsTwoPanePreference?.onPreferenceChangeListener = this
        detailTwoPanePreference?.onPreferenceChangeListener = this
        detailLeftWidthPreference?.onPreferenceChangeListener = this
        updatePreferenceVisibility()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val layoutEnabled = if (preference.key == "layout_enabled") newValue as Boolean else layoutEnabledPreference?.isChecked == true
        val detailTwoPane = if (preference.key == "layout_detail_two_pane") newValue as Boolean else detailTwoPanePreference?.isChecked == true
        updatePreferenceVisibility(layoutEnabled, detailTwoPane)
        requireActivity().setResult(Activity.RESULT_OK, SettingsActivity.recreateMainActivityResult())
        return true
    }

    private fun updatePreferenceVisibility(
        layoutEnabled: Boolean = layoutEnabledPreference?.isChecked == true,
        detailTwoPane: Boolean = detailTwoPanePreference?.isChecked == true,
    ) {
        detailLeftWidthPreference?.isVisible = layoutEnabled && detailTwoPane
    }

    @get:StringRes
    override val fragmentTitle: Int
        get() = R.string.settings_layout
}
