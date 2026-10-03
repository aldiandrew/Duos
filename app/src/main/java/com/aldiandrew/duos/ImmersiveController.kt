package com.aldiandrew.duos

object ImmersiveController {
    suspend fun hideStatusBar(): Result<String> {
        ShizukuManager.executeCommand("am broadcast -a com.android.systemui.demo -e command exit")
        ShizukuManager.executeCommand("cmd statusbar send-disable-flag system-icons clock notification-icons")
        return ShizukuManager.executeCommand("settings put global policy_control immersive.status=*")
    }

    suspend fun restoreStatusBar(): Result<String> {
        ShizukuManager.executeCommand("cmd statusbar send-disable-flag none")
        ShizukuManager.executeCommand("settings delete global policy_control")
        val policy = ShizukuManager.executeCommand("settings put global policy_control null")
        ShizukuManager.executeCommand("am broadcast -a com.android.systemui.demo -e command exit")
        return if (policy.isSuccess) Result.success("Status bar restored") else policy
    }

    suspend fun isHidden(): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        val result = ShizukuManager.executeCommand("settings get global policy_control")
        val policy = result.getOrDefault("")
        return policy.isNotBlank() && policy != "null" &&
            (policy.contains("immersive.status") || policy.contains("immersive.full"))
    }
}