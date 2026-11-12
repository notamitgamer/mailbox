package dev.amit.mail.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("mailbox", Context.MODE_PRIVATE)

    /** "system", "light" or "dark". */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    /** Material You colours from the wallpaper (Android 12+). Off by default: the graphite and teal palette. */
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic", false)
        set(v) = sp.edit().putBoolean("dynamic", v).apply()

    var aliases: List<String>
        get() = (sp.getString("aliases", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("aliases", v.joinToString("\n")).apply()

    /** Recent searches, newest first (shown as suggestions in the search view). */
    var recentSearches: List<String>
        get() = (sp.getString("recent", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recent", v.take(8).joinToString("\n")).apply()

    /** An FCM token that could not be sent to the server yet (not signed in, or offline). */
    var pendingFcm: String?
        get() = sp.getString("pendingFcm", null)
        set(v) = sp.edit().putString("pendingFcm", v).apply()
}
