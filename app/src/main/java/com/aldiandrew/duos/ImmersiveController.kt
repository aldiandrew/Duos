package com.aldiandrew.duos

object ImmersiveController {
    private const val HIDE_FLAGS =
        "statusbar-expansion system-icons clock notification-icons"

    suspend fun hideStatusBar(): Result<String> {
        val demo = ShizukuManager.executeCommand(
            "am broadcast -a com.android.systemui.demo -e command exit"
        )

        val flags = ShizukuManager.executeCommand(
            "cmd statusbar send-disable-flag $HIDE_FLAGS"
        )

        val policy = ShizukuManager.executeCommand(
            "settings put global policy_control 'immersive.status=*'"
        )

        if (policy.isFailure) {
            return policy
        }

        val verify = ShizukuManager.executeCommand(
            "settings get global policy_control"
        )

        val value = verify.getOrDefault("").trim()
        return if (value.contains("immersive.status=*")) {
            Result.success("Status bar hidden")
        } else {
            Result.failure(
                IllegalStateException(
                    "Android did not apply immersive status-bar policy"
                )
            )
        }
    }

    suspend fun restoreStatusBar(): Result<String> {
        val flags = ShizukuManager.executeCommand(
            "cmd statusbar send-disable-flag none"
        )

        val delete = ShizukuManager.executeCommand(
            "settings delete global policy_control"
        )

        val nullFallback = ShizukuManager.executeCommand(
            "settings put global policy_control null"
        )

        val demo = ShizukuManager.executeCommand(
            "am broadcast -a com.android.systemui.demo -e command exit"
        )

        return if (
            delete.isSuccess ||
            nullFallback.isSuccess ||
            flags.isSuccess ||
            demo.isSuccess
        ) {
            Result.success("Status bar restored")
        } else {
            Result.failure(
                IllegalStateException("Could not restore status bar")
            )
        }
    }

    suspend fun isHidden(): Boolean {
        if (!ShizukuManager.hasPermission()) {
            return false
        }

        return try {
            val result = ShizukuManager.executeCommand(
                "settings get global policy_control"
            )
            val value = result.getOrDefault("").trim()
            value.contains("immersive.status=*") ||
                value.contains("immersive.full=*")
        } catch (_: Throwable) {
            false
        }
    }
}
