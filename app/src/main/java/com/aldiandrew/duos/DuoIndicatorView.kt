package com.aldiandrew.duos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Compact Canvas renderer for Duos' custom status bar.
 *
 * It deliberately uses plain Android Canvas: no Rive/native renderer and no SystemUI classes. This
 * keeps the application overlay safe while retaining the Duo-style ring, Wi-Fi, cellular dots and
 * network indicator.
 */
class DuoIndicatorView(context: Context) : View(context) {

    private companion object {
        const val DESIGN_SIZE = 103f
        const val STROKE = 7.2f
        const val TRACK_ALPHA = 0.22f
        const val DOT_RADIUS = 5.5f
        const val PERCENT_FONT = 31f
        const val PERCENT_FONT_3 = 25f

        val DOTS = arrayOf(
            floatArrayOf(-27f, 42.7f),
            floatArrayOf(-9.5f, 49.7f),
            floatArrayOf(8.5f, 50.2f),
            floatArrayOf(26f, 44.3f)
        )
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
    }

    private val wifiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()
    private val arc = RectF()

    @Volatile
    private var state = DuoStatusState()

    fun update(newState: DuoStatusState) {
        state = newState
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val side = min(width, height).toFloat()
        if (side <= 0f) return

        val k = side / DESIGN_SIZE
        val stroke = STROKE * k
        val radius = (side - stroke) / 2f
        val cx = width / 2f
        val cy = height / 2f

        arc.set(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        val current = state
        ringPaint.strokeWidth = stroke

        drawTrack(canvas, current, arc)
        drawProgress(canvas, current, arc)
        drawMiddle(canvas, current, k, cx, cy)
        drawSignalDots(canvas, current, k, cx, cy)
        drawBatteryText(canvas, current, k, cx, cy)
    }

    private fun drawTrack(
        canvas: Canvas,
        current: DuoStatusState,
        bounds: RectF
    ) {
        ringPaint.color = withAlpha(current.foregroundColor, TRACK_ALPHA)

        val gap = if (current.charging) 55.6f else 72f
        val half = (360f - gap) / 2f

        // Two arcs leave a clean top opening for the percentage/charging bolt.
        canvas.drawArc(bounds, 126f, half, false, ringPaint)
        canvas.drawArc(bounds, 180f + gap / 2f, half, false, ringPaint)
    }

    private fun drawProgress(
        canvas: Canvas,
        current: DuoStatusState,
        bounds: RectF
    ) {
        ringPaint.color = current.batteryColor

        val level = current.batteryLevel.coerceIn(0, 100)
        val gap = if (current.charging) 55.6f else 72f
        val half = (360f - gap) / 2f

        if (current.charging) {
            canvas.drawArc(bounds, 126f, half, false, ringPaint)
            canvas.drawArc(
                bounds,
                180f + gap / 2f,
                half,
                false,
                ringPaint
            )
            drawBolt(canvas, current.batteryColor, bounds.centerX(), bounds.centerY(), bounds.width())
            return
        }

        val left = half * min(level, 50) / 50f
        val right = half * maxOf(level - 50, 0) / 50f

        if (left > 0f) {
            canvas.drawArc(bounds, 126f, left, false, ringPaint)
        }
        if (right > 0f) {
            canvas.drawArc(
                bounds,
                180f + gap / 2f,
                right,
                false,
                ringPaint
            )
        }
    }

    private fun drawBatteryText(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        if (current.charging) return

        val text = current.batteryLevel.coerceIn(0, 100).toString()
        textPaint.color = current.foregroundColor
        textPaint.textSize =
            (if (text.length >= 3) PERCENT_FONT_3 else PERCENT_FONT) * k

        // Center of the percentage text box at the upper ring gap, matching the compact Duo geometry.
        val textCenterY = cy - 39f * k
        val baseline =
            textCenterY - (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.drawText(text, cx, baseline, textPaint)
    }

    private fun drawMiddle(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        // Priority matches the status meaning: airplane -> DND -> active Wi-Fi -> mobile generation.
        when {
            current.airplane -> drawAirplane(
                canvas,
                current.foregroundColor,
                k,
                cx,
                cy + 10f * k
            )
            current.dnd -> drawMoon(
                canvas,
                current.foregroundColor,
                k,
                cx,
                cy + 12f * k
            )
            current.wifiConnected -> drawWifi(
                canvas,
                current.foregroundColor,
                current.wifiLevel,
                k,
                cx,
                cy + 13f * k
            )
            current.networkGeneration.isNotEmpty() -> drawNetwork(
                canvas,
                current.foregroundColor,
                current.networkGeneration,
                k,
                cx,
                cy + 10f * k
            )
        }
    }

    private fun drawNetwork(
        canvas: Canvas,
        color: Int,
        label: String,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        textPaint.color = color
        textPaint.textSize = 23f * k
        val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, cx, baseline, textPaint)
    }

    private fun drawWifi(
        canvas: Canvas,
        color: Int,
        level: Int,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        val (outer, middle, dot) = DuoStatusMapper.wifiOpacities(level)
        val wx = cx - 0.5f * k
        val wy = cy

        wifiPaint.strokeWidth = 4.8f * k
        drawWifiArc(canvas, color, wx, wy, 31.1f, outer, k)
        drawWifiArc(canvas, color, wx, wy, 18.15f, middle, k)

        fillPaint.color = withAlpha(color, dot)
        canvas.drawCircle(
            wx,
            wy + 16.2f * k,
            4.4f * k,
            fillPaint
        )
    }

    private fun drawWifiArc(
        canvas: Canvas,
        color: Int,
        x: Float,
        y: Float,
        radius: Float,
        alpha: Float,
        k: Float
    ) {
        if (alpha <= 0f) return

        wifiPaint.color = withAlpha(color, alpha)
        arc.set(
            x - radius * k,
            y - radius * k,
            x + radius * k,
            y + radius * k
        )
        canvas.drawArc(arc, 223f, 74f, false, wifiPaint)
    }

    private fun drawSignalDots(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        val opacities = DuoStatusMapper.cellOpacities(
            if (current.airplane) 0 else current.cellLevel
        )

        for (i in DOTS.indices) {
            val x = cx + DOTS[i][0] * k
            val y = cy + DOTS[i][1] * k
            fillPaint.color = withAlpha(current.foregroundColor, opacities[i])
            canvas.drawCircle(x, y, DOT_RADIUS * k, fillPaint)
        }
    }

    private fun drawMoon(
        canvas: Canvas,
        color: Int,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        fillPaint.color = color
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(k * 0.48f, k * 0.48f)
        path.reset()
        path.moveTo(-1.8f, -16.3f)
        path.cubicTo(-1.5f, -16.8f, -1.5f, -17.4f, -1.8f, -17.8f)
        path.cubicTo(-2.1f, -18.3f, -2.6f, -18.5f, -3.2f, -18.4f)
        path.cubicTo(-11.9f, -16.9f, -18.5f, -9.3f, -18.5f, -0.1f)
        path.cubicTo(-18.5f, 10.1f, -10.1f, 18.5f, 0.1f, 18.5f)
        path.cubicTo(9.3f, 18.5f, 16.9f, 11.9f, 18.4f, 3.2f)
        path.cubicTo(18.5f, 2.6f, 18.3f, 2.1f, 17.8f, 1.8f)
        path.cubicTo(17.4f, 1.5f, 16.8f, 1.5f, 16.3f, 1.8f)
        path.cubicTo(14.2f, 3.4f, 11.6f, 4.2f, 8.8f, 4.2f)
        path.cubicTo(1.6f, 4.2f, -4.3f, -1.6f, -4.3f, -8.8f)
        path.cubicTo(-4.3f, -11.6f, -3.4f, -14.2f, -1.9f, -16.3f)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun drawAirplane(
        canvas: Canvas,
        color: Int,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        fillPaint.color = color
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-14f)
        canvas.scale(k * 0.24f, k * 0.24f)
        path.reset()
        path.moveTo(-2f, -31f)
        path.lineTo(5f, -8f)
        path.lineTo(29f, 6f)
        path.lineTo(29f, 11f)
        path.lineTo(5f, 5f)
        path.lineTo(10f, 26f)
        path.lineTo(4f, 29f)
        path.lineTo(0f, 6f)
        path.lineTo(-20f, 1f)
        path.lineTo(-23f, -4f)
        path.lineTo(-4f, -7f)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun drawBolt(
        canvas: Canvas,
        color: Int,
        cx: Float,
        cy: Float,
        diameter: Float
    ) {
        fillPaint.color = color
        canvas.save()
        canvas.translate(cx, cy)
        val scale = diameter / DESIGN_SIZE
        canvas.scale(scale * 0.28f, scale * 0.28f)
        path.reset()
        path.moveTo(4f, -26f)
        path.lineTo(-13f, 1f)
        path.lineTo(-1f, 1f)
        path.lineTo(-8f, 27f)
        path.lineTo(14f, -6f)
        path.lineTo(2f, -6f)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * factor)
            .toInt()
            .coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }
}
