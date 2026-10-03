package com.aldiandrew.duos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class StatusBarService : Service() {

    companion object {
        private const val CHANNEL_ID = "duos_status_bar"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.aldiandrew.duos.START"
        const val ACTION_STOP = "com.aldiandrew.duos.STOP"
        private const val PREFS = "duos"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timeView: TextView? = null
    private var networkView: TextView? = null
    private var batteryView: TextView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operationJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            restoreAndStop()
        } else {
            startAndShow()
        }
        return START_NOT_STICKY
    }

    private fun startAndShow() {
        operationJob?.cancel()

        operationJob = serviceScope.launch {
            if (!ShizukuManager.hasPermission()) {
                showErrorAndStop("Shizuku permission is not granted")
                return@launch
            }

            if (!Settings.canDrawOverlays(this@StatusBarService)) {
                showErrorAndStop("Overlay permission is missing")
                return@launch
            }

            val result = StatusBarHider.hide()

            if (result.isFailure) {
                showErrorAndStop(
                    result.exceptionOrNull()?.message
                        ?: "Could not hide the system status bar"
                )
                return@launch
            }

            if (!isDestroyed) {
                showOverlay()

                if (overlayView != null) {
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putBoolean("enabled", true)
                        .apply()
                }
            }
        }
    }

    private fun restoreAndStop() {
        operationJob?.cancel()

        operationJob = serviceScope.launch {
            StatusBarHider.restore()
            stopOverlay()

            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("enabled", false)
                .apply()

            stopSelf()
        }
    }

    private fun showErrorAndStop(message: String) {
        stopOverlay()

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", false)
            .apply()

        Toast.makeText(this@StatusBarService, message, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, StatusBarService::class.java).apply {
            action = ACTION_STOP
        }

        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) {
                android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        val stopPendingIntent = android.app.PendingIntent.getService(
            this,
            10,
            stopIntent,
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Duos custom status bar")
            .setContentText("Custom status bar is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description =
                getString(R.string.notification_channel_description)

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        stopOverlay()

        if (!Settings.canDrawOverlays(this)) {
            showErrorAndStop("Overlay permission is missing")
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeightPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP

            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.argb(235, 0, 0, 0))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        timeView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        networkView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
        }

        batteryView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
            setPadding(dp(10), 0, 0, 0)
        }

        container.addView(
            timeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(spacer)
        container.addView(
            networkView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            batteryView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        try {
            windowManager?.addView(container, params)
            overlayView = container

            updateStatus()
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        } catch (e: Throwable) {
            overlayView = null
            showErrorAndStop(
                "Could not add custom status bar: " +
                    (e.message ?: "unknown error")
            )
        }
    }

    private fun statusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )

        val systemHeight = if (resourceId != 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            dp(24)
        }

        return systemHeight.coerceAtLeast(dp(24))
    }

    private fun updateStatus() {
        timeView?.text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        networkView?.text = networkLabel()
        batteryView?.text = batteryPercent().toString() + "%"
    }

    private fun batteryPercent(): Int {
        val intent = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return 0

        val level = intent.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        )
        val scale = intent.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        )

        return if (level >= 0 && scale > 0) {
            level * 100 / scale
        } else {
            0
        }
    }

    private fun networkLabel(): String {
        val manager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = manager.activeNetwork ?: return "Offline"
        val caps =
            manager.getNetworkCapabilities(network) ?: return "Offline"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            else -> "Online"
        }
    }

    private fun stopOverlay() {
        handler.removeCallbacks(updateRunnable)

        overlayView?.let {
            try {
                windowManager?.removeViewImmediate(it)
            } catch (_: Throwable) {
                try {
                    windowManager?.removeView(it)
                } catch (_: Throwable) {
                }
            }
        }

        overlayView = null
        timeView = null
        networkView = null
        batteryView = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        operationJob?.cancel()
        stopOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
