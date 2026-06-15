/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.ehviewer

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.hippo.ehviewer.EhApplication.Companion.application
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.ui.scene.GalleryListScene
import com.hippo.glgallery.GalleryView
import com.hippo.okhttp.UAPresets
import com.hippo.unifile.UniFile
import com.hippo.yorozuya.NumberUtils
import java.util.Locale

@Suppress("SameParameterValue")
object Settings {
    /********************
     ****** Eh
     ********************/
    const val KEY_ACCOUNT = "account"
    const val KEY_GALLERY_SITE = "gallery_site"
    private const val DEFAULT_GALLERY_SITE = 0
    private const val KEY_IMAGE_LIMITS = "image_limits"
    private const val KEY_U_CONFIG = "uconfig"
    private const val KEY_MY_TAGS = "mytags"
    const val KEY_THEME = "theme"
    private const val DEFAULT_THEME = -1
    const val KEY_BLACK_DARK_THEME = "black_dark_theme"
    private const val DEFAULT_BLACK_DARK_THEME = false
    private const val KEY_LAUNCH_PAGE = "launch_page"
    private const val DEFAULT_LAUNCH_PAGE = 0
    const val KEY_LIST_MODE = "list_mode"
    private const val DEFAULT_LIST_MODE = 0
    const val KEY_DETAIL_SIZE = "detail_size_"
    private const val DEFAULT_DETAIL_SIZE = 8
    const val KEY_LIST_THUMB_SIZE = "list_cover_size"
    private const val DEFAULT_LIST_THUMB_SIZE = 40
    const val KEY_THUMB_SIZE = "cover_size"
    private const val DEFAULT_THUMB_SIZE = 4
    const val KEY_THUMB_SHOW_TITLE = "thumb_show_title"
    private const val DEFAULT_THUMB_SHOW_TITLE = true
    const val KEY_FORCE_EH_THUMB = "force_eh_thumb"
    private const val DEFAULT_FORCE_EH_THUMB = false
    private const val KEY_SHOW_JPN_TITLE = "show_jpn_title"
    private const val DEFAULT_SHOW_JPN_TITLE = false
    private const val KEY_SHOW_GALLERY_PAGES = "show_gallery_pages"
    private const val DEFAULT_SHOW_GALLERY_PAGES = true
    private const val KEY_SHOW_COMMENTS = "show_gallery_comments"
    private const val DEFAULT_SHOW_COMMENTS = true
    private const val KEY_COMMENT_THRESHOLD = "comment_threshold"
    private const val DEFAULT_COMMENT_THRESHOLD = -101
    private const val KEY_PREVIEW_NUM = "preview_num"
    private const val DEFAULT_PREVIEW_NUM = 60
    private const val KEY_PREVIEW_SIZE = "preview_size"
    private const val DEFAULT_PREVIEW_SIZE = 3
    const val KEY_SHOW_TAG_TRANSLATIONS = "show_tag_translations"
    private const val DEFAULT_SHOW_TAG_TRANSLATIONS = false
    private const val KEY_TRANSLATIONS_LAST_UPDATE = "translations_last_update"
    const val KEY_TAG_TRANSLATIONS_SOURCE = "tag_translations_source"
    private const val KEY_METERED_NETWORK_WARNING = "cellular_network_warning"
    private const val DEFAULT_METERED_NETWORK_WARNING = false
    private const val KEY_REQUEST_NEWS = "request_news"
    private const val DEFAULT_REQUEST_NEWS = false
    private const val KEY_HIDE_HV_EVENTS = "hide_hv_events"
    private const val DEFAULT_HIDE_HV_EVENTS = false
    val SIGN_IN_REQUIRED = arrayOf(
        KEY_GALLERY_SITE,
        KEY_IMAGE_LIMITS,
        KEY_U_CONFIG,
        KEY_MY_TAGS,
        KEY_FORCE_EH_THUMB,
        KEY_SHOW_JPN_TITLE,
        KEY_REQUEST_NEWS,
        KEY_HIDE_HV_EVENTS,
    )

    /********************
     ****** Read
     ********************/
    private const val KEY_SCREEN_ROTATION = "screen_rotation"
    private const val DEFAULT_SCREEN_ROTATION = 0
    private const val KEY_READING_DIRECTION = "reading_direction"
    private const val DEFAULT_READING_DIRECTION = GalleryView.LAYOUT_RIGHT_TO_LEFT
    private const val KEY_PAGE_SCALING = "page_scaling"
    private const val DEFAULT_PAGE_SCALING = GalleryView.SCALE_FIT
    private const val KEY_START_POSITION = "start_position"
    private const val DEFAULT_START_POSITION = GalleryView.START_POSITION_TOP_RIGHT
    private const val KEY_READ_THEME = "read_theme"
    private const val DEFAULT_READ_THEME = 1
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val DEFAULT_KEEP_SCREEN_ON = false
    private const val KEY_SHOW_CLOCK = "gallery_show_clock"
    private const val DEFAULT_SHOW_CLOCK = true
    private const val KEY_SHOW_PROGRESS = "gallery_show_progress"
    private const val DEFAULT_SHOW_PROGRESS = true
    private const val KEY_SHOW_BATTERY = "gallery_show_battery"
    private const val DEFAULT_SHOW_BATTERY = true
    private const val KEY_SHOW_PAGE_INTERVAL = "gallery_show_page_interval"
    private const val DEFAULT_SHOW_PAGE_INTERVAL = false
    private const val KEY_TURN_PAGE_INTERVAL = "turn_page_interval"
    private const val DEFAULT_TURN_PAGE_INTERVAL = 5
    private const val KEY_VOLUME_PAGE = "volume_page"
    private const val DEFAULT_VOLUME_PAGE = false
    private const val KEY_VOLUME_PAGE_INTERVAL = "volume_page_interval"
    private const val DEFAULT_VOLUME_PAGE_INTERVAL = 1
    private const val KEY_REVERSE_VOLUME_PAGE = "reserve_volume_page"
    private const val DEFAULT_REVERSE_VOLUME_PAGE = false
    private const val KEY_READING_FULLSCREEN = "reading_fullscreen"
    private const val VALUE_READING_FULLSCREEN = true
    private const val KEY_CUSTOM_SCREEN_LIGHTNESS = "custom_screen_lightness"
    private const val DEFAULT_CUSTOM_SCREEN_LIGHTNESS = false
    private const val KEY_SCREEN_LIGHTNESS = "screen_lightness"
    private const val DEFAULT_SCREEN_LIGHTNESS = 50
    const val KEY_DOUBLE_PAGE_MODE = "double_page_mode"
    private const val DEFAULT_DOUBLE_PAGE_MODE = false
    const val KEY_DOUBLE_PAGE_MODE_LANDSCAPE = "double_page_mode_landscape"
    private const val DEFAULT_DOUBLE_PAGE_MODE_LANDSCAPE = true
    const val KEY_DOUBLE_PAGE_OFFSET = "double_page_offset"
    private const val DEFAULT_DOUBLE_PAGE_OFFSET = false
    private const val KEY_STRIP_EXTRANEOUS_ADS = "strip_extraneous_ads"
    private const val DEFAULT_STRIP_EXTRANEOUS_ADS = false
    const val KEY_DOUBLE_PAGE_GAP = "double_page_gap"
    private const val DEFAULT_DOUBLE_PAGE_GAP = 0
    const val KEY_READ_IMAGE_LIMIT = "read_image_limit"
    private const val DEFAULT_READ_IMAGE_LIMIT = 1 // 1x equivalent (index 1 in [3/4, 1, 4/3, 1.5, 2, 3])

    /********************
     ****** Layout
     ********************/
    private const val KEY_LAYOUT_ENABLED = "layout_enabled"
    private const val DEFAULT_LAYOUT_ENABLED = false
    private const val KEY_LAYOUT_MAIN_PERSISTENT_NAV = "layout_main_persistent_nav"
    private const val DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV = false
    private const val KEY_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE = "layout_main_persistent_nav_visible"
    private const val DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE = true
    private const val KEY_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH = "layout_main_persistent_nav_width"
    private const val DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH = 3
    private const val KEY_LAYOUT_SETTINGS_TWO_PANE = "layout_settings_two_pane"
    private const val DEFAULT_LAYOUT_SETTINGS_TWO_PANE = false
    private const val KEY_LAYOUT_DETAIL_TWO_PANE = "layout_detail_two_pane"
    private const val DEFAULT_LAYOUT_DETAIL_TWO_PANE = false
    private const val KEY_LAYOUT_DETAIL_LEFT_WIDTH = "layout_detail_left_width"
    private const val DEFAULT_LAYOUT_DETAIL_LEFT_WIDTH = 3
    private const val KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR = "layout_reader_thumbnail_sidebar"
    private const val DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR = false
    private const val KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH = "layout_reader_thumbnail_sidebar_width"
    private const val DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH = 13
    private const val KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_VISIBLE = "layout_reader_thumbnail_sidebar_visible"
    private const val DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_VISIBLE = true
    private const val KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT = "layout_reader_thumbnail_sidebar_on_right"
    private const val DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT = true
    private const val KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_TOGGLE_AUTO_HIDE = "layout_reader_thumbnail_sidebar_toggle_auto_hide"
    private const val DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_TOGGLE_AUTO_HIDE = false
    private const val KEY_LAYOUT_SIDEBAR_WIDTH_FIVE_LEVELS_MIGRATED = "layout_sidebar_width_five_levels_migrated"
    private const val KEY_LAYOUT_SIDEBAR_WIDTH_SEVEN_LEVELS_MIGRATED = "layout_sidebar_width_seven_levels_migrated"
    private const val KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH_MIGRATED =
        "layout_reader_thumbnail_sidebar_width_migrated"

    /********************
     ****** Download
     ********************/
    const val KEY_DOWNLOAD_LOCATION = "download_location"
    private const val KEY_DOWNLOAD_SAVE_SCHEME = "image_scheme"
    private const val KEY_DOWNLOAD_SAVE_AUTHORITY = "image_authority"
    private const val KEY_DOWNLOAD_SAVE_PATH = "image_path"
    private const val KEY_DOWNLOAD_SAVE_QUERY = "image_query"
    private const val KEY_DOWNLOAD_SAVE_FRAGMENT = "image_fragment"
    const val KEY_MEDIA_SCAN = "media_scan"
    private const val DEFAULT_MEDIA_SCAN = false
    const val KEY_MULTI_THREAD_DOWNLOAD = "download_thread"
    private const val DEFAULT_MULTI_THREAD_DOWNLOAD = 3
    const val KEY_DOWNLOAD_DELAY = "download_delay_2"
    private const val DEFAULT_DOWNLOAD_DELAY = 1000
    private const val KEY_DOWNLOAD_TIMEOUT = "download_timeout"
    private const val DEFAULT_DOWNLOAD_TIMEOUT = 60
    const val KEY_TIMEOUT_SPEED = "timeout_speed"
    private const val DEFAULT_TIMEOUT_SPEED = 64
    const val KEY_PRELOAD_IMAGE = "preload_image"
    private const val DEFAULT_PRELOAD_IMAGE = 5
    const val KEY_DOWNLOAD_ORIGIN_IMAGE = "download_origin_image_"
    private const val DEFAULT_DOWNLOAD_ORIGIN_IMAGE = 0

    /********************
     ****** Privacy and Security
     ********************/
    private const val KEY_SECURITY = "security"
    private const val DEFAULT_SECURITY = ""
    private const val KEY_ENABLE_FINGERPRINT = "enable_fingerprint"
    private const val DEFAULT_ENABLE_FINGERPRINT = false
    private const val KEY_SEC_SECURITY = "enable_secure"
    private const val DEFAULT_SEC_SECURITY = false

    /********************
     ****** Advanced
     ********************/
    private const val KEY_SAVE_PARSE_ERROR_BODY = "save_parse_error_body"
    private const val DEFAULT_SAVE_PARSE_ERROR_BODY = true
    private const val KEY_SAVE_CRASH_LOG = "save_crash_log"
    private const val DEFAULT_SAVE_CRASH_LOG = true
    private const val KEY_READ_CACHE_SIZE = "read_cache_size"
    private const val DEFAULT_READ_CACHE_SIZE = 320
    const val KEY_APP_LANGUAGE = "app_language"
    private const val DEFAULT_APP_LANGUAGE = "system"

    private const val KEY_USER_AGENT = "user_agent"
    val DEFAULT_USER_AGENT: String get() = UAPresets.WEBVIEW_ANDROID
    private const val KEY_PROXY_TYPE = "proxy_type"
    private const val DEFAULT_PROXY_TYPE = EhProxySelector.TYPE_SYSTEM
    private const val KEY_PROXY_IP = "proxy_ip"
    private val DEFAULT_PROXY_IP: String? = null
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val DEFAULT_PROXY_PORT = -1
    private const val KEY_BUILT_IN_HOSTS = "built_in_hosts_2"
    private const val DEFAULT_BUILT_IN_HOSTS = false
    private const val KEY_DOH = "dns_over_https"
    private const val DEFAULT_DOH = false
    private const val KEY_DOH_SERVER = "doh_server"
    private const val DEFAULT_DOH_SERVER = "https://cloudflare-dns.com/dns-query"
    const val KEY_DOMAIN_FRONTING = "domain_fronting"
    private const val DEFAULT_DOMAIN_FRONTING = false
    const val KEY_BYPASS_VPN = "bypass_vpn"
    private const val DEFAULT_BYPASS_VPN = false
    private const val KEY_APP_LINK_VERIFY_TIP = "app_link_verify_tip"
    private const val DEFAULT_APP_LINK_VERIFY_TIP = false
    private const val KEY_DOH_ENABLED = "dns_over_https"
    private const val DEFAULT_DOH_ENABLED = false
    const val KEY_DEVELOPER_MODE = "developer_mode"
    private const val DEFAULT_DEVELOPER_MODE = false

    /********************
     ****** Favorites
     ********************/
    private const val KEY_FAV_CAT_0 = "fav_cat_0"
    private const val KEY_FAV_CAT_1 = "fav_cat_1"
    private const val KEY_FAV_CAT_2 = "fav_cat_2"
    private const val KEY_FAV_CAT_3 = "fav_cat_3"
    private const val KEY_FAV_CAT_4 = "fav_cat_4"
    private const val KEY_FAV_CAT_5 = "fav_cat_5"
    private const val KEY_FAV_CAT_6 = "fav_cat_6"
    private const val KEY_FAV_CAT_7 = "fav_cat_7"
    private const val KEY_FAV_CAT_8 = "fav_cat_8"
    private const val KEY_FAV_CAT_9 = "fav_cat_9"
    private const val DEFAULT_FAV_CAT_0 = "Favorites 0"
    private const val DEFAULT_FAV_CAT_1 = "Favorites 1"
    private const val DEFAULT_FAV_CAT_2 = "Favorites 2"
    private const val DEFAULT_FAV_CAT_3 = "Favorites 3"
    private const val DEFAULT_FAV_CAT_4 = "Favorites 4"
    private const val DEFAULT_FAV_CAT_5 = "Favorites 5"
    private const val DEFAULT_FAV_CAT_6 = "Favorites 6"
    private const val DEFAULT_FAV_CAT_7 = "Favorites 7"
    private const val DEFAULT_FAV_CAT_8 = "Favorites 8"
    private const val DEFAULT_FAV_CAT_9 = "Favorites 9"
    private const val KEY_FAV_COUNT_0 = "fav_count_0"
    private const val KEY_FAV_COUNT_1 = "fav_count_1"
    private const val KEY_FAV_COUNT_2 = "fav_count_2"
    private const val KEY_FAV_COUNT_3 = "fav_count_3"
    private const val KEY_FAV_COUNT_4 = "fav_count_4"
    private const val KEY_FAV_COUNT_5 = "fav_count_5"
    private const val KEY_FAV_COUNT_6 = "fav_count_6"
    private const val KEY_FAV_COUNT_7 = "fav_count_7"
    private const val KEY_FAV_COUNT_8 = "fav_count_8"
    private const val KEY_FAV_COUNT_9 = "fav_count_9"
    private const val KEY_FAV_LOCAL = "fav_local"
    private const val KEY_FAV_CLOUD = "fav_cloud"
    private const val DEFAULT_FAV_COUNT = 0
    private const val KEY_RECENT_FAV_CAT = "recent_fav_cat"
    private const val DEFAULT_RECENT_FAV_CAT = FavListUrlBuilder.FAV_CAT_ALL

    // -1 for local, 0 - 9 for cloud favorite, other for no default fav slot
    const val INVALID_DEFAULT_FAV_SLOT = -2
    private const val KEY_DEFAULT_FAV_SLOT = "default_favorite_2"
    private const val DEFAULT_DEFAULT_FAV_SLOT = INVALID_DEFAULT_FAV_SLOT
    private const val KEY_NEVER_ADD_FAV_NOTES = "never_add_favorite_notes"
    private const val DEFAULT_NEVER_ADD_FAV_NOTES = false

    /********************
     ****** Guide
     ********************/
    private const val KEY_GUIDE_GALLERY = "guide_gallery"
    private const val DEFAULT_GUIDE_GALLERY = true

    /********************
     ****** Others
     ********************/
    private val TAG = Settings::class.java.simpleName
    private const val KEY_SELECT_SITE = "select_site"
    private const val DEFAULT_SELECT_SITE = true
    private const val KEY_NEED_SIGN_IN = "need_sign_in"
    private const val DEFAULT_NEED_SIGN_IN = true
    private const val KEY_DISPLAY_NAME = "display_name"
    private val DEFAULT_DISPLAY_NAME: String? = null
    private const val KEY_AVATAR = "avatar"
    private val DEFAULT_AVATAR: String? = null
    private const val KEY_QS_SAVE_PROGRESS = "qs_save_progress"
    private const val DEFAULT_QS_SAVE_PROGRESS = false
    private const val KEY_HAS_DEFAULT_DOWNLOAD_LABEL = "has_default_download_label"
    private const val DEFAULT_HAS_DOWNLOAD_LABEL = false
    private const val KEY_DEFAULT_DOWNLOAD_LABEL = "default_download_label"
    private val DEFAULT_DOWNLOAD_LABEL: String? = null
    private const val KEY_RECENT_DOWNLOAD_LABEL = "recent_download_label"
    private val DEFAULT_RECENT_DOWNLOAD_LABEL: String? = null
    private const val KEY_DEFAULT_SORTING_METHOD = "default_sorting_method"
    private const val DEFAULT_SORTING_METHOD = 0
    private const val KEY_DEFAULT_TOP_LIST = "default_top_list"
    private const val DEFAULT_TOP_LIST = "15"
    private const val KEY_REMOVE_IMAGE_FILES = "include_pic"
    private const val DEFAULT_REMOVE_IMAGE_FILES = true
    private const val KEY_CLIPBOARD_TEXT_HASH_CODE = "clipboard_text_hash_code"
    private const val DEFAULT_CLIPBOARD_TEXT_HASH_CODE = 0
    private const val KEY_ARCHIVE_PASSWDS = "archive_passwds"
    private const val KEY_NOTIFICATION_REQUIRED = "notification_required"
    private const val KEY_SAVED_CATEGORY = "saved_category"
    private lateinit var sSettingsPre: SharedPreferences

    fun initialize() {
        sSettingsPre = PreferenceManager.getDefaultSharedPreferences(application)
        fixDefaultValue()
    }

