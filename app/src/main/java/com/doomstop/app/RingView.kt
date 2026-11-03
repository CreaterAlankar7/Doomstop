package com.doomstop.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

class RingView(context: Context) : View(context) {
    private var progress = 1f
    private var time = "25:00"
    private var sub = "Study"

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E3A2C")
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8EE05A")
        strokeCap = Paint.Cap.ROUND
    }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0C1F17")
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9DB8AA")
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun set(p: Float, t: String, s: String) {
        progress = p
        time = t
        sub = s
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = w * 0.045f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        rect.set(stroke, stroke, w - stroke, h - stroke)
        canvas.drawOval(rect, fillP)
        canvas.drawOval(rect, track)
        canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, arc)
        big.textSize = w * 0.22f
        small.textSize = w * 0.06f
        canvas.drawText(sub, w / 2, h * 0.36f, small)
        canvas.drawText(time, w / 2, h / 2 + big.textSize * 0.35f, big)
        canvas.drawText("Focus timer", w / 2, h * 0.70f, small)
    }
}