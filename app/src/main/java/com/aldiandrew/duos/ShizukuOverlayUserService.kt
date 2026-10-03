package com.aldiandrew.duos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
class ShizukuOverlayUserService(
    private val context: Context
) : IDuosOverlay.Stub() {

    companion object {
        private const val TAG = "duos_overlay"
        private const val DESTROY_TRANSACTION = 16777115
    }

    @Volatile
    private var ready = false

    @Volatile
    private var errorMessage = ""

    private var windowManager: WindowManager? = null
    private var rootView: StatusBarView? = null

    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            rootView?.refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        try {
            createOverlay()
        } catch (t: Throwable) {
            errorMessage = t.stackTraceToString()
            Log.e(
                TAG,
                "createOverlay() failed: uid=${Process.myUid()} pid=${Process.myPid()}",
                t
            )
            ready = false
        }
    }

    override fun isReady(): Boolean = ready

    override fun getError(): String = errorMessage

    override fun onTransact(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int
    ): Boolean {
        if (code == DESTROY_TRANSACTION) {
            destroyOverlay()
            System.exit(0)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun createOverlay() {
        windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: throw IllegalStateException("WindowManager unavailable")

        val height = statusBarHeightPx()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            packageName = context.packageName

            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
            }
        }

        // Do not create TextView here. A Shizuku shell UserService can inherit
        // a theme/font configuration that makes TextView initialization fail
        // with "The Typeface is not fully initialized". Canvas rendering avoids
        // that framework/theme path entirely.
        val customView = StatusBarView(context)

        Log.i(
            TAG,
            "Adding custom status bar window: " +
                "type=${params.type}, width=${params.width}, " +
                "height=${params.height}, uid=${Process.myUid()}"
        )

        windowManager?.addView(customView, params)

        Log.i(TAG, "Custom status bar window added successfully")

        rootView = customView
        customView.refresh()

        handler.post(clockRunnable)
        ready = true
    }

    private inner class StatusBarView(
        context: Context
    ) : View(context) {

        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(12).toFloat()
        }

        private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(14).toFloat()
        }

        private val batteryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(12).toFloat()
        }

        private val backgroundPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        private val dateFormat =
            SimpleDateFormat("EEE, dd MMM", Locale.getDefault())

        private val timeFormat =
            SimpleDateFormat("HH:mm", Locale.getDefault())

        private var dateText = ""
        private var timeText = ""
        private var batteryText = "0%"

        init {
            setBackgroundColor(Color.BLACK)
            importantForAccessibility =
                IMPORTANT_FOR_ACCESSIBILITY_NO
            refresh()
        }

        fun refresh() {
            val now = Date()
            dateText = dateFormat.format(now)
            timeText = timeFormat.format(now)
            batteryText = batteryPercent().toString() + "%"
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                backgroundPaint
            )

            val left = dp(14).toFloat()
            val right = width - dp(14).toFloat()
            val timeGap = dp(10).toFloat()

            val dateWidth = datePaint.measureText(dateText)
            val timeWidth = timePaint.measureText(timeText)
            val batteryWidth = batteryPaint.measureText(batteryText)

            val centerY =
                height / 2f -
                    (datePaint.ascent() + datePaint.descent()) / 2f

            canvas.drawText(
                dateText,
                left,
                centerY,
                datePaint
            )

            val timeX = left + dateWidth + timeGap

            canvas.drawText(
                timeText,
                timeX,
                height / 2f -
                    (timePaint.ascent() + timePaint.descent()) / 2f,
                timePaint
            )

            val batteryX = right - batteryWidth

            canvas.drawText(
                batteryText,
                batteryX,
                centerY,
                batteryPaint
            )
        }
    }

    private fun batteryPercent(): Int {
        return try {
            val manager =
                context.getSystemService(Context.BATTERY_SERVICE)
                    as? BatteryManager

            val value =
                manager?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                ) ?: -1

            if (value in 0..100) value else 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun statusBarHeightPx(): Int {
        val id = context.resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )

        val value =
            if (id != 0) {
                context.resources.getDimensionPixelSize(id)
            } else {
                dp(24)
            }

        return value.coerceAtLeast(dp(24))
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun destroyOverlay() {
        handler.removeCallbacks(clockRunnable)

        rootView?.let {
            try {
                windowManager?.removeViewImmediate(it)
            } catch (_: Throwable) {
                try {
                    windowManager?.removeView(it)
                } catch (_: Throwable) {
                }
            }
        }

        rootView = null
        ready = false
    }
}
