package com.doomstop.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Calendar

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("doomstop", "Doomstop", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, "doomstop")
            .setContentTitle("Doomstop is running")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun check() {
        val p = Store.prefs(this)
        val limitMs = p.getInt("limit", 30) * 60_000L
        val scrollLimit = p.getInt("scroll_limit", 50)
        val apps = Store.enabled(this)
        val usage = usageFor(apps)

        var blockedApp: String? = null
        var reason = ""
        for (pkg in apps) {
            val (used, fg) = usage[pkg] ?: Pair(0L, false)
            if (!fg) continue
            if (used >= limitMs) {
                blockedApp = Apps.all[pkg]
                reason = "You used up your ${limitMs / 60_000} minutes for today."
                break
            }
            if (scrollLimit > 0 && Store.scrolls(this, pkg) >= scrollLimit) {
                blockedApp = Apps.all[pkg]
                reason = "You scrolled through $scrollLimit videos today."
                break
            }
        }
        if (blockedApp != null) showOverlay(blockedApp, reason) else hideOverlay()
    }

    private fun usageFor(pkgs: Set<String>): Map<String, Pair<Long, Boolean>> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(cal.timeInMillis, now)
        val e = UsageEvents.Event()
        val total = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            if (pkg !in pkgs) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (!resumedAt.containsKey(pkg)) resumedAt[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val r = resumedAt.remove(pkg)
                    if (r != null) total[pkg] = (total[pkg] ?: 0L) + (e.timeStamp - r)
                }
            }
        }
        val out = HashMap<String, Pair<Long, Boolean>>()
        for (pkg in pkgs) {
            val r = resumedAt[pkg]
            var t = total[pkg] ?: 0L
            if (r != null) t += now - r
            out[pkg] = Pair(t, r != null)
        }
        return out
    }

    private fun showOverlay(appName: String, reason: String) {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dp = resources.displayMetrics.density

        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
            setBackgroundColor(Color.parseColor("#F2111827"))
            addView(TextView(context).apply {
                text = "Doomstop"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 16f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "$appName is locked for now"
                setTextColor(Color.WHITE)
                textSize = 26f
                gravity = Gravity.CENTER
                setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
            })
            addView(TextView(context).apply {
                text = reason + "\nGo do something real. You can come back tomorrow."
                setTextColor(Color.parseColor("#9CA3AF"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (28 * dp).toInt())
            })
            addView(Button(context).apply {
                text = "Go home"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EF4444"))
                    cornerRadius = 28 * dp
                }
                setOnClickListener { goHome() }
            })
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
        overlay = v
    }

    private fun hideOverlay() {
        overlay?.let {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            overlay = null
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}