package com.aldiandrew.duos

import android.content.Context
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuController {
    private const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean =
        try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean =
        isAvailable() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun requestPermission() {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun hideSystemBar(): Boolean = runCommand(
        "am broadcast -a com.android.systemui.demo -e command exit",
        "cmd statusbar send-disable-flag system-icons clock notification-icons",
        "settings put global policy_control immersive.status=*"
    )

    fun restoreSystemBar(): Boolean = runCommand(
        "cmd statusbar send-disable-flag none",
        "settings delete global policy_control",
        "settings put global policy_control null",
        "am broadcast -a com.android.systemui.demo -e command exit"
    )

    private fun runCommand(vararg commands: String): Boolean {
        if (!hasPermission()) return false
        return try {
            commands.forEach { command ->
                val process: ShizukuRemoteProcess = Shizuku.newProcess(
                    arrayOf("sh", "-c", command),
                    null,
                    null
                )
                process.waitFor()
                process.destroy()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
