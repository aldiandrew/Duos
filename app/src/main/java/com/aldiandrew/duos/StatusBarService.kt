package com.aldiandrew.duos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class StatusBarService : Service() {
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var batteryView: BatteryRingView? = null
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                .coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            batteryView?.level = (level * 100f / scale).roundToInt()
            batteryView?.charging =
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WindowManager::class.java)
        buildOverlay()
        registerBatteryReceiver()
    }

    private fun buildOverlay() {
        val density = resources.displayMetrics.density
        val height = (34f * density).roundToInt()

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        batteryView = BatteryRingView(this).apply {
            level = getCurrentLevel()
            charging = isCharging()
        }

        val batterySize = (34f * density).roundToInt()
        val batteryParams = FrameLayout.LayoutParams(
            batterySize,
            height
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            rightMargin = (8f * density).roundToInt()
            topMargin = (2f * density).roundToInt()
        }
        root?.addView(batteryView, batteryParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 0
        }

        try {
            windowManager?.addView(root, windowParams)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun registerBatteryReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                batteryReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun getCurrentBatteryIntent(): Intent? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun getCurrentLevel(): Int {
        val intent = getCurrentBatteryIntent() ?: return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            .coerceAtLeast(1)
        return (level * 100f / scale).roundToInt()
    }

    private fun isCharging(): Boolean {
        val intent = getCurrentBatteryIntent() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Duos",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Duos")
            .setContentText("Custom status bar is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: Throwable) {
            }
            receiverRegistered = false
        }

        root?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Throwable) {
            }
        }

        ShizukuController.restoreSystemBar()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "duos"
        private const val NOTIFICATION_ID = 7
    }
}
