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
    private val IG = "com.instagram.android"
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
            .setContentTitle("Doomstop is watching Instagram")
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
        val limitMs = getSharedPreferences("doomstop", Context.MODE_PRIVATE)
            .getInt("limit", 30) * 60_000L
        val (used, inForeground) = instagramUsage()
        if (inForeground && used >= limitMs) showOverlay() else hideOverlay()
    }

    private fun instagramUsage(): Pair<Long, Boolean> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(cal.timeInMillis, now)
        val e = UsageEvents.Event()
        var total = 0L
        var resumedAt = -1L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != IG) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (resumedAt < 0) resumedAt = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED ->
                    if (resumedAt >= 0) {
                        total += e.timeStamp - resumedAt
                        resumedAt = -1
                    }
            }
        }
        val fg = resumedAt >= 0
        if (fg) total += now - resumedAt
        return Pair(total, fg)
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2000000"))
            addView(TextView(context).apply {
                text = "Time's up.\nYou reached your Instagram limit for today."
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
            })
            addView(Button(context).apply {
                text = "Go home"
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