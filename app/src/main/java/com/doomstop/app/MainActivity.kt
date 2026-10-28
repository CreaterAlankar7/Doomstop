package com.doomstop.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var stats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Store.prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(t: String) = TextView(this).apply {
            text = t
            setPadding(0, pad, 0, 0)
        }
        fun number(v: Int) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(v.toString())
        }
        fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        val title = TextView(this).apply { text = "Doomstop"; textSize = 28f }
        val minutes = number(prefs.getInt("limit", 30))
        val scrolls = number(prefs.getInt("scroll_limit", 50))
        val reelsOnly = CheckBox(this).apply {
            text = "Count reels/shorts only (not feed scrolls)"
            isChecked = prefs.getBoolean("reels_only", true)
        }
        val boxes = Apps.all.map { (pkg, name) ->
            pkg to CheckBox(this).apply {
                text = name
                isChecked = pkg in Store.enabled(this@MainActivity)
            }
        }
        stats = TextView(this).apply { setPadding(0, pad, 0, pad) }

        root.addView(title)
        root.addView(label("Daily time limit per app (minutes):"))
        root.addView(minutes)
        root.addView(label("Daily scroll limit per app (0 = off):"))
        root.addView(scrolls)
        root.addView(reelsOnly)
        root.addView(label("Apps to block:"))
        boxes.forEach { root.addView(it.second) }

        root.addView(btn("1. Grant Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(btn("2. Allow display over apps") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })
        root.addView(btn("3. Turn on Accessibility (for scroll count)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(btn("4. Save and start") {
            val sel = boxes.filter { it.second.isChecked }.map { it.first }.toSet()
            prefs.edit()
                .putInt("limit", minutes.text.toString().toIntOrNull() ?: 30)
                .putInt("scroll_limit", scrolls.text.toString().toIntOrNull() ?: 50)
                .putBoolean("reels_only", reelsOnly.isChecked)
                .putStringSet("apps", sel)
                .putBoolean("running", true)
                .apply()
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(Intent(this, MonitorService::class.java))
            Toast.makeText(this, "Saved. Doomstop is running.", Toast.LENGTH_SHORT).show()
        })
        root.addView(stats)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        stats.text = "Today's scrolls counted:\n" +
            Apps.all.map { (p, n) -> "$n: ${Store.scrolls(this, p)}" }.joinToString("\n")
    }
}