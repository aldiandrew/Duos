package com.aldiandrew.duos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
    private var rootView: DuoIndicatorView? = null

    private val handler = Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val refreshRunnable = object : Runnable {
        override fun run() {
            try {
                rootView?.update(readState())
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "state refresh failed: ${t.javaClass.simpleName}: ${t.message}"
                )
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private val appearanceRunnable = object : Runnable {
        override fun run() {
            scope.launch {
            val light = readLightStatusBar()
            val snapshot = readState(
                foregroundOverride = if (light) Color.BLACK else Color.WHITE
            )
            handler.post {
                rootView?.update(snapshot)
            }
        }
            handler.postDelayed(this, 3000L)
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
                "Compact custom status bar started: " +
                    "uid=${android.os.Process.myUid()} " +
                    "pid=${android.os.Process.myPid()}"
            )
        } catch (t: Throwable) {
            lastError = t.stackTraceToString()
            isRunning = false
            Log.e(TAG, "Custom status bar failed", t)
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
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(appearanceRunnable)

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

        // Safety rule: once the custom bar disappears, never leave the user with no status bar.
        restoreSystemBarInBackground()

        Log.i(TAG, "Compact custom status bar stopped")
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Custom status bar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Duos custom status bar service running."
                setShowBadge(false)
            }
        )
    }

    private fun startForegroundCompat() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val notification = builder
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
            startForeground(NOTIFICATION_ID, notification)
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

        val side = compactSizePx()

        val params = WindowManager.LayoutParams(
            side,
            side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            // Small inward offset from the physical right edge.
            x = dp(3f)
            y = -dp(1.5f)

            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
            }
        }

        val customView = DuoIndicatorView(this)
        customView.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO

        Log.i(
            TAG,
            "Adding compact custom status bar: " +
                "type=${params.type}, side=${params.width}, gravity=TOP|END"
        )

        windowManager?.addView(customView, params)
        rootView = customView

        // Do not block the main thread during overlay creation. The night-mode value is a safe
        // first frame; the SystemUI appearance is refined by the asynchronous reader below.
        customView.update(
            readState(
                foregroundOverride =
                    if (isNightMode()) Color.WHITE else Color.BLACK
            )
        )

        handler.post(refreshRunnable)
        handler.postDelayed(appearanceRunnable, 500L)
    }

    private fun readState(
        foregroundOverride: Int? = null
    ): DuoStatusState {
        val battery = batteryState()
        val wifi = wifiState()
        val airplane = isAirplaneOn()
        val dnd = isDndOn()
        val telephony = telephonyState(airplane)

        return DuoStatusState(
            batteryLevel = battery.first,
            charging = battery.second,
            powerSaver = isPowerSaveOn(),
            wifiLevel = wifi.first,
            wifiConnected = wifi.second,
            wifiValidated = wifi.third,
            cellLevel = telephony.first,
            networkGeneration = telephony.second,
            airplane = airplane,
            dnd = dnd,
            foregroundColor =
                foregroundOverride
                    ?: if (isNightMode()) Color.WHITE else Color.BLACK
        )
    }

    private fun batteryState(): Pair<Int, Boolean> {
        return try {
            val manager =
                getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            val level =
                manager?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )?.takeIf { it in 0..100 } ?: 0

            val status =
                registerReceiver(
                    null,
                    android.content.IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED
                    )
                )?.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

            level to (
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                )
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "battery read failed: ${t.javaClass.simpleName}"
            )
            0 to false
        }
    }

    private fun wifiState(): Triple<Int, Boolean, Boolean> {
        return try {
            val wifi =
                getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return Triple(0, false, false)

            val cm =
                getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager
                    ?: return Triple(0, false, false)

            if (!wifi.isWifiEnabled) {
                return Triple(0, false, false)
            }

            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
                ?: return Triple(0, false, false)

            val transportWifi =
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

            if (!transportWifi) {
                return Triple(0, false, false)
            }

            val validated =
                caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )

            @Suppress("DEPRECATION")
            val rssi = wifi.connectionInfo?.rssi ?: -127

            val stockBars =
                if (rssi == -127) {
                    0
                } else {
                    @Suppress("DEPRECATION")
                    (WifiManager.calculateSignalLevel(rssi, 5) + 1)
                        .coerceIn(0, 4)
                }

            // Only a validated Wi-Fi network owns the middle slot; otherwise the mobile generation
            // remains visible, matching the active data path.
            Triple(
                DuoStatusMapper.wifiBars(stockBars),
                validated,
                validated
            )
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "wifi read failed: ${t.javaClass.simpleName}"
            )
            Triple(0, false, false)
        }
    }

    private fun telephonyState(
        airplane: Boolean
    ): Pair<Int, String> {
        if (airplane) return 0 to ""

        return try {
            val tm =
                getSystemService(Context.TELEPHONY_SERVICE)
                    as? TelephonyManager
                    ?: return 0 to ""

            val level =
                try {
                    tm.signalStrength?.level?.coerceIn(0, 4) ?: 0
                } catch (_: SecurityException) {
                    0
                }

            val networkType =
                try {
                    tm.dataNetworkType
                } catch (_: SecurityException) {
                    TelephonyManager.NETWORK_TYPE_UNKNOWN
                }

            val nr =
                try {
                    val serviceState = tm.serviceState
                    val method =
                        serviceState?.javaClass?.getMethod("getNrState")
                    val nrState = method?.invoke(serviceState) as? Int
                    nrState == 2 || nrState == 3
                } catch (_: Throwable) {
                    false
                }

            level to DuoStatusMapper.networkLabel(networkType, nr)
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "telephony read failed: ${t.javaClass.simpleName}"
            )
            0 to ""
        }
    }

    private fun isAirplaneOn(): Boolean =
        try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        } catch (_: Throwable) {
            false
        }

    private fun isPowerSaveOn(): Boolean =
        try {
            val power =
                getSystemService(Context.POWER_SERVICE)
                    as? android.os.PowerManager
            power?.isPowerSaveMode == true
        } catch (_: Throwable) {
            false
        }

    private fun isDndOn(): Boolean =
        try {
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager
            val filter = manager?.currentInterruptionFilter
            filter != null &&
                filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Throwable) {
            false
        }

    private fun readLightStatusBar(): Boolean {
        return try {
            val output = runBlocking(Dispatchers.IO) {
                ShizukuManager.executeCommand(
                    "dumpsys statusbar"
                ).getOrDefault("")
            }

            val appearanceLine =
                output.lineSequence()
                    .firstOrNull {
                        it.trimStart().startsWith("mAppearance=")
                    }

            appearanceLine?.contains(
                "LIGHT_STATUS_BARS",
                ignoreCase = true
            ) ?: !isNightMode()
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "appearance read failed: ${t.javaClass.simpleName}"
            )
            !isNightMode()
        }
    }

    private fun isNightMode(): Boolean =
        (
            resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            ) == Configuration.UI_MODE_NIGHT_YES

    private fun compactSizePx(): Int =
        dp(30f).coerceAtLeast(1)

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)

    private fun restoreSystemBarInBackground() {
        Thread {
            try {
                runBlocking {
                    SystemBarController.restore()
                }
            } catch (t: Throwable) {
                Log.e(
                    TAG,
                    "Automatic system status bar restore failed",
                    t
                )
            }
        }.apply {
            name = "Duos-SystemBar-Restore"
            isDaemon = true
            start()
        }
    }
}
