package com.aldiandrew.duos

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class StatusBarService : Service() {
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var batteryView: BatteryRingView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                batteryView?.level = (level * 100f / scale).roundToInt()
                batteryView?.charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7, notification())
        windowManager = getSystemService(WindowManager::class.java)
        buildOverlay()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun buildOverlay() {
        val density = resources.displayMetrics.density
        val height = (34 * density).roundToInt()

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        batteryView = BatteryRingView(this).apply {
            level = getCurrentLevel()
            charging = isCharging()
        }

        val params = FrameLayout.LayoutParams((34 * density).roundToInt(), height).apply {
            gravity = Gravity.END or Gravity.TOP
            rightMargin = (8 * density).roundToInt()
            topMargin = (2 * density).roundToInt()
        }
        root!!.addView(batteryView, params)

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
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun getCurrentLevel(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return (level * 100f / scale.coerceAtLeast(1)).roundToInt()
    }

    private fun isCharging(): Boolean {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("duos", "Duos", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, "duos")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Duos")
            .setContentText("Custom status bar is active")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        unregisterReceiver(receiver)
        try { windowManager?.removeView(root) } catch (_: Exception) {}
        ShizukuController.restoreSystemBar()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
