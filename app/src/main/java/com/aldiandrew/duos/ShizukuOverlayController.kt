package com.aldiandrew.duos

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

object ShizukuOverlayController {

    private const val TAG = "duos_overlay"
    private const val USER_SERVICE_VERSION = 8

    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var binder: IDuosOverlay? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isBound(): Boolean = binder != null

    fun start(
        context: Context,
        callback: (Boolean, String) -> Unit
    ) {
        if (!ShizukuManager.hasPermission()) {
            callback(false, "Shizuku permission is not granted")
            return
        }

        scope.launch {
            // This operation intentionally does NOT hide the native status bar.
            // The UI must complete that as a separate step first.
            if (!SystemBarController.isHidden()) {
                mainHandler.post {
                    callback(
                        false,
                        "Hide the system status bar first."
                    )
                }
                return@launch
            }

            try {
                if (binder?.isReady() == true) {
                    mainHandler.post {
                        callback(true, "")
                    }
                    return@launch
                }
            } catch (_: Throwable) {
                binder = null
            }

            val component = ComponentName(
                context.packageName,
                ShizukuOverlayUserService::class.java.name
            )

            val serviceArgs = Shizuku.UserServiceArgs(component)
                .daemon(true)
                .tag(TAG)
                .processNameSuffix("overlay")
                .version(USER_SERVICE_VERSION)

            lateinit var serviceConnection: ServiceConnection

            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?
                ) {
                    try {
                        val remote =
                            IDuosOverlay.Stub.asInterface(service)

                        binder = remote

                        val success =
                            remote?.isReady() == true

                        val message =
                            if (success) {
                                ""
                            } else {
                                remote?.getError().orEmpty()
                                    .ifBlank {
                                        "Custom status bar overlay did not start"
                                    }
                            }

                        if (!success) {
                            try {
                                Shizuku.unbindUserService(
                                    serviceArgs,
                                    serviceConnection,
                                    true
                                )
                            } catch (_: Throwable) {
                            }

                            binder = null
                            args = null
                            connection = null
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

                mainHandler.post {
                    callback(
                        false,
                        t.message
                            ?: "Could not start Shizuku custom status bar"
                    )
                }
            }
        }
    }

    fun stop(
        restoreSystemBar: Boolean = false,
        callback: (() -> Unit)? = null
    ) {
        val serviceArgs = args
        val serviceConnection = connection

        binder = null
        args = null
        connection = null

        scope.launch {
            try {
                if (serviceArgs != null) {
                    Shizuku.unbindUserService(
                        serviceArgs,
                        serviceConnection,
                        true
                    )
                }
            } catch (_: Throwable) {
            }

            if (restoreSystemBar) {
                SystemBarController.restore()
            }

            mainHandler.post {
                callback?.invoke()
            }
        }
    }
}
