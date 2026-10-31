package com.doomstop.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    val APPS = linkedMapOf(
        "Instagram" to "com.instagram.android",
        "YouTube" to "com.google.android.youtube",
        "Facebook" to "com.facebook.katana",
        "Snapchat" to "com.snapchat.android"
    )

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("doomstop", Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun selectedApps(c: Context): Set<String> =
        prefs(c).getStringSet("apps", setOf("com.instagram.android")) ?: emptySet()

    fun scrolls(c: Context): Int {
        val p = prefs(c)
        return if (p.getString("scroll_day", "") == today()) p.getInt("scrolls", 0) else 0
    }

    fun addScroll(c: Context) {
        val n = scrolls(c) + 1
        prefs(c).edit().putString("scroll_day", today()).putInt("scrolls", n).apply()
    }
}