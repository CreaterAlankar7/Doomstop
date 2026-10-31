package com.doomstop.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatsActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var body: TextView

    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Today's usage"
        val pad = (16 * resources.displayMetrics.density).toInt()
        body = TextView(this).apply {
            textSize = 18f
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(body) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d min %02d sec".format(s / 60, s % 60)
    }

    private fun render() {
        val usage = Usage.todayMs(this)
        val selected = Store.selectedApps(this)
        val on = Store.prefs(this).getBoolean("enabled", false)
        val sb = StringBuilder()
        sb.append(if (on) "Status: RUNNING\n" else "Status: STOPPED\n")
        sb.append("Refreshes every 2 seconds\n\n")
        var totalMs = 0L
        var totalScrolls = 0
        for ((name, pkg) in Store.APPS) {
            val ms = usage[pkg] ?: 0L
            val sc = Store.scrollsFor(this, pkg)
            totalMs += ms
            totalScrolls += sc
            sb.append(name)
            if (pkg !in selected) sb.append(" (not blocked)")
            sb.append("\n  Time: ").append(fmt(ms))
            sb.append("\n  Scrolls: ").append(sc).append("\n\n")
        }
        sb.append("TOTAL\n  Time: ").append(fmt(totalMs))
        sb.append("\n  Scrolls: ").append(totalScrolls)
        body.text = sb.toString()
    }
}