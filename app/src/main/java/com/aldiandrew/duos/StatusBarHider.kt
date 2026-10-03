package com.aldiandrew.duos

object StatusBarHider {
    suspend fun hide(): Result<String> = ImmersiveController.hideStatusBar()
    suspend fun restore(): Result<String> = ImmersiveController.restoreStatusBar()
    suspend fun isHidden(): Boolean = ImmersiveController.isHidden()
}