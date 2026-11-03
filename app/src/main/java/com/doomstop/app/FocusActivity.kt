package com.doomstop.app

import android.Manifest
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class FocusActivity : AppCompatActivity() {
    private val green = Color.parseColor("#8EE05A")
    private val dim = Color.parseColor("#9DB8AA")
    private val card = Color.parseColor("#12261D")
    private val darkText = Color.parseColor("#06210F")
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ring: RingView
    private lateinit var startBtn: Button
    private lateinit var chip: Button
    private lateinit var today: TextView
    private lateinit var strictBox: CheckBox
    private val presets = ArrayList<Button>()
    private val checks = ArrayList<Pair<CheckBox, String>>()
    private val controls = ArrayList<View>()
    private var minutes = 25
    private var subject = "Study"

    private val loop = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun pill(t: String, fill: Int, txt: Int, stroke: Int = Color.TRANSPARENT) =
        Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(txt)
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(fill)
                if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
            }
            stateListAnimator = null
        }

    private fun style(b: Button, fill: Int, txt: Int) {
        (b.background as GradientDrawable).setColor(fill)
        b.setTextColor(txt)
    }

    private fun fmt(ms: Long): String {
        val s = (ms + 999) / 1000
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#050F0B")
        window.navigationBarColor = Color.parseColor("#0F5A3C")

        val sp = FocusStore.p(this)
        minutes = sp.getInt("minutes", 25)
        subject = sp.getString("subject", "Study") ?: "Study"
        val blocked = FocusStore.blocked(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.addView(text("Focus", 30f, Color.WHITE, true))
        root.addView(text("Lock in. Scroll less.", 14f, dim))

        chip = pill("", card, Color.WHITE, Color.parseColor("#2C5A43"))
        chip.setOnClickListener { pickSubject() }
        controls.add(chip)
        root.addView(chip, lp(WRAP, dp(44), 18))

        ring = RingView(this)
        root.addView(ring, lp(dp(280), dp(280), 16))

        root.addView(text("Session length (minutes)", 13f, dim), lp(WRAP, WRAP, 8))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in listOf(15, 25, 45, 60, 90)) {
            val b = pill(m.toString(), card, Color.WHITE)
            b.minWidth = 0
            b.minimumWidth = 0
            b.setPadding(0, 0, 0, 0)
            b.tag = m
            b.setOnClickListener {
                minutes = m
                FocusStore.p(this).edit().putInt("minutes", m).apply()
                refresh()
            }
            presets.add(b)
            controls.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        root.addView(row, lp(MATCH, WRAP, 6))

        strictBox = CheckBox(this).apply {
            text = "Strict mode (can't quit early)"
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(green)
            isChecked = sp.getBoolean("strict", false)
        }
        controls.add(strictBox)
        root.addView(strictBox, lp(WRAP, WRAP, 10))

        root.addView(text("Block during focus", 13f, dim), lp(WRAP, WRAP, 10))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val entries = FocusStore.APPS.entries.toList()
        for (i in entries.indices step 2) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i until minOf(i + 2, entries.size)) {
                val name = entries[j].key
                val pkg = entries[j].value
                val cb = CheckBox(this).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    buttonTintList = ColorStateList.valueOf(green)
                    isChecked = pkg in blocked
                }
                checks.add(cb to pkg)
                controls.add(cb)
                r.addView(cb, LinearLayout.LayoutParams(dp(140), WRAP))
            }
            grid.addView(r)
        }
        root.addView(grid)

        startBtn = pill("Start focus", green, darkText)
        startBtn.textSize = 18f
        startBtn.typeface = Typeface.DEFAULT_BOLD
        startBtn.setOnClickListener { onMainButton() }
        root.addView(startBtn, lp(MATCH, dp(58), 16))

        today = text("", 14f, dim)
        root.addView(today, lp(WRAP, WRAP, 14))

        val settings = pill("Block settings", Color.TRANSPARENT, green, Color.parseColor("#2C5A43"))
        settings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        root.addView(settings, lp(MATCH, dp(48), 10))

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
        if (FocusStore.p(this).getBoolean("running", false)) {
            startForegroundService(Intent(this, FocusService::class.java))
        }
        handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        super.onPause()
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun pickSubject() {
        val items = FocusStore.SUBJECTS.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Subject")
            .setItems(items) { _, i ->
                subject = items[i]
                FocusStore.p(this).edit().putString("subject", subject).apply()
                refresh()
            }
            .show()
    }

    private fun onMainButton() {
        if (!FocusStore.p(this).getBoolean("running", false)) {
            startSession()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Give up this session?")
            .setMessage("Your focus time so far will still be saved.")
            .setPositiveButton("Give up") { _, _ ->
                FocusStore.endSession(this)
                stopService(Intent(this, FocusService::class.java))
                refresh()
            }
            .setNegativeButton("Keep going", null)
            .show()
    }

    private fun startSession() {
        val apps = checks.filter { it.first.isChecked }.map { it.second }.toSet()
        if (apps.isEmpty()) {
            Toast.makeText(this, "Choose at least one app to block", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, "Turn on Usage Access for Doomstop", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
            return
        }
        val total = minutes * 60_000L
        FocusStore.p(this).edit()
            .putBoolean("running", true)
            .putLong("totalMs", total)
            .putLong("endAt", System.currentTimeMillis() + total)
            .putString("subject", subject)
            .putBoolean("strict", strictBox.isChecked)
            .putStringSet("blocked", apps)
            .apply()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, FocusService::class.java))
        refresh()
    }

    private fun refresh() {
        val p = FocusStore.p(this)
        var running = p.getBoolean("running", false)
        if (running && p.getLong("endAt", 0L) <= System.currentTimeMillis()) {
            FocusStore.endSession(this)
            running = false
        }
        val subj = if (running) (p.getString("subject", subject) ?: subject) else subject
        chip.text = subj + "  ▾"

        if (running) {
            val total = p.getLong("totalMs", 1L).coerceAtLeast(1L)
            val left = (p.getLong("endAt", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
            ring.set(left.toFloat() / total.toFloat(), fmt(left), subj)
            if (p.getBoolean("strict", false)) {
                startBtn.text = "Locked in until the timer ends"
                startBtn.isEnabled = false
                style(startBtn, Color.parseColor("#1E3A2C"), dim)
            } else {
                startBtn.text = "Give up"
                startBtn.isEnabled = true
                style(startBtn, Color.parseColor("#3A1E1E"), Color.parseColor("#FF8A80"))
            }
        } else {
            ring.set(1f, fmt(minutes * 60_000L), subj)
            startBtn.text = "Start focus"
            startBtn.isEnabled = true
            style(startBtn, green, darkText)
        }

        for (c in controls) {
            c.isEnabled = !running
            c.alpha = if (running) 0.45f else 1f
        }
        for (b in presets) {
            val sel = b.tag == minutes
            style(b, if (sel) green else card, if (sel) darkText else Color.WHITE)
        }
        today.text = "Focused today: " + FocusStore.todayMinutes(this) + " min"
    }
}