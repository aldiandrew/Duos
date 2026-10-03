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

    private const val KEY_SIZE_DP = "indicator_size_dp"
    private const val KEY_AUTO_POSITION = "automatic_position"
    private const val KEY_X_OFFSET_DP = "horizontal_offset_dp"
    private const val KEY_Y_OFFSET_DP = "vertical_offset_dp"

    fun getIndicatorSizeDp(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_SIZE_DP, 36f)
            .coerceIn(28f, 60f)

    fun setIndicatorSizeDp(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SIZE_DP, value.coerceIn(28f, 60f))
            .apply()
    }

    fun isAutomaticPosition(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_POSITION, true)

    fun setAutomaticPosition(context: Context, automatic: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_POSITION, automatic)
            .apply()
    }

    fun getHorizontalOffsetDp(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_X_OFFSET_DP, 0f)
            .coerceIn(-24f, 24f)

    fun setHorizontalOffsetDp(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_X_OFFSET_DP, value.coerceIn(-24f, 24f))
            .apply()
    }

    fun getVerticalOffsetDp(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_Y_OFFSET_DP, 0f)
            .coerceIn(-24f, 24f)

    fun setVerticalOffsetDp(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_Y_OFFSET_DP, value.coerceIn(-24f, 24f))
            .apply()
    }

    fun resetPosition(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_POSITION, true)
            .putFloat(KEY_X_OFFSET_DP, 0f)
            .putFloat(KEY_Y_OFFSET_DP, 0f)
            .apply()
    }

    fun colorToHex(color: Int): String =
        String.format("#%06X", color and 0xFFFFFF)
}
