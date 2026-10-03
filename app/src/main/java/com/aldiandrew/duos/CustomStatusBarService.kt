package com.aldiandrew.duos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private val handler = Handler(android.os.Looper.getMainLooper())
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                "Custom status bar service started: " +
                    "uid=${android.os.Process.myUid()} " +
                    "pid=${android.os.Process.myPid()}"
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
            restoreSystemBarInBackground()
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
        restoreSystemBarInBackground()

        Log.i(TAG, "Custom status bar service stopped")

        scope.cancel()
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
        if (Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)
        ) {
            throw SecurityException(
                "Display over other apps is not enabled for Duos"
            )
        }

        windowManager =
            getSystemService(Context.WINDOW_SERVICE)
                as? WindowManager
                ?: throw IllegalStateException(
                    "WindowManager unavailable"
                )

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
            alpha = 1f

            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
            }
        }

        val customView = StatusBarView(this)

        Log.i(
            TAG,
            "Adding custom status bar window: " +
                "type=${params.type}, " +
                "width=${params.width}, " +
                "height=${params.height}, " +
                "uid=${android.os.Process.myUid()}, " +
                "pid=${android.os.Process.myPid()}"
        )

        windowManager?.addView(customView, params)

        Log.i(
            TAG,
            "Custom status bar window added successfully"
        )

        rootView = customView
        customView.refresh()

        handler.post(clockRunnable)
    }

    private inner class StatusBarView(
        context: Context
    ) : View(context) {

        private val timePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = sp(14f)
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.NORMAL
                )
            }

        private val datePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = sp(11f)
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.NORMAL
                )
            }

        private val iconPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = dp(1.8f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

        private val dateFormat =
            SimpleDateFormat(
                "dd MMM",
                Locale.getDefault()
            )

        private val timeFormat =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            )

        private var timeText = ""
        private var dateText = ""
        private var batteryLevel = 0
        private var charging = false
        private var wifiConnected = false
        private var wifiLevel = 0
        private var lightIcons = false

        init {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility =
                IMPORTANT_FOR_ACCESSIBILITY_NO

            updateIconAppearance()
        }

        fun refresh() {
            val now = Date()

            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now)

            batteryState()
            wifiState()
            updateIconAppearance()

            postInvalidate()

            updateAppearanceFromSystemUi()
        }

        private fun updateAppearanceFromSystemUi() {
            scope.launch {
                val output =
                    ShizukuManager.executeCommand(
                        "dumpsys statusbar"
                    ).getOrDefault("")

                val appearanceLine =
                    output.lineSequence()
                        .firstOrNull {
                            it.trimStart()
                                .startsWith("mAppearance=")
                        }

                val detectedLight =
                    appearanceLine?.contains(
                        "LIGHT_STATUS_BARS",
                        ignoreCase = true
                    ) ?: (
                        (
                            resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK
                            ) !=
                                Configuration.UI_MODE_NIGHT_YES
                        )

                handler.post {
                    if (lightIcons != detectedLight) {
                        lightIcons = detectedLight
                        updateIconAppearance()
                        invalidate()
                    }
                }
            }
        }

        private fun updateIconAppearance() {
            val color =
                if (lightIcons) Color.BLACK else Color.WHITE

            timePaint.color = color
            datePaint.color = color
            iconPaint.color = color
            fillPaint.color = color
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val left = dp(12f)
            val centerY = height / 2f

            // AOSP-style left cluster: clock first, compact date next.
            canvas.drawText(
                timeText,
                left,
                centeredBaseline(timePaint),
                timePaint
            )

            val timeWidth =
                timePaint.measureText(timeText)

            canvas.drawText(
                dateText,
                left + timeWidth + dp(7f),
                centeredBaseline(datePaint),
                datePaint
            )

            // Right cluster: Wi-Fi then battery, like the modern status bar.
            var x = width.toFloat() - dp(12f)

            val batteryWidth =
                drawBattery(
                    canvas,
                    x,
                    centerY
                )

            x -= batteryWidth + dp(8f)

            drawWifi(
                canvas,
                x,
                centerY
            )
        }

        private fun drawBattery(
            canvas: Canvas,
            right: Float,
            centerY: Float
        ): Float {
            val bodyWidth = dp(20f)
            val bodyHeight = dp(10f)
            val capWidth = dp(2f)

            val left = right - capWidth - bodyWidth
            val top = centerY - bodyHeight / 2f

            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = dp(1.5f)

            canvas.drawRoundRect(
                RectF(
                    left,
                    top,
                    left + bodyWidth,
                    top + bodyHeight
                ),
                dp(2f),
                dp(2f),
                iconPaint
            )

            canvas.drawRoundRect(
                RectF(
                    left + bodyWidth,
                    centerY - dp(2f),
                    left + bodyWidth + capWidth,
                    centerY + dp(2f)
                ),
                dp(0.8f),
                dp(0.8f),
                fillPaint
            )

            val inner = dp(1.8f)
            val maxFill =
                bodyWidth - inner * 2f

            val fillWidth =
                maxFill *
                    batteryLevel.coerceIn(0, 100) /
                    100f

            if (fillWidth > 0f) {
                canvas.drawRoundRect(
                    RectF(
                        left + inner,
                        top + inner,
                        left + inner + fillWidth,
                        top + bodyHeight - inner
                    ),
                    dp(1f),
                    dp(1f),
                    fillPaint
                )
            }

            if (charging) {
                val bolt = Path()

                bolt.moveTo(
                    left + bodyWidth * 0.58f,
                    top + dp(1f)
                )
                bolt.lineTo(
                    left + bodyWidth * 0.43f,
                    centerY
                )
                bolt.lineTo(
                    left + bodyWidth * 0.55f,
                    centerY
                )
                bolt.lineTo(
                    left + bodyWidth * 0.44f,
                    top + bodyHeight - dp(1f)
                )
                bolt.close()

                canvas.drawPath(
                    bolt,
                    fillPaint
                )
            }

            return bodyWidth + capWidth
        }

        private fun drawWifi(
            canvas: Canvas,
            right: Float,
            centerY: Float
        ) {
            val size = dp(18f)
            val centerX = right - size / 2f
            val top = centerY - size / 2f

            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = dp(1.7f)

            if (!wifiConnected) {
                val slash = Path()

                slash.moveTo(
                    centerX - dp(6f),
                    top + dp(4f)
                )
                slash.lineTo(
                    centerX + dp(6f),
                    top + dp(14f)
                )

                canvas.drawPath(
                    slash,
                    iconPaint
                )
                return
            }

            val outer =
                RectF(
                    centerX - dp(7f),
                    top + dp(1f),
                    centerX + dp(7f),
                    top + dp(15f)
                )

            val middle =
                RectF(
                    centerX - dp(5f),
                    top + dp(4f),
                    centerX + dp(5f),
                    top + dp(14f)
                )

            val inner =
                RectF(
                    centerX - dp(3f),
                    top + dp(7f),
                    centerX + dp(3f),
                    top + dp(14f)
                )

            when (wifiLevel.coerceIn(0, 3)) {
                3 -> {
                    canvas.drawArc(
                        outer,
                        225f,
                        90f,
                        false,
                        iconPaint
                    )
                    canvas.drawArc(
                        middle,
                        225f,
                        90f,
                        false,
                        iconPaint
                    )
                    canvas.drawArc(
                        inner,
                        225f,
                        90f,
                        false,
                        iconPaint
                    )
                }

                2 -> {
                    canvas.drawArc(
                        outer,
                        225f,
                        90f,
                        false,
                        iconPaint
                    )
                    canvas.drawArc(
                        middle,
                        225f,
                        90f,
                        false,
                        iconPaint
                    )
                }

                1 -> {
                    canvas.drawArc(
                        middle,
                        225f,
                        90f,
                        false,
                        iconPaint
                    )
                }
            }

            iconPaint.style = Paint.Style.FILL
            canvas.drawCircle(
                centerX,
                top + dp(14f),
                dp(1.7f),
                fillPaint
            )
        }

        private fun batteryState() {
            val manager =
                getSystemService(Context.BATTERY_SERVICE)
                    as? BatteryManager

            batteryLevel =
                try {
                    manager?.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CAPACITY
                    )?.takeIf { it in 0..100 }
                        ?: 0
                } catch (_: Throwable) {
                    0
                }

            charging =
                try {
                    val status =
                        registerReceiver(
                            null,
                            android.content.IntentFilter(
                                Intent.ACTION_BATTERY_CHANGED
                            )
                        )?.getIntExtra(
                            BatteryManager.EXTRA_STATUS,
                            BatteryManager.BATTERY_STATUS_UNKNOWN
                        )
                            ?: BatteryManager.BATTERY_STATUS_UNKNOWN

                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                } catch (_: Throwable) {
                    false
                }
        }

        private fun wifiState() {
            wifiConnected = false
            wifiLevel = 0

            try {
                val connectivity =
                    getSystemService(
                        Context.CONNECTIVITY_SERVICE
                    ) as? ConnectivityManager

                val network =
                    connectivity?.activeNetwork

                val caps =
                    network?.let {
                        connectivity.getNetworkCapabilities(it)
                    }

                wifiConnected =
                    caps?.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    ) == true

                if (!wifiConnected) {
                    return
                }

                val wifiManager =
                    getSystemService(
                        Context.WIFI_SERVICE
                    ) as? WifiManager

                @Suppress("DEPRECATION")
                val rssi =
                    wifiManager?.connectionInfo?.rssi
                        ?: -100

                @Suppress("DEPRECATION")
                wifiLevel =
                    WifiManager.calculateSignalLevel(
                        rssi,
                        4
                    )
            } catch (_: Throwable) {
                wifiConnected = false
                wifiLevel = 0
            }
        }

        private fun centeredBaseline(
            paint: Paint
        ): Float =
            height / 2f -
                (paint.ascent() + paint.descent()) / 2f

        private fun dp(value: Float): Float =
            value * resources.displayMetrics.density

        private fun sp(value: Float): Float =
            value * resources.displayMetrics.scaledDensity
    }

    private fun restoreSystemBarInBackground() {
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    SystemBarController.restore()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Automatic system status bar restore failed", t)
            }
        }.apply {
            name = "Duos-SystemBar-Restore"
            isDaemon = true
            start()
        }
    }

    private fun statusBarHeightPx(): Int {
        // AOSP baseline: keep the custom visual bar close to 24dp and
        // avoid oversized vendor-specific status-bar heights.
        return (24f * resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)
    }
}
