package com.doomstop.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocusStore {
    val APPS = linkedMapOf(
        "Instagram" to "com.instagram.android",
        "YouTube" to "com.google.android.youtube",
        "Facebook" to "com.facebook.katana",
        "Snapchat" to "com.snapchat.android"
    )
    val SUBJECTS = listOf("Study", "Coding", "Reading", "Revision", "Work")

    fun p(c: Context): SharedPreferences =
        c.getSharedPreferences("focus", Context.MODE_PRIVATE)

    private fun day(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun blocked(c: Context): Set<String> =
        p(c).getStringSet("blocked", APPS.values.toSet()) ?: emptySet()

    private fun logSession(c: Context, subject: String, minutes: Int) {
        val old = p(c).getString("log", "") ?: ""
        p(c).edit().putString("log", old + day() + "|" + subject + "|" + minutes + "\n").apply()
    }

    fun todayMinutes(c: Context): Int {
        val log = p(c).getString("log", "") ?: ""
        val d = day()
        var sum = 0
        for (line in log.lines()) {
            val parts = line.split("|")
            if (parts.size == 3 && parts[0] == d) sum += parts[2].toIntOrNull() ?: 0
        }
        return sum
    }

    fun endSession(c: Context) {
        val pr = p(c)
        if (!pr.getBoolean("running", false)) return
        val total = pr.getLong("totalMs", 0L)
        val end = pr.getLong("endAt", 0L)
        val elapsed = (total - (end - System.currentTimeMillis())).coerceIn(0L, total)
        val mins = (elapsed / 60000L).toInt()
        if (mins >= 1) logSession(c, pr.getString("subject", "Study") ?: "Study", mins)
        pr.edit().putBoolean("running", false).apply()
    }
}