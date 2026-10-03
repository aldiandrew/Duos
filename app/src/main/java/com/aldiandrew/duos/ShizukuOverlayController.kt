package com.aldiandrew.duos

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

object ShizukuOverlayController {

    private const val TAG = "duos_overlay"

    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var binder: IDuosOverlay? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isBound(): Boolean = binder != null

    fun start(
        context: Context,
        callback: (Boolean, String) -> Unit
    ) {
        if (!ShizukuManager.hasPermission()) {
            callback(false, "Shizuku permission is not granted")
            return
        }

        if (binder != null) {
            try {
                val remote = binder
                if (remote != null && remote.isReady) {
                    callback(true, "")
                    return
                }
            } catch (_: Throwable) {
            }
        }

        val component = ComponentName(
            context.packageName,
            ShizukuOverlayUserService::class.java.name
        )

        val serviceArgs = Shizuku.UserServiceArgs(component)
            .daemon(true)
            .tag(TAG)
            .processNameSuffix("overlay")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {
                try {
                    val remote = IDuosOverlay.Stub.asInterface(service)
                    binder = remote

                    val success = remote != null && remote.isReady
                    val message = if (success) {
                        ""
                    } else {
                        remote?.error
                            ?: "Custom status bar overlay did not start"
                    }

                    mainHandler.post {
                        callback(success, message)
                    }
                } catch (t: Throwable) {
                    binder = null
                    mainHandler.post {
                        callback(
                            false,
                            t.message
                                ?: "Could not connect to custom status bar"
                        )
                    }
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {
                binder = null
                mainHandler.post {
                    callback(
                        false,
                        "Shizuku custom status bar service disconnected"
                    )
                }
            }
        }

        args = serviceArgs
        connection = serviceConnection

        try {
            Shizuku.bindUserService(
                serviceArgs,
                serviceConnection
            )
        } catch (t: Throwable) {
            binder = null
            args = null
            connection = null
            callback(
                false,
                t.message
                    ?: "Could not start Shizuku custom status bar"
            )
        }
    }

    fun stop() {
        val serviceArgs = args ?: return
        val serviceConnection = connection

        binder = null
        args = null
        connection = null

        try {
            Shizuku.unbindUserService(
                serviceArgs,
                serviceConnection,
                true
            )
        } catch (_: Throwable) {
        }
    }
}
