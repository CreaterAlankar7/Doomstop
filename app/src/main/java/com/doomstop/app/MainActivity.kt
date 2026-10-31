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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun askCode(onOk: () -> Unit) {
        val secret = Store.prefs(this).getString("otp_secret", "") ?: ""
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "6-digit code"
        }
        AlertDialog.Builder(this)
            .setTitle("Enter OTP")
            .setMessage("Open your authenticator app and type the current code.")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (Totp.verify(secret, input.text.toString())) {
                    onOk()
                } else {
                    Toast.makeText(this, "Wrong code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Store.prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        fun label(t: String) = TextView(this).apply {
            text = t
            setPadding(0, dp(14), 0, 0)
        }

        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        status = TextView(this).apply { textSize = 16f }
        val title = TextView(this).apply { text = "Doomstop"; textSize = 28f }

        val timeInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("limit", 30).toString())
        }
        val scrollInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("scroll_limit", 0).toString())
        }

        val selected = Store.selectedApps(this)
        val boxes = Store.APPS.map { (name, pkg) ->
            CheckBox(this).apply {
                text = name
                isChecked = pkg in selected
            } to pkg
        }
        val reels = CheckBox(this).apply {
            text = "Reels only (experimental)"
            isChecked = prefs.getBoolean("reels_only", false)
        }

        root.addView(title)
        root.addView(status)
        root.addView(button("Today's usage and scrolls") {
            startActivity(Intent(this, StatsActivity::class.java))
        })
        root.addView(label("Daily time limit in minutes (0 = off)"))
        root.addView(timeInput)
        root.addView(label("Daily scroll limit (0 = off)"))
        root.addView(scrollInput)
        root.addView(label("Apps to block"))
        boxes.forEach { root.addView(it.first) }
        root.addView(reels)

        root.addView(label("Permissions (do once)"))
        root.addView(button("1. Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(button("2. Display over apps") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
        })
        root.addView(button("3. Accessibility (scroll counter)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(label("Control"))
        root.addView(button("4. Save and start") {
            val minutes = timeInput.text.toString().toIntOrNull() ?: 0
            val scrolls = scrollInput.text.toString().toIntOrNull() ?: 0
            val apps = boxes.filter { it.first.isChecked }.map { it.second }.toSet()
            if (apps.isEmpty()) {
                Toast.makeText(this, "Choose at least one app", Toast.LENGTH_SHORT).show()
                return@button
            }
            val changed = minutes != prefs.getInt("limit", 30) ||
                scrolls != prefs.getInt("scroll_limit", 0) ||
                apps != Store.selectedApps(this) ||
                reels.isChecked != prefs.getBoolean("reels_only", false)
            val save = {
                prefs.edit()
                    .putInt("limit", minutes)
                    .putInt("scroll_limit", scrolls)
                    .putStringSet("apps", apps)
                    .putBoolean("reels_only", reels.isChecked)
                    .putBoolean("enabled", true)
                    .apply()
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
                startForegroundService(Intent(this, MonitorService::class.java))
                refresh()
            }
            val secret = prefs.getString("otp_secret", "") ?: ""
            if (secret.isNotEmpty() && changed) askCode(save) else save()
        })
        root.addView(button("Stop") {
            val secret = prefs.getString("otp_secret", "") ?: ""
            val doStop = {
                prefs.edit().putBoolean("enabled", false).apply()
                stopService(Intent(this, MonitorService::class.java))
                refresh()
            }
            if (secret.isEmpty()) doStop() else askCode(doStop)
        })

        root.addView(label("OTP protection"))
        root.addView(button("OTP for Stop (set up / remove)") {
            val secret = prefs.getString("otp_secret", "") ?: ""
            if (secret.isEmpty()) {
                val s = Totp.newSecret()
                prefs.edit().putString("otp_secret", s).apply()
                val tv = TextView(this).apply {
                    text = s
                    textSize = 22f
                    setTextIsSelectable(true)
                    setPadding(dp(24), dp(16), dp(24), dp(8))
                }
                AlertDialog.Builder(this)
                    .setTitle("Your OTP key")
                    .setMessage(
                        "In an authenticator app on ANOTHER device, choose " +
                            "\"Enter a setup key\" (time based) and type this key. " +
                            "It will NOT be shown again."
                    )
                    .setView(tv)
                    .setCancelable(false)
                    .setPositiveButton("I added it") { _, _ -> refresh() }
                    .show()
            } else {
                askCode {
                    prefs.edit().remove("otp_secret").apply()
                    Toast.makeText(this, "OTP removed", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val p = Store.prefs(this)
        val on = p.getBoolean("enabled", false)
        val otp = !p.getString("otp_secret", "").isNullOrEmpty()
        status.text = (if (on) "Status: RUNNING" else "Status: STOPPED") +
            "\nScrolls today: " + Store.scrolls(this) +
            "\nOTP for Stop: " + (if (otp) "ON" else "OFF")
    }
}