    private fun fixDefaultValue() {
        if ("zh" == Locale.getDefault().language) {
            // Enable show tag translations if the language is zh
            if (!sSettingsPre.contains(KEY_SHOW_TAG_TRANSLATIONS)) {
                putShowTagTranslations(true)
            }
            // Enable builtInHosts if the language is zh
            if (!sSettingsPre.contains(KEY_BUILT_IN_HOSTS)) {
                putBuiltInHosts(true)
            }
            // Enable DoF if the language is zh
            if (!sSettingsPre.contains(KEY_DOMAIN_FRONTING)) {
                putDoF(true)
            }
        }
        migrateLegacyLayoutSidebarWidths()
        migrateLayoutSidebarWidthsToSevenLevels()
        migrateReaderThumbnailSidebarWidth()
        val enableLargeScreenLayout = application.resources.configuration.smallestScreenWidthDp >= 600
        if (!sSettingsPre.contains(KEY_LAYOUT_ENABLED)) {
            putLayoutEnabled(enableLargeScreenLayout)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_MAIN_PERSISTENT_NAV)) {
            putLayoutMainPersistentNav(enableLargeScreenLayout)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE)) {
            putLayoutMainPersistentNavVisible(DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH)) {
            putLayoutMainPersistentNavWidth(DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_SETTINGS_TWO_PANE)) {
            putLayoutSettingsTwoPane(enableLargeScreenLayout)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_DETAIL_TWO_PANE)) {
            putLayoutDetailTwoPane(enableLargeScreenLayout)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_DETAIL_LEFT_WIDTH)) {
            putLayoutDetailLeftWidth(DEFAULT_LAYOUT_DETAIL_LEFT_WIDTH)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR)) {
            putLayoutReaderThumbnailSidebar(enableLargeScreenLayout)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH)) {
            putLayoutReaderThumbnailSidebarWidth(DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_VISIBLE)) {
            putLayoutReaderThumbnailSidebarVisible(true)
        }
        if (!sSettingsPre.contains(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT)) {
            putLayoutReaderThumbnailSidebarOnRight(DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT)
        }
    }

    private fun migrateLegacyLayoutSidebarWidths() {
        if (sSettingsPre.getBoolean(KEY_LAYOUT_SIDEBAR_WIDTH_FIVE_LEVELS_MIGRATED, false)) {
            return
        }
        migrateLegacyLayoutSidebarWidth(KEY_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH)
        migrateLegacyLayoutSidebarWidth(KEY_LAYOUT_DETAIL_LEFT_WIDTH)
        migrateLegacyLayoutSidebarWidth(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH)
        putBoolean(KEY_LAYOUT_SIDEBAR_WIDTH_FIVE_LEVELS_MIGRATED, true)
    }

    private fun migrateLayoutSidebarWidthsToSevenLevels() {
        if (sSettingsPre.getBoolean(KEY_LAYOUT_SIDEBAR_WIDTH_SEVEN_LEVELS_MIGRATED, false)) {
            return
        }
        migrateLayoutSidebarWidthToSevenLevels(KEY_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH)
        migrateLayoutSidebarWidthToSevenLevels(KEY_LAYOUT_DETAIL_LEFT_WIDTH)
        migrateLayoutSidebarWidthToSevenLevels(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH)
        putBoolean(KEY_LAYOUT_SIDEBAR_WIDTH_SEVEN_LEVELS_MIGRATED, true)
    }

    private fun migrateLegacyLayoutSidebarWidth(key: String) {
        val rawValue = when (val storedValue = sSettingsPre.all[key]) {
            is Int -> storedValue
            is Long -> storedValue.toInt()
            is String -> storedValue.toIntOrNull()
            else -> null
        } ?: return
        val migratedValue = when (rawValue) {
            0 -> 0
            1 -> 2
            2 -> 4
            else -> rawValue.coerceIn(0, 4)
        }
        if (migratedValue != rawValue) {
            putIntToStr(key, migratedValue)
        }
    }

    private fun migrateLayoutSidebarWidthToSevenLevels(key: String) {
        val rawValue = when (val storedValue = sSettingsPre.all[key]) {
            is Int -> storedValue
            is Long -> storedValue.toInt()
            is String -> storedValue.toIntOrNull()
            else -> null
        } ?: return
        val migratedValue = when (rawValue) {
            0 -> 0
            1 -> 1
            2 -> 3
            3 -> 5
            4 -> 6
            else -> rawValue.coerceIn(0, 6)
        }
        if (migratedValue != rawValue) {
            putIntToStr(key, migratedValue)
        }
    }

    private fun migrateReaderThumbnailSidebarWidth() {
        if (sSettingsPre.getBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH_MIGRATED, false)) {
            return
        }
        val key = KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH
        val rawValue = when (val storedValue = sSettingsPre.all[key]) {
            is Int -> storedValue
            is Long -> storedValue.toInt()
            is String -> storedValue.toIntOrNull()
            else -> null
        }
        // Migrate from 7-level (0-6) or 21-level (0-20) to 33-level (0-32)
        val migratedValue = when (rawValue) {
            null -> null
            0 -> 2
            1 -> 7
            2 -> 12
            3 -> 17
            4 -> 22
            5 -> 27
            6 -> 32
            in 7..20 -> rawValue + 2
            else -> rawValue.coerceIn(0, 32)
        }
        migratedValue?.let {
            if (it != rawValue) {
                putIntToStr(key, it)
            }
        }
        putBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH_MIGRATED, true)
    }

    private fun getBoolean(key: String, defValue: Boolean): Boolean = try {
        sSettingsPre.getBoolean(key, defValue)
    } catch (e: ClassCastException) {
        Log.d(TAG, "Get ClassCastException when get $key value", e)
        defValue
    }

    private fun putBoolean(key: String, value: Boolean) {
        sSettingsPre.edit { putBoolean(key, value) }
    }

    @JvmStatic
    fun getInt(key: String, defValue: Int): Int = try {
        sSettingsPre.getInt(key, defValue)
    } catch (e: ClassCastException) {
        Log.d(TAG, "Get ClassCastException when get $key value", e)
        defValue
    }

    @JvmStatic
    fun putInt(key: String, value: Int) {
        sSettingsPre.edit { putInt(key, value) }
    }

    private fun getLong(key: String, defValue: Long): Long = try {
        sSettingsPre.getLong(key, defValue)
    } catch (e: ClassCastException) {
        Log.d(TAG, "Get ClassCastException when get $key value", e)
        defValue
    }

    private fun putLong(key: String, value: Long) {
        sSettingsPre.edit { putLong(key, value) }
    }

    private fun getString(key: String, defValue: String?): String? = try {
        sSettingsPre.getString(key, defValue)
    } catch (e: ClassCastException) {
        Log.d(TAG, "Get ClassCastException when get $key value", e)
        defValue
    }

    private fun putString(key: String, value: String?) {
        sSettingsPre.edit { putString(key, value) }
    }

    private fun getStringSet(key: String): MutableSet<String>? = sSettingsPre.getStringSet(key, null)

    private fun putStringToStringSet(key: String, value: String) {
        var set = getStringSet(key)
        if (set == null) {
            set =
                mutableSetOf(value)
        } else if (set.contains(value)) {
            return
        } else {
            set.add(value)
        }
        sSettingsPre.edit { putStringSet(key, set) }
    }

    private fun getIntFromStr(key: String, defValue: Int): Int = try {
        NumberUtils.parseIntSafely(
            sSettingsPre.getString(key, defValue.toString()),
            defValue,
        )
    } catch (e: ClassCastException) {
        Log.d(TAG, "Get ClassCastException when get $key value", e)
        defValue
    }

    private fun putIntToStr(key: String, value: Int) {
        sSettingsPre.edit { putString(key, value.toString()) }
    }

    private fun dip2px(dpValue: Int): Int {
        val scale = application.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    val locale: Locale
        get() {
            return if (appLanguage != null && appLanguage != "system") {
                Locale.forLanguageTag(appLanguage!!)
            } else {
                Locale.getDefault()
            }
        }

    val gallerySite: Int
        get() = getIntFromStr(KEY_GALLERY_SITE, DEFAULT_GALLERY_SITE)
    fun putGallerySite(value: Int) {
        putIntToStr(KEY_GALLERY_SITE, value)
    }

    val theme: Int
        get() = getIntFromStr(KEY_THEME, DEFAULT_THEME)
    fun putTheme(theme: Int) {
        putIntToStr(KEY_THEME, theme)
    }

    val blackDarkTheme
        get() = getBoolean(KEY_BLACK_DARK_THEME, DEFAULT_BLACK_DARK_THEME)

    val launchPageGalleryListSceneAction: String
        get() {
            return when (getIntFromStr(KEY_LAUNCH_PAGE, DEFAULT_LAUNCH_PAGE)) {
                3 -> GalleryListScene.ACTION_TOP_LIST
                2 -> GalleryListScene.ACTION_WHATS_HOT
                1 -> GalleryListScene.ACTION_SUBSCRIPTION
                else -> GalleryListScene.ACTION_HOMEPAGE
            }
        }

    val listMode: Int
        get() = getIntFromStr(KEY_LIST_MODE, DEFAULT_LIST_MODE)

    val detailSize: Int
        get() = dip2px(40 * getInt(KEY_DETAIL_SIZE, DEFAULT_DETAIL_SIZE))

    val listThumbSize: Int
        get() = dip2px(2 * getInt(KEY_LIST_THUMB_SIZE, DEFAULT_LIST_THUMB_SIZE))
    val listTitleSingleLine: Boolean
        get() = getInt(KEY_LIST_THUMB_SIZE, DEFAULT_LIST_THUMB_SIZE) < DEFAULT_LIST_THUMB_SIZE - 2

    val thumbSize: Int
        get() = dip2px(40 * getInt(KEY_THUMB_SIZE, DEFAULT_THUMB_SIZE))

    val thumbShowTitle: Boolean
        get() = getBoolean(KEY_THUMB_SHOW_TITLE, DEFAULT_THUMB_SHOW_TITLE)

    val forceEhThumb: Boolean
        get() = getBoolean(KEY_FORCE_EH_THUMB, DEFAULT_FORCE_EH_THUMB)

    val showJpnTitle: Boolean
        get() = getBoolean(KEY_SHOW_JPN_TITLE, DEFAULT_SHOW_JPN_TITLE)

    val showGalleryPages: Boolean
        get() = getBoolean(KEY_SHOW_GALLERY_PAGES, DEFAULT_SHOW_GALLERY_PAGES)

    val showComments: Boolean
        get() = getBoolean(KEY_SHOW_COMMENTS, DEFAULT_SHOW_COMMENTS)

    val commentThreshold: Int
        get() = getInt(KEY_COMMENT_THRESHOLD, DEFAULT_COMMENT_THRESHOLD)

    val previewNum: Int
        get() = getInt(KEY_PREVIEW_NUM, DEFAULT_PREVIEW_NUM)

    val previewSize: Int
        get() = dip2px(40 * getInt(KEY_PREVIEW_SIZE, DEFAULT_PREVIEW_SIZE))

    val showTagTranslations: Boolean
        get() = getBoolean(KEY_SHOW_TAG_TRANSLATIONS, DEFAULT_SHOW_TAG_TRANSLATIONS)
    private fun putShowTagTranslations(value: Boolean) {
        putBoolean(KEY_SHOW_TAG_TRANSLATIONS, value)
    }

    val translationsLastUpdate: Long
        get() = getLong(KEY_TRANSLATIONS_LAST_UPDATE, -1)
    fun putTranslationsLastUpdate(value: Long) {
        putLong(KEY_TRANSLATIONS_LAST_UPDATE, value)
    }

    val meteredNetworkWarning: Boolean
        get() = getBoolean(KEY_METERED_NETWORK_WARNING, DEFAULT_METERED_NETWORK_WARNING)

    val requestNews: Boolean
        get() = getBoolean(KEY_REQUEST_NEWS, DEFAULT_REQUEST_NEWS)

    val hideHvEvents: Boolean
        get() = getBoolean(KEY_HIDE_HV_EVENTS, DEFAULT_HIDE_HV_EVENTS)

    val screenRotation: Int
        get() = getIntFromStr(KEY_SCREEN_ROTATION, DEFAULT_SCREEN_ROTATION)
    fun putScreenRotation(value: Int) {
        putIntToStr(KEY_SCREEN_ROTATION, value)
    }

    @GalleryView.LayoutMode
    val readingDirection: Int
        get() = GalleryView.sanitizeLayoutMode(getIntFromStr(KEY_READING_DIRECTION, DEFAULT_READING_DIRECTION))
    fun putReadingDirection(value: Int) {
        putIntToStr(KEY_READING_DIRECTION, value)
    }

    @GalleryView.ScaleMode
    val pageScaling: Int
        get() = GalleryView.sanitizeScaleMode(getIntFromStr(KEY_PAGE_SCALING, DEFAULT_PAGE_SCALING))
    fun putPageScaling(value: Int) {
        putIntToStr(KEY_PAGE_SCALING, value)
    }

    @GalleryView.StartPosition
    val startPosition: Int
        get() = GalleryView.sanitizeStartPosition(getIntFromStr(KEY_START_POSITION, DEFAULT_START_POSITION))
    fun putStartPosition(value: Int) {
        putIntToStr(KEY_START_POSITION, value)
    }

    val readTheme: Int
        get() = getIntFromStr(KEY_READ_THEME, DEFAULT_READ_THEME)
    fun putReadTheme(value: Int) {
        putIntToStr(KEY_READ_THEME, value)
    }

    val keepScreenOn: Boolean
        get() = getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)
    fun putKeepScreenOn(value: Boolean) {
        putBoolean(KEY_KEEP_SCREEN_ON, value)
    }

    val showClock: Boolean
        get() = getBoolean(KEY_SHOW_CLOCK, DEFAULT_SHOW_CLOCK)
    fun putShowClock(value: Boolean) {
        putBoolean(KEY_SHOW_CLOCK, value)
    }

    val showProgress: Boolean
        get() = getBoolean(KEY_SHOW_PROGRESS, DEFAULT_SHOW_PROGRESS)
    fun putShowProgress(value: Boolean) {
        putBoolean(KEY_SHOW_PROGRESS, value)
    }

    val showBattery: Boolean
        get() = getBoolean(KEY_SHOW_BATTERY, DEFAULT_SHOW_BATTERY)
    fun putShowBattery(value: Boolean) {
        putBoolean(KEY_SHOW_BATTERY, value)
    }

    val showPageInterval: Boolean
        get() = getBoolean(KEY_SHOW_PAGE_INTERVAL, DEFAULT_SHOW_PAGE_INTERVAL)
    fun putShowPageInterval(value: Boolean) {
        putBoolean(KEY_SHOW_PAGE_INTERVAL, value)
    }

    val turnPageInterval: Int
        get() = getInt(KEY_TURN_PAGE_INTERVAL, DEFAULT_TURN_PAGE_INTERVAL)
    fun putTurnPageInterval(value: Int) {
        putInt(KEY_TURN_PAGE_INTERVAL, value)
    }

    val volumePage: Boolean
        get() = getBoolean(KEY_VOLUME_PAGE, DEFAULT_VOLUME_PAGE)
    fun putVolumePage(value: Boolean) {
        putBoolean(KEY_VOLUME_PAGE, value)
    }

    val volumePageInterval: Int
        get() = getInt(KEY_VOLUME_PAGE_INTERVAL, DEFAULT_VOLUME_PAGE_INTERVAL)
    fun putVolumePageInterval(value: Int) {
        putInt(KEY_VOLUME_PAGE_INTERVAL, value)
    }

    val reverseVolumePage: Boolean
        get() = getBoolean(KEY_REVERSE_VOLUME_PAGE, DEFAULT_REVERSE_VOLUME_PAGE)
    fun putReverseVolumePage(value: Boolean) {
        putBoolean(KEY_REVERSE_VOLUME_PAGE, value)
    }

    val readingFullscreen: Boolean
        get() = getBoolean(KEY_READING_FULLSCREEN, VALUE_READING_FULLSCREEN)
    fun putReadingFullscreen(value: Boolean) {
        putBoolean(KEY_READING_FULLSCREEN, value)
    }

    val customScreenLightness: Boolean
        get() = getBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, DEFAULT_CUSTOM_SCREEN_LIGHTNESS)
    fun putCustomScreenLightness(value: Boolean) {
        putBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, value)
    }

    val screenLightness: Int
        get() = getInt(KEY_SCREEN_LIGHTNESS, DEFAULT_SCREEN_LIGHTNESS)
    fun putScreenLightness(value: Int) {
        putInt(KEY_SCREEN_LIGHTNESS, value)
    }

    val doublePageMode: Boolean
        get() = getBoolean(KEY_DOUBLE_PAGE_MODE, DEFAULT_DOUBLE_PAGE_MODE)
    fun putDoublePageMode(value: Boolean) {
        putBoolean(KEY_DOUBLE_PAGE_MODE, value)
    }

    val doublePageModeLandscape: Boolean
        get() = getBoolean(KEY_DOUBLE_PAGE_MODE_LANDSCAPE, DEFAULT_DOUBLE_PAGE_MODE_LANDSCAPE)
    fun putDoublePageModeLandscape(value: Boolean) {
        putBoolean(KEY_DOUBLE_PAGE_MODE_LANDSCAPE, value)
    }

    val doublePageOffset: Boolean
        get() = getBoolean(KEY_DOUBLE_PAGE_OFFSET, DEFAULT_DOUBLE_PAGE_OFFSET)
    fun putDoublePageOffset(value: Boolean) {
        putBoolean(KEY_DOUBLE_PAGE_OFFSET, value)
    }

    val doublePageGap: Int
        get() = dip2px(getInt(KEY_DOUBLE_PAGE_GAP, DEFAULT_DOUBLE_PAGE_GAP))
    fun putDoublePageGap(value: Int) {
        putInt(KEY_DOUBLE_PAGE_GAP, value)
    }

    val stripExtraneousAds: Boolean
        get() = getBoolean(KEY_STRIP_EXTRANEOUS_ADS, DEFAULT_STRIP_EXTRANEOUS_ADS)
    fun putStripExtraneousAds(value: Boolean) {
        putBoolean(KEY_STRIP_EXTRANEOUS_ADS, value)
    }

    val readImageLimit: Int
        get() = getInt(KEY_READ_IMAGE_LIMIT, DEFAULT_READ_IMAGE_LIMIT)
    fun putReadImageLimit(value: Int) {
        putInt(KEY_READ_IMAGE_LIMIT, value)
    }

    val layoutEnabled: Boolean
        get() = getBoolean(KEY_LAYOUT_ENABLED, DEFAULT_LAYOUT_ENABLED)
    fun putLayoutEnabled(value: Boolean) {
        putBoolean(KEY_LAYOUT_ENABLED, value)
    }

    val layoutMainPersistentNav: Boolean
        get() = getBoolean(KEY_LAYOUT_MAIN_PERSISTENT_NAV, DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV)
    fun putLayoutMainPersistentNav(value: Boolean) {
        putBoolean(KEY_LAYOUT_MAIN_PERSISTENT_NAV, value)
    }

    val layoutMainPersistentNavVisible: Boolean
        get() = getBoolean(KEY_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE, DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE)
    fun putLayoutMainPersistentNavVisible(value: Boolean) {
        putBoolean(KEY_LAYOUT_MAIN_PERSISTENT_NAV_VISIBLE, value)
    }

    val layoutMainPersistentNavWidth: Int
        get() = getIntFromStr(KEY_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH, DEFAULT_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH).coerceIn(0, 6)
    fun putLayoutMainPersistentNavWidth(value: Int) {
        putIntToStr(KEY_LAYOUT_MAIN_PERSISTENT_NAV_WIDTH, value)
    }

    val layoutSettingsTwoPane: Boolean
        get() = getBoolean(KEY_LAYOUT_SETTINGS_TWO_PANE, DEFAULT_LAYOUT_SETTINGS_TWO_PANE)
    fun putLayoutSettingsTwoPane(value: Boolean) {
        putBoolean(KEY_LAYOUT_SETTINGS_TWO_PANE, value)
    }

    val layoutDetailTwoPane: Boolean
        get() = getBoolean(KEY_LAYOUT_DETAIL_TWO_PANE, DEFAULT_LAYOUT_DETAIL_TWO_PANE)
    fun putLayoutDetailTwoPane(value: Boolean) {
        putBoolean(KEY_LAYOUT_DETAIL_TWO_PANE, value)
    }

    val layoutDetailLeftWidth: Int
        get() = getIntFromStr(KEY_LAYOUT_DETAIL_LEFT_WIDTH, DEFAULT_LAYOUT_DETAIL_LEFT_WIDTH).coerceIn(0, 6)
    fun putLayoutDetailLeftWidth(value: Int) {
        putIntToStr(KEY_LAYOUT_DETAIL_LEFT_WIDTH, value)
    }

    val layoutReaderThumbnailSidebar: Boolean
        get() = getBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR, DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR)
    fun putLayoutReaderThumbnailSidebar(value: Boolean) {
        putBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR, value)
    }

    val layoutReaderThumbnailSidebarWidth: Int
        get() = getIntFromStr(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH, DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH).coerceIn(0, 32)
    fun putLayoutReaderThumbnailSidebarWidth(value: Int) {
        putIntToStr(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_WIDTH, value)
    }

    val layoutReaderThumbnailSidebarVisible: Boolean
        get() = getBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_VISIBLE, DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_VISIBLE)
    fun putLayoutReaderThumbnailSidebarVisible(value: Boolean) {
        putBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_VISIBLE, value)
    }

    val layoutReaderThumbnailSidebarOnRight: Boolean
        get() = getBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT, DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT)
    fun putLayoutReaderThumbnailSidebarOnRight(value: Boolean) {
        putBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_ON_RIGHT, value)
    }

    val layoutReaderThumbnailSidebarToggleAutoHide: Boolean
        get() = getBoolean(
            KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_TOGGLE_AUTO_HIDE,
            DEFAULT_LAYOUT_READER_THUMBNAIL_SIDEBAR_TOGGLE_AUTO_HIDE,
        )
    fun putLayoutReaderThumbnailSidebarToggleAutoHide(value: Boolean) {
        putBoolean(KEY_LAYOUT_READER_THUMBNAIL_SIDEBAR_TOGGLE_AUTO_HIDE, value)
    }

    val downloadLocation: UniFile?
        get() {
            val dir: UniFile?
            val builder = Uri.Builder()
            builder.scheme(getString(KEY_DOWNLOAD_SAVE_SCHEME, null))
            builder.encodedAuthority(getString(KEY_DOWNLOAD_SAVE_AUTHORITY, null))
            builder.encodedPath(getString(KEY_DOWNLOAD_SAVE_PATH, null))
            builder.encodedQuery(getString(KEY_DOWNLOAD_SAVE_QUERY, null))
            builder.encodedFragment(getString(KEY_DOWNLOAD_SAVE_FRAGMENT, null))
            dir = UniFile.fromUri(application, builder.build())
            return dir ?: UniFile.fromFile(AppConfig.getDefaultDownloadDir())
        }
    fun putDownloadLocation(location: UniFile) {
        val uri = location.uri
        putString(KEY_DOWNLOAD_SAVE_SCHEME, uri.scheme)
        putString(KEY_DOWNLOAD_SAVE_AUTHORITY, uri.encodedAuthority)
        putString(KEY_DOWNLOAD_SAVE_PATH, uri.encodedPath)
        putString(KEY_DOWNLOAD_SAVE_QUERY, uri.encodedQuery)
        putString(KEY_DOWNLOAD_SAVE_FRAGMENT, uri.encodedFragment)
    }

    val mediaScan: Boolean
        get() = getBoolean(KEY_MEDIA_SCAN, DEFAULT_MEDIA_SCAN)

    val downloadThreadCount: Int
        get() = getIntFromStr(KEY_MULTI_THREAD_DOWNLOAD, DEFAULT_MULTI_THREAD_DOWNLOAD)

    val downloadDelay: Int
        get() = getIntFromStr(KEY_DOWNLOAD_DELAY, DEFAULT_DOWNLOAD_DELAY)

    val downloadTimeout: Int
        get() = getInt(KEY_DOWNLOAD_TIMEOUT, DEFAULT_DOWNLOAD_TIMEOUT)

    val timeoutSpeed: Int
        get() = getInt(KEY_TIMEOUT_SPEED, DEFAULT_TIMEOUT_SPEED)

    val preloadImage: Int
        get() = getIntFromStr(KEY_PRELOAD_IMAGE, DEFAULT_PRELOAD_IMAGE)

    fun getDownloadOriginImage(mode: Boolean): Boolean = when (getIntFromStr(KEY_DOWNLOAD_ORIGIN_IMAGE, DEFAULT_DOWNLOAD_ORIGIN_IMAGE)) {
        2 -> mode
        1 -> true
        else -> false
    }

    val skipCopyImage: Boolean
        get() = getIntFromStr(KEY_DOWNLOAD_ORIGIN_IMAGE, DEFAULT_DOWNLOAD_ORIGIN_IMAGE) == 2

    val security: String?
        get() = getString(KEY_SECURITY, DEFAULT_SECURITY)
    fun putSecurity(value: String?) {
        putString(KEY_SECURITY, value)
    }

    val enableFingerprint: Boolean
        get() = getBoolean(KEY_ENABLE_FINGERPRINT, DEFAULT_ENABLE_FINGERPRINT)
    fun putEnableFingerprint(value: Boolean) {
        putBoolean(KEY_ENABLE_FINGERPRINT, value)
    }

    val enabledSecurity: Boolean
        get() = getBoolean(KEY_SEC_SECURITY, DEFAULT_SEC_SECURITY)

    val saveParseErrorBody: Boolean
        get() = getBoolean(KEY_SAVE_PARSE_ERROR_BODY, DEFAULT_SAVE_PARSE_ERROR_BODY)

    val saveCrashLog: Boolean
        get() = getBoolean(KEY_SAVE_CRASH_LOG, DEFAULT_SAVE_CRASH_LOG)

    val developerMode: Boolean
        get() = getBoolean(KEY_DEVELOPER_MODE, DEFAULT_DEVELOPER_MODE)
    fun putDeveloperMode(value: Boolean) {
        putBoolean(KEY_DEVELOPER_MODE, value)
    }

    val readCacheSize: Int
        get() = getIntFromStr(KEY_READ_CACHE_SIZE, DEFAULT_READ_CACHE_SIZE)

    val appLanguage: String?
        get() = getString(KEY_APP_LANGUAGE, DEFAULT_APP_LANGUAGE)

    val userAgent: String
        get() = getString(KEY_USER_AGENT, DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT

    fun putUserAgent(value: String?) {
        putString(KEY_USER_AGENT, value)
    }

    val builtInUserAgents: List<String>
        get() = listOf(
            UAPresets.CHROME_PC,
            UAPresets.CHROME_ANDROID,
            UAPresets.FIREFOX_PC,
            UAPresets.SAFARI_PC,
            UAPresets.WEBVIEW_ANDROID,
        )

    val builtInUserAgentNames = listOf(
        "Chrome (PC)",
        "Chrome (Android)",
        "Firefox (PC)",
        "Safari (PC)",
        "WebView (Android)",
    )

    val proxyType: Int
        get() = getInt(KEY_PROXY_TYPE, DEFAULT_PROXY_TYPE)
    fun putProxyType(value: Int) {
        putInt(KEY_PROXY_TYPE, value)
    }

    val proxyIp: String?
        get() = getString(KEY_PROXY_IP, DEFAULT_PROXY_IP)
    fun putProxyIp(value: String?) {
        putString(KEY_PROXY_IP, value)
    }

    val proxyPort: Int
        get() = getInt(KEY_PROXY_PORT, DEFAULT_PROXY_PORT)
    fun putProxyPort(value: Int) {
        putInt(KEY_PROXY_PORT, value)
    }

    val builtInHosts: Boolean
        get() = getBoolean(KEY_BUILT_IN_HOSTS, DEFAULT_BUILT_IN_HOSTS)
    fun putBuiltInHosts(value: Boolean) {
        putBoolean(KEY_BUILT_IN_HOSTS, value)
    }

    val dOH: Boolean
        get() = getBoolean(KEY_DOH, DEFAULT_DOH)
    fun putDoH(value: Boolean) {
        putBoolean(KEY_DOH, value)
    }

    val doHServer: String?
        get() = getString(KEY_DOH_SERVER, DEFAULT_DOH_SERVER)
    fun putDoHServer(value: String?) {
        putString(KEY_DOH_SERVER, value)
    }

    val dF: Boolean
        get() = getBoolean(KEY_DOMAIN_FRONTING, DEFAULT_DOMAIN_FRONTING)
    fun putDoF(value: Boolean) {
        putBoolean(KEY_DOMAIN_FRONTING, value)
    }

    val bypassVpn: Boolean
        get() = getBoolean(KEY_BYPASS_VPN, DEFAULT_BYPASS_VPN)

    val appLinkVerifyTip: Boolean
        get() = getBoolean(KEY_APP_LINK_VERIFY_TIP, DEFAULT_APP_LINK_VERIFY_TIP)
    fun putAppLinkVerifyTip(value: Boolean) {
        putBoolean(KEY_APP_LINK_VERIFY_TIP, value)
    }

    var favCat: Array<String>
        get() = arrayOf(
            sSettingsPre.getString(KEY_FAV_CAT_0, DEFAULT_FAV_CAT_0)!!,
            sSettingsPre.getString(KEY_FAV_CAT_1, DEFAULT_FAV_CAT_1)!!,
            sSettingsPre.getString(KEY_FAV_CAT_2, DEFAULT_FAV_CAT_2)!!,
            sSettingsPre.getString(KEY_FAV_CAT_3, DEFAULT_FAV_CAT_3)!!,
            sSettingsPre.getString(KEY_FAV_CAT_4, DEFAULT_FAV_CAT_4)!!,
            sSettingsPre.getString(KEY_FAV_CAT_5, DEFAULT_FAV_CAT_5)!!,
            sSettingsPre.getString(KEY_FAV_CAT_6, DEFAULT_FAV_CAT_6)!!,
            sSettingsPre.getString(KEY_FAV_CAT_7, DEFAULT_FAV_CAT_7)!!,
            sSettingsPre.getString(KEY_FAV_CAT_8, DEFAULT_FAV_CAT_8)!!,
            sSettingsPre.getString(KEY_FAV_CAT_9, DEFAULT_FAV_CAT_9)!!,
        )
        set(value) {
            check(value.size == 10)
            sSettingsPre.edit {
                putString(KEY_FAV_CAT_0, value[0])
                    .putString(KEY_FAV_CAT_1, value[1])
                    .putString(KEY_FAV_CAT_2, value[2])
                    .putString(KEY_FAV_CAT_3, value[3])
                    .putString(KEY_FAV_CAT_4, value[4])
                    .putString(KEY_FAV_CAT_5, value[5])
                    .putString(KEY_FAV_CAT_6, value[6])
                    .putString(KEY_FAV_CAT_7, value[7])
                    .putString(KEY_FAV_CAT_8, value[8])
                    .putString(KEY_FAV_CAT_9, value[9])
            }
        }

    var favCount: IntArray
        get() = intArrayOf(
            sSettingsPre.getInt(KEY_FAV_COUNT_0, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_1, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_2, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_3, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_4, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_5, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_6, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_7, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_8, DEFAULT_FAV_COUNT),
            sSettingsPre.getInt(KEY_FAV_COUNT_9, DEFAULT_FAV_COUNT),
        )
        set(count) {
            check(count.size == 10)
            sSettingsPre.edit {
                putInt(KEY_FAV_COUNT_0, count[0])
                    .putInt(KEY_FAV_COUNT_1, count[1])
                    .putInt(KEY_FAV_COUNT_2, count[2])
                    .putInt(KEY_FAV_COUNT_3, count[3])
                    .putInt(KEY_FAV_COUNT_4, count[4])
                    .putInt(KEY_FAV_COUNT_5, count[5])
                    .putInt(KEY_FAV_COUNT_6, count[6])
                    .putInt(KEY_FAV_COUNT_7, count[7])
                    .putInt(KEY_FAV_COUNT_8, count[8])
                    .putInt(KEY_FAV_COUNT_9, count[9])
            }
        }

    val favLocalCount: Int
        get() = sSettingsPre.getInt(KEY_FAV_LOCAL, DEFAULT_FAV_COUNT)
    fun putFavLocalCount(count: Int) {
        sSettingsPre.edit { putInt(KEY_FAV_LOCAL, count) }
    }

    val favCloudCount: Int
        get() = sSettingsPre.getInt(KEY_FAV_CLOUD, DEFAULT_FAV_COUNT)
    fun putFavCloudCount(count: Int) {
        sSettingsPre.edit { putInt(KEY_FAV_CLOUD, count) }
    }

    val recentFavCat: Int
        get() = getInt(KEY_RECENT_FAV_CAT, DEFAULT_RECENT_FAV_CAT)
    fun putRecentFavCat(value: Int) {
        putInt(KEY_RECENT_FAV_CAT, value)
    }

    var savedCategory: Int
        get() = getInt(KEY_SAVED_CATEGORY, EhUtils.ALL_CATEGORY)
        set(value) = putInt(KEY_SAVED_CATEGORY, value)

    val defaultFavSlot: Int
        get() = getInt(KEY_DEFAULT_FAV_SLOT, DEFAULT_DEFAULT_FAV_SLOT)
    fun putDefaultFavSlot(value: Int) {
        putInt(KEY_DEFAULT_FAV_SLOT, value)
    }

    val neverAddFavNotes: Boolean
        get() = getBoolean(KEY_NEVER_ADD_FAV_NOTES, DEFAULT_NEVER_ADD_FAV_NOTES)
    fun putNeverAddFavNotes(value: Boolean) {
        putBoolean(KEY_NEVER_ADD_FAV_NOTES, value)
    }

    val guideGallery: Boolean
        get() = getBoolean(KEY_GUIDE_GALLERY, DEFAULT_GUIDE_GALLERY)
    fun putGuideGallery(value: Boolean) {
        putBoolean(KEY_GUIDE_GALLERY, value)
    }

    val selectSite: Boolean
        get() = getBoolean(KEY_SELECT_SITE, DEFAULT_SELECT_SITE)
    fun putSelectSite(value: Boolean) {
        putBoolean(KEY_SELECT_SITE, value)
    }

    val needSignIn: Boolean
        get() = getBoolean(KEY_NEED_SIGN_IN, DEFAULT_NEED_SIGN_IN)
    fun putNeedSignIn(value: Boolean) {
        putBoolean(KEY_NEED_SIGN_IN, value)
    }

    val displayName: String?
        get() = getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME)
    fun putDisplayName(value: String?) {
        putString(KEY_DISPLAY_NAME, value)
    }

    val avatar: String?
        get() = getString(KEY_AVATAR, DEFAULT_AVATAR)
    fun putAvatar(value: String?) {
        putString(KEY_AVATAR, value)
    }

    val qSSaveProgress: Boolean
        get() = getBoolean(KEY_QS_SAVE_PROGRESS, DEFAULT_QS_SAVE_PROGRESS)
    fun putQSSaveProgress(value: Boolean) {
        putBoolean(KEY_QS_SAVE_PROGRESS, value)
    }

    val hasDefaultDownloadLabel: Boolean
        get() = getBoolean(KEY_HAS_DEFAULT_DOWNLOAD_LABEL, DEFAULT_HAS_DOWNLOAD_LABEL)
    fun putHasDefaultDownloadLabel(has: Boolean) {
        putBoolean(KEY_HAS_DEFAULT_DOWNLOAD_LABEL, has)
    }

    val defaultDownloadLabel: String?
        get() = getString(KEY_DEFAULT_DOWNLOAD_LABEL, DEFAULT_DOWNLOAD_LABEL)
    fun putDefaultDownloadLabel(value: String?) {
        putString(KEY_DEFAULT_DOWNLOAD_LABEL, value)
    }

    val recentDownloadLabel: String?
        get() = getString(KEY_RECENT_DOWNLOAD_LABEL, DEFAULT_RECENT_DOWNLOAD_LABEL)
    fun putRecentDownloadLabel(value: String?) {
        putString(KEY_RECENT_DOWNLOAD_LABEL, value)
    }

    val defaultSortingMethod: Int
        get() = getInt(KEY_DEFAULT_SORTING_METHOD, DEFAULT_SORTING_METHOD)
    fun putDefaultSortingMethod(value: Int) {
        putInt(KEY_DEFAULT_SORTING_METHOD, value)
    }

    val defaultTopList: String?
        get() = getString(KEY_DEFAULT_TOP_LIST, DEFAULT_TOP_LIST)
    fun putDefaultTopList(value: String?) {
        putString(KEY_DEFAULT_TOP_LIST, value)
    }

    val removeImageFiles: Boolean
        get() = getBoolean(KEY_REMOVE_IMAGE_FILES, DEFAULT_REMOVE_IMAGE_FILES)
    fun putRemoveImageFiles(value: Boolean) {
        putBoolean(KEY_REMOVE_IMAGE_FILES, value)
    }

    val clipboardTextHashCode: Int
        get() = getInt(KEY_CLIPBOARD_TEXT_HASH_CODE, DEFAULT_CLIPBOARD_TEXT_HASH_CODE)
    fun putClipboardTextHashCode(value: Int) {
        putInt(KEY_CLIPBOARD_TEXT_HASH_CODE, value)
    }

    val archivePasswds: Set<String>?
        get() = getStringSet(KEY_ARCHIVE_PASSWDS)
    fun putPasswdToArchivePasswds(value: String) {
        putStringToStringSet(KEY_ARCHIVE_PASSWDS, value)
    }

    val notificationRequired: Boolean
        get() = getBoolean(KEY_NOTIFICATION_REQUIRED, false)
    fun putNotificationRequired() {
        putBoolean(KEY_NOTIFICATION_REQUIRED, true)
    }
}
