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
import android.graphics.Typeface
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
import java.util.Locale

class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var overlayText: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            val p = FocusStore.p(this@FocusService)
            if (!p.getBoolean("running", false)) {
                hideOverlay()
                stopSelf()
                return
            }
            val left = p.getLong("endAt", 0L) - System.currentTimeMillis()
            if (left <= 0L) {
                finishSession()
                return
            }
            val fg = foregroundPackage()
            if (fg != null && fg in FocusStore.blocked(this@FocusService)) {
                showOverlay(left)
            } else {
                hideOverlay()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("focus", "Focus session", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("focus_done", "Focus finished", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val n = NotificationCompat.Builder(this, "focus")
            .setContentTitle("Focus session running")
            .setContentText("Distracting apps are blocked")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(3, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun foregroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 60 * 60 * 1000L, now)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                pkg = e.packageName
            } else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED && e.packageName == pkg) {
                pkg = null
            }
        }
        return pkg
    }

    private fun showOverlay(left: Long) {
        val s = (left + 999) / 1000
        val t = String.format(Locale.US, "%02d:%02d left", s / 60, s % 60)
        if (overlay != null) {
            overlayText?.text = t
            return
        }
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val pad = (24 * resources.displayMetrics.density).toInt()
        val timeView = TextView(this).apply {
            text = t
            setTextColor(Color.parseColor("#8EE05A"))
            textSize = 40f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#F2050F0B"))
            addView(TextView(context).apply {
                text = "Stay focused"
                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "This app is blocked during your focus session."
                setTextColor(Color.parseColor("#9DB8AA"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, pad / 2, 0, pad / 2)
            })
            addView(timeView)
            addView(Button(context).apply {
                text = "Back to focus"
                setOnClickListener { backToFocus() }
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
        overlayText = timeView
    }

    private fun hideOverlay() {
        overlay?.let {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (e: Exception) {
            }
        }
        overlay = null
        overlayText = null
    }

    private fun backToFocus() {
        startActivity(
            Intent(this, FocusActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun finishSession() {
        val subj = FocusStore.p(this).getString("subject", "Study") ?: "Study"
        FocusStore.endSession(this)
        hideOverlay()
        val n = NotificationCompat.Builder(this, "focus_done")
            .setContentTitle("Focus session complete")
            .setContentText("Nice work on $subj. Take a break.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(4, n)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}