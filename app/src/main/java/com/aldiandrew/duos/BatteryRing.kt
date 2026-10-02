package com.aldiandrew.duos

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

class BatteryRingView(context: Context) : View(context) {
    var level: Int = 100
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var charging: Boolean = false
        set(value) { field = value; invalidate() }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bolt = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * .39f

        ring.strokeWidth = size * .075f
        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, -90f, 359.5f, false, ring)

        ring.strokeWidth = size * .075f
        val sweep = level * 3.6f
        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, -90f, sweep, false, ring)

        text.textSize = size * .27f
        canvas.drawText(level.toString(), cx, cy + text.textSize * .34f, text)

        if (charging) {
            bolt.reset()
            bolt.moveTo(cx + size * .20f, cy - size * .34f)
            bolt.lineTo(cx + size * .02f, cy - size * .04f)
            bolt.lineTo(cx + size * .13f, cy - size * .04f)
            bolt.lineTo(cx - size * .02f, cy + size * .30f)
            bolt.lineTo(cx + size * .17f, cy + size * .01f)
            bolt.lineTo(cx + size * .06f, cy + size * .01f)
            bolt.close()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawPath(bolt, p)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
