package com.aldiandrew.duos

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

object ShizukuController {
    private const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

    fun hasPermission(): Boolean =
        isAvailable() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun requestPermission() {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun hideSystemBar(): Boolean = runCommands(
        "am broadcast -a com.android.systemui.demo -e command exit",
        "cmd statusbar send-disable-flag system-icons clock notification-icons",
        "settings put global policy_control immersive.status=*"
    )

    fun restoreSystemBar(): Boolean = runCommands(
        "cmd statusbar send-disable-flag none",
        "settings delete global policy_control",
        "settings put global policy_control null",
        "am broadcast -a com.android.systemui.demo -e command exit"
    )

    private fun runCommands(vararg commands: String): Boolean {
        if (!hasPermission()) return false

        var success = true

        commands.forEach { command ->
            try {
                val process = newProcess(
                    arrayOf("sh", "-c", command),
                    null,
                    null
                )
                val exitCode = process.waitFor()
                process.destroy()
                if (exitCode != 0) {
                    success = false
                }
            } catch (_: Throwable) {
                success = false
            }
        }

        return success
    }

    private fun newProcess(
        command: Array<String>,
        environment: Array<String>?,
        directory: String?
    ): ShizukuRemoteProcess {
        val method: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, command, environment, directory) as ShizukuRemoteProcess
    }
}
