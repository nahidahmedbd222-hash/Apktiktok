package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_DOWNLOAD_LOCATION_TYPE = "download_location_type"
        const val KEY_DARK_MODE = "dark_mode_theme" // "auto", "light", "dark"
        const val KEY_CACHE_ENABLED = "cache_enabled"
        const val KEY_DESKTOP_MODE = "desktop_mode"

        // Location types
        const val VAL_LOC_PUBLIC_DOWNLOADS = "PUBLIC_DOWNLOADS"
        const val VAL_LOC_PRIVATE_INTERNAL = "PRIVATE_INTERNAL"
        const val VAL_LOC_PRIVATE_EXTERNAL = "PRIVATE_EXTERNAL"
    }

    var downloadLocationType: String
        get() = prefs.getString(KEY_DOWNLOAD_LOCATION_TYPE, VAL_LOC_PUBLIC_DOWNLOADS) ?: VAL_LOC_PUBLIC_DOWNLOADS
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_LOCATION_TYPE, value).apply()

    var darkModeTheme: String
        get() = prefs.getString(KEY_DARK_MODE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_DARK_MODE, value).apply()

    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()

    var desktopMode: Boolean
        get() = prefs.getString(KEY_DESKTOP_MODE, "false").toBoolean() // Keep consistent
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()
}
