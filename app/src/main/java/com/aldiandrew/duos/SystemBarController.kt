package com.aldiandrew.duos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object SystemBarController {

    suspend fun isHidden(): Boolean = withContext(Dispatchers.IO) {
        val current = ShizukuManager.executeCommand(
            "settings get global policy_control"
        )

        if (current.isFailure) {
            return@withContext false
        }

        current.getOrDefault("")
            .trim()
            .contains("immersive.status=*")
    }

    suspend fun hide(): Result<String> = withContext(Dispatchers.IO) {
        // CleanBar-style sequence: leave demo mode, disable the visible
        // status-bar elements, then apply the immersive status policy.
        ShizukuManager.executeCommand(
            "am broadcast -a com.android.systemui.demo -e command exit"
        )

        ShizukuManager.executeCommand(
            "cmd statusbar send-disable-flag system-icons clock notification-icons"
        )

        val policy = ShizukuManager.executeCommand(
            "settings put global policy_control 'immersive.status=*'"
        )

        if (policy.isFailure) {
            return@withContext policy
        }

        delay(150)

        if (isHidden()) {
            Result.success("System status bar hidden")
        } else {
            Result.failure(
                IllegalStateException(
                    "Android did not apply immersive.status policy"
                )
            )
        }
    }

    suspend fun restore(): Result<String> = withContext(Dispatchers.IO) {
        val flags = ShizukuManager.executeCommand(
            "cmd statusbar send-disable-flag none"
        )

        val delete = ShizukuManager.executeCommand(
            "settings delete global policy_control"
        )

        val nullValue = ShizukuManager.executeCommand(
            "settings put global policy_control null"
        )

        val demoExit = ShizukuManager.executeCommand(
            "am broadcast -a com.android.systemui.demo -e command exit"
        )

        if (
            flags.isFailure &&
            delete.isFailure &&
            nullValue.isFailure &&
            demoExit.isFailure
        ) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Could not restore system status bar"
                )
            )
        }

        delay(150)

        if (!isHidden()) {
            Result.success("System status bar restored")
        } else {
            Result.failure(
                IllegalStateException(
                    "Android still reports immersive.status=*"
                )
            )
        }
    }
}
