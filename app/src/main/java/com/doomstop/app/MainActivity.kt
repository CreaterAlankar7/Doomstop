package com.doomstop.app

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val green = Color.parseColor("#8EE05A")
    private val red = Color.parseColor("#FF8A80")
    private val dim = Color.parseColor("#9DB8AA")
    private val cardColor = Color.parseColor("#12261D")
    private val border = Color.parseColor("#2C5A43")
    private val darkText = Color.parseColor("#06210F")
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private lateinit var dot: View
    private lateinit var statusText: TextView
    private lateinit var scrollText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun rounded(fill: Int, r: Int, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            cornerRadius = dp(r).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = rounded(cardColor, 20, Color.parseColor("#1F4A35"))
    }

    private fun pill(t: String, fill: Int, txt: Int, stroke: Int = Color.TRANSPARENT) =
        Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(txt)
            background = rounded(fill, 28, stroke)
            stateListAnimator = null
        }

    private fun field(value: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value)
        setTextColor(Color.WHITE)
        textSize = 18f
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(Color.parseColor("#0C1F17"), 14, border)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#050F0B")
        window.navigationBarColor = Color.parseColor("#0F5A3C")
        val prefs = Store.prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }

        // Header
        val back = text("‹  Block settings", 26f, Color.WHITE, true)
        back.setOnClickListener { finish() }
        root.addView(back)

        // Status card
        val status = card()
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dot = View(this)
        statusText = text("", 18f, green, true)
        statusRow.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply {
            setMargins(0, 0, dp(10), 0)
        })
        statusRow.addView(statusText)
        scrollText = text("", 14f, dim)
        status.addView(statusRow)
        status.addView(scrollText, lp(WRAP, WRAP, 6))
        root.addView(status, lp(MATCH, WRAP, 16))

        // Limits card
        val limits = card()
        limits.addView(text("Limits", 16f, green, true))
        limits.addView(text("Daily time limit (minutes, 0 = off)", 13f, dim), lp(WRAP, WRAP, 10))
        val timeInput = field(prefs.getInt("limit", 30).toString())
        limits.addView(timeInput, lp(MATCH, WRAP, 6))
        limits.addView(text("Daily scroll limit (0 = off)", 13f, dim), lp(WRAP, WRAP, 12))
        val scrollInput = field(prefs.getInt("scroll_limit", 0).toString())
        limits.addView(scrollInput, lp(MATCH, WRAP, 6))
        root.addView(limits, lp(MATCH, WRAP, 14))

        // Apps card
        val appsCard = card()
        appsCard.addView(text("Apps to block", 16f, green, true))
        val selected = Store.selectedApps(this)
        val boxes = Store.APPS.entries.map { e ->
            CheckBox(this).apply {
                text = e.key
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(green)
                isChecked = e.value in selected
            } to e.value
        }
        for (b in boxes) appsCard.addView(b.first, lp(WRAP, WRAP, 4))
        val reels = CheckBox(this).apply {
            text = "Reels only (experimental)"
            setTextColor(dim)
            buttonTintList = ColorStateList.valueOf(green)
            isChecked = prefs.getBoolean("reels_only", false)
        }
        appsCard.addView(reels, lp(WRAP, WRAP, 8))
        root.addView(appsCard, lp(MATCH, WRAP, 14))

        // Permissions card
        val perms = card()
        perms.addView(text("Permissions (do once)", 16f, green, true))
        val p1 = pill("1   Usage Access", Color.TRANSPARENT, Color.WHITE, border)
        p1.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        val p2 = pill("2   Display over apps", Color.TRANSPARENT, Color.WHITE, border)
        p2.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
        }
        val p3 = pill("3   Accessibility (scroll counter)", Color.TRANSPARENT, Color.WHITE, border)
        p3.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        perms.addView(p1, lp(MATCH, dp(48), 10))
        perms.addView(p2, lp(MATCH, dp(48), 8))
        perms.addView(p3, lp(MATCH, dp(48), 8))
        root.addView(perms, lp(MATCH, WRAP, 14))

        // Start / Stop
        val startBtn = pill("Save and start", green, darkText)
        startBtn.textSize = 17f
        startBtn.typeface = Typeface.DEFAULT_BOLD
        val stopBtn = pill("Stop", Color.parseColor("#3A1E1E"), red)
        stopBtn.textSize = 17f
        stopBtn.typeface = Typeface.DEFAULT_BOLD

        startBtn.setOnClickListener {
            val minutes = timeInput.text.toString().toIntOrNull() ?: 0
            val scrolls = scrollInput.text.toString().toIntOrNull() ?: 0
            val apps = boxes.filter { it.first.isChecked }.map { it.second }.toSet()
            if (apps.isEmpty()) {
                Toast.makeText(this, "Choose at least one app", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
        stopBtn.setOnClickListener {
            prefs.edit().putBoolean("enabled", false).apply()
            stopService(Intent(this, MonitorService::class.java))
            refresh()
        }

        val ctl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ctl.addView(startBtn, LinearLayout.LayoutParams(0, dp(54), 2f).apply {
            setMargins(0, 0, dp(8), 0)
        })
        ctl.addView(stopBtn, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(ctl, lp(MATCH, WRAP, 18))

        val statsBtn = pill("View stats", Color.TRANSPARENT, green, border)
        statsBtn.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        root.addView(statsBtn, lp(MATCH, dp(48), 10))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#050F0B"),
                Color.parseColor("#0A2A1D"),
                Color.parseColor("#0F5A3C")
            )
        )
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            background = bg
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = Store.prefs(this).getBoolean("enabled", false)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) green else red)
        }
        statusText.text = if (on) "Blocker is RUNNING" else "Blocker is STOPPED"
        statusText.setTextColor(if (on) green else red)
        scrollText.text = "Scrolls today: " + Store.scrolls(this)
    }
}