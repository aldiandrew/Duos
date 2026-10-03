package com.aldiandrew.duos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val enabled = context.getSharedPreferences("duos", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

        if (!enabled || !Settings.canDrawOverlays(context)) {
            return
        }

        StatusBarHider.hide(context)

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StatusBarService::class.java)
            )
        } catch (_: Throwable) {
        }
    }
}
