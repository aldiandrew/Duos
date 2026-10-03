package com.aldiandrew.duos

import android.content.Context
import android.graphics.Color

object DuoPreferences {
    private const val PREFS = "duos_preferences"
    private const val KEY_BATTERY_COLOR = "battery_color"

    fun getBatteryColorOverride(context: Context): Int? {
        val value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BATTERY_COLOR, null)
            ?: return null

        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun setBatteryColor(context: Context, color: Int) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BATTERY_COLOR, String.format("#%08X", color))
            .apply()
    }

    fun clearBatteryColor(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BATTERY_COLOR)
            .apply()
    }

    fun colorToHex(color: Int): String =
        String.format("#%06X", color and 0xFFFFFF)
}
