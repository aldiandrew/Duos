package com.aldiandrew.duos

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat

fun notificationForDuos(context: Context): Notification =
    NotificationCompat.Builder(context, "duos")
        .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
        .setContentTitle("Duos")
        .setContentText("Custom status bar is active")
        .setOngoing(true)
        .build()
