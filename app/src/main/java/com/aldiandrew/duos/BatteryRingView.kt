package com.aldiandrew.duos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

class BatteryRingView(context: Context) : View(context) {
    var level: Int = 100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var charging: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val stroke = max(2f, width * 0.09f)
        ringPaint.strokeWidth = stroke

        val inset = stroke / 2f + 1f
        val rect = RectF(inset, inset, width - inset, height - inset)
        val sweep = 360f * level / 100f

        canvas.drawArc(rect, -90f, sweep, false, ringPaint)

        val textSize = height * 0.31f
        textPaint.textSize = textSize
        val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(level.toString(), width / 2f, baseline, textPaint)

        if (charging) {
            val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            val cx = width / 2f
            val cy = height / 2f
            val w = width * 0.16f
            val h = height * 0.32f
            val path = android.graphics.Path().apply {
                moveTo(cx + w * 0.25f, cy - h)
                lineTo(cx - w * 0.65f, cy + h * 0.05f)
                lineTo(cx - w * 0.05f, cy + h * 0.05f)
                lineTo(cx - w * 0.25f, cy + h)
                lineTo(cx + w * 0.65f, cy - h * 0.05f)
                lineTo(cx + w * 0.05f, cy - h * 0.05f)
                close()
            }
            canvas.drawPath(path, boltPaint)
        }
    }
}
