package com.doomstop.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object Usage {
    fun todayMs(c: Context): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(cal.timeInMillis, now)
        val e = UsageEvents.Event()
        val pkgs = Store.APPS.values.toSet()
        val resumed = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            if (pkg !in pkgs) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (!resumed.containsKey(pkg)) resumed[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val s = resumed.remove(pkg)
                    if (s != null) total[pkg] = (total[pkg] ?: 0L) + (e.timeStamp - s)
                }
            }
        }
        for ((pkg, s) in resumed) total[pkg] = (total[pkg] ?: 0L) + (now - s)
        return total
    }
}