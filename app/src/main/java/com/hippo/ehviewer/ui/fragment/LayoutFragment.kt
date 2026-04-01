package com.hippo.ehviewer.ui.fragment

import android.app.Activity
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.hippo.ehviewer.R
import com.hippo.ehviewer.ui.SettingsActivity

class LayoutFragment : BasePreferenceFragment() {
    private var layoutEnabledPreference: SwitchPreferenceCompat? = null
    private var mainPersistentNavPreference: SwitchPreferenceCompat? = null
    private var mainPersistentNavWidthPreference: ListPreference? = null
    private var settingsTwoPanePreference: SwitchPreferenceCompat? = null
    private var readerThumbnailSidebarPreference: SwitchPreferenceCompat? = null
    private var readerThumbnailSidebarWidthPreference: ListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.layout_settings)
        layoutEnabledPreference = findPreference("layout_enabled")
        mainPersistentNavPreference = findPreference("layout_main_persistent_nav")
        mainPersistentNavWidthPreference = findPreference("layout_main_persistent_nav_width")
        settingsTwoPanePreference = findPreference("layout_settings_two_pane")
        readerThumbnailSidebarPreference = findPreference("layout_reader_thumbnail_sidebar")
        readerThumbnailSidebarWidthPreference = findPreference("layout_reader_thumbnail_sidebar_width")

        layoutEnabledPreference?.onPreferenceChangeListener = this
        mainPersistentNavPreference?.onPreferenceChangeListener = this
        mainPersistentNavWidthPreference?.onPreferenceChangeListener = this
        settingsTwoPanePreference?.onPreferenceChangeListener = this
        readerThumbnailSidebarPreference?.onPreferenceChangeListener = this
        readerThumbnailSidebarWidthPreference?.onPreferenceChangeListener = this
        updatePreferenceVisibility()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val layoutEnabled = if (preference.key == "layout_enabled") newValue as Boolean else layoutEnabledPreference?.isChecked == true
        val mainPersistentNav = if (preference.key == "layout_main_persistent_nav") newValue as Boolean else mainPersistentNavPreference?.isChecked == true
        val readerThumbnailSidebar = if (preference.key == "layout_reader_thumbnail_sidebar") newValue as Boolean else readerThumbnailSidebarPreference?.isChecked == true
        updatePreferenceVisibility(layoutEnabled, mainPersistentNav, readerThumbnailSidebar)
        requireActivity().setResult(Activity.RESULT_OK, SettingsActivity.recreateMainActivityResult())
        return true
    }

    private fun updatePreferenceVisibility(
        layoutEnabled: Boolean = layoutEnabledPreference?.isChecked == true,
        mainPersistentNav: Boolean = mainPersistentNavPreference?.isChecked == true,
        readerThumbnailSidebar: Boolean = readerThumbnailSidebarPreference?.isChecked == true,
    ) {
        mainPersistentNavWidthPreference?.isVisible = layoutEnabled && mainPersistentNav
        readerThumbnailSidebarWidthPreference?.isVisible = layoutEnabled && readerThumbnailSidebar
    }

    @get:StringRes
    override val fragmentTitle: Int
        get() = R.string.settings_layout
}
