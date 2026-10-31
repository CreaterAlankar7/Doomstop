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

    private fun rollDay(c: Context) {
        val p = prefs(c)
        if (p.getString("scroll_day", "") != today()) {
            val ed = p.edit().putString("scroll_day", today())
            for (pkg in APPS.values) ed.putInt("s_$pkg", 0)
            ed.apply()
        }
    }

    fun scrollsFor(c: Context, pkg: String): Int {
        val p = prefs(c)
        return if (p.getString("scroll_day", "") == today()) p.getInt("s_$pkg", 0) else 0
    }

    fun scrolls(c: Context): Int {
        var total = 0
        for (pkg in selectedApps(c)) total += scrollsFor(c, pkg)
        return total
    }

    fun addScroll(c: Context, pkg: String) {
        rollDay(c)
        val p = prefs(c)
        p.edit().putInt("s_$pkg", p.getInt("s_$pkg", 0) + 1).apply()
    }
}
