package com.doomstop.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("doomstop", Context.MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply { text = "Doomstop"; textSize = 26f }
        val label = TextView(this).apply { text = "Daily Instagram limit (minutes):" }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("limit", 30).toString())
        }
        val b1 = Button(this).apply {
            text = "1. Grant Usage Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        val b2 = Button(this).apply {
            text = "2. Allow display over apps"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + this@MainActivity.packageName)
                    )
                )
            }
        }
        val b3 = Button(this).apply {
            text = "3. Save and start"
            setOnClickListener {
                val m = input.text.toString().toIntOrNull() ?: 30
                prefs.edit().putInt("limit", m).apply()
                if (Build.VERSION.SDK_INT >= 33) {
                    this@MainActivity.requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
                    )
                }
                this@MainActivity.startForegroundService(
                    Intent(this@MainActivity, MonitorService::class.java)
                )
                Toast.makeText(
                    this@MainActivity, "Limit set to $m min", Toast.LENGTH_SHORT
                ).show()
            }
        }
        root.addView(title)
        root.addView(label)
        root.addView(input)
        root.addView(b1)
        root.addView(b2)
        root.addView(b3)
        setContentView(root)
    }
}