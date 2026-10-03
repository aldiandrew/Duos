package com.aldiandrew.duos

import android.content.Context
import android.provider.Settings

object StatusBarHider {
    private const val POLICY_CONTROL = "policy_control"
    private const val IMMERSIVE_STATUS = "immersive.status=*"
    private const val PREFS = "duos_status_bar"

    fun isWriteSecureSettingsAvailable(context: Context): Boolean {
        return try {
            Settings.Global.putString(context.contentResolver, "duos_permission_probe", "1")
            Settings.Global.putString(context.contentResolver, "duos_permission_probe", null)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun hide(context: Context): Result<String> {
        return try {
            val current = Settings.Global.getString(context.contentResolver, POLICY_CONTROL)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("previous_policy_control", current)
                .putBoolean("saved", true)
                .apply()

            val written = Settings.Global.putString(
                context.contentResolver,
                POLICY_CONTROL,
                IMMERSIVE_STATUS
            )

            if (written) {
                Result.success("Status bar hide policy enabled")
            } else {
                Result.failure(IllegalStateException("Android rejected policy_control write"))
            }
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException(
                    "WRITE_SECURE_SETTINGS is not granted. Grant it with ADB first.",
                    e
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun restore(context: Context): Result<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getBoolean("saved", false)
            val previous = prefs.getString("previous_policy_control", null)

            val written = if (saved && !previous.isNullOrBlank() && previous != "null") {
                Settings.Global.putString(context.contentResolver, POLICY_CONTROL, previous)
            } else {
                Settings.Global.putString(context.contentResolver, POLICY_CONTROL, null)
            }

            prefs.edit().clear().apply()

            if (written) {
                Result.success("Status bar restored")
            } else {
                Result.failure(IllegalStateException("Android rejected policy_control restore"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun isHidden(context: Context): Boolean {
        return try {
            val value = Settings.Global.getString(context.contentResolver, POLICY_CONTROL)
            value?.contains("immersive.status") == true ||
                value?.contains("immersive.full") == true
        } catch (_: Throwable) {
            false
        }
    }
}
