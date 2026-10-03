package com.aldiandrew.duos

import android.content.Context
import android.graphics.Color
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
class ShizukuOverlayUserService(private val context: Context) : IDuosOverlay.Stub() {

    companion object {
        private const val TAG = "duos_overlay"
        private const val DESTROY_TRANSACTION = 16777115
    }

    @Volatile
    private var ready = false

    @Volatile
    private var errorMessage = ""

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var dateView: TextView? = null
    private var timeView: TextView? = null
    private var batteryView: TextView? = null

    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateText()
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        try {
            createOverlay()
        } catch (t: Throwable) {
            errorMessage = t.stackTraceToString()
            Log.e(TAG, "createOverlay() failed: uid=${Process.myUid()} pid=${Process.myPid()}", t)
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

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.BLACK)
            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        dateView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
        }

        timeView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            setPadding(dp(10), 0, 0, 0)
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        batteryView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
        }

        container.addView(
            dateView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        container.addView(
            timeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        container.addView(spacer)

        container.addView(
            batteryView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        Log.i(TAG, "Adding custom status bar window: type=${params.type}, width=${params.width}, height=${params.height}, uid=${Process.myUid()}")
        windowManager?.addView(container, params)
        Log.i(TAG, "Custom status bar window added successfully")
        rootView = container

        updateText()
        handler.post(clockRunnable)
        ready = true
    }

    private fun updateText() {
        val now = Date()

        dateView?.text =
            SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
                .format(now)

        timeView?.text =
            SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(now)

        batteryView?.text =
            batteryPercent().toString() + "%"
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

        val value = if (id != 0) {
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
        dateView = null
        timeView = null
        batteryView = null
        ready = false
    }
}
