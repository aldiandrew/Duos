package com.aldiandrew.duos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
class CustomStatusBarService : Service() {

    companion object {
        private const val TAG = "duos_overlay"
        private const val CHANNEL_ID = "duos_custom_status_bar"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String = ""
            private set

        fun clearError() {
            lastError = ""
        }
    }

    private var windowManager: WindowManager? = null
    private var rootView: StatusBarView? = null

    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            rootView?.refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()

        clearError()

        try {
            createNotificationChannel()
            startForegroundCompat()
            createOverlay()

            isRunning = true
            Log.i(
                TAG,
                "Custom status bar service started in app process " +
                    "uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}"
            )
        } catch (t: Throwable) {
            lastError = t.stackTraceToString()
            Log.e(
                TAG,
                "Custom status bar service failed: " +
                    "uid=${android.os.Process.myUid()} " +
                    "pid=${android.os.Process.myPid()}",
                t
            )
            isRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
        windowManager = null
        isRunning = false

        Log.i(TAG, "Custom status bar service stopped")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager
                ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Custom status bar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Keeps the Duos custom status bar service running."
                setShowBadge(false)
            }
        )
    }

    private fun startForegroundCompat() {
        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }

        val notification =
            builder
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Duos")
                .setContentText("Custom status bar is running")
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            throw SecurityException(
                "Display over other apps is not enabled for Duos"
            )
        }

        windowManager =
            getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: throw IllegalStateException("WindowManager unavailable")

        val height = statusBarHeightPx()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP

            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
            }
        }

        val customView = StatusBarView(this)

        Log.i(
            TAG,
            "Adding custom status bar window: " +
                "type=${params.type}, width=${params.width}, " +
                "height=${params.height}, " +
                "uid=${android.os.Process.myUid()}, " +
                "pid=${android.os.Process.myPid()}"
        )

        windowManager?.addView(customView, params)

        Log.i(TAG, "Custom status bar window added successfully")

        rootView = customView
        customView.refresh()
        handler.post(clockRunnable)
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
        }

        fun refresh() {
            val now = Date()
            dateText = dateFormat.format(now)
            timeText = timeFormat.format(now)
            batteryText = batteryPercent().toString() + "%"
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                backgroundPaint
            )

            val left = dp(14).toFloat()
            val right = width - dp(14).toFloat()
            val gap = dp(10).toFloat()

            val dateWidth = datePaint.measureText(dateText)
            val batteryWidth = batteryPaint.measureText(batteryText)

            canvas.drawText(
                dateText,
                left,
                centeredBaseline(datePaint),
                datePaint
            )

            canvas.drawText(
                timeText,
                left + dateWidth + gap,
                centeredBaseline(timePaint),
                timePaint
            )

            canvas.drawText(
                batteryText,
                right - batteryWidth,
                centeredBaseline(batteryPaint),
                batteryPaint
            )
        }

        private fun centeredBaseline(paint: Paint): Float =
            height / 2f -
                (paint.ascent() + paint.descent()) / 2f
    }

    private fun batteryPercent(): Int {
        return try {
            val manager =
                getSystemService(Context.BATTERY_SERVICE)
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
        val id = resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )

        val value =
            if (id != 0) {
                resources.getDimensionPixelSize(id)
            } else {
                dp(24)
            }

        return value.coerceAtLeast(dp(24))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
