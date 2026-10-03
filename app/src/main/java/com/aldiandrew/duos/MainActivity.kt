package com.aldiandrew.duos

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var shizukuAvailable by mutableStateOf(false)
    private var shizukuPermission by mutableStateOf(false)
    private var systemBarHidden by mutableStateOf(false)
    private var customActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var permissionRequesting = false

    private val binderReceived =
        Shizuku.OnBinderReceivedListener {
            refreshShizuku()
            refreshSystemBarState()
            requestShizukuPermissionIfNeeded()
        }

    private val binderDead =
        Shizuku.OnBinderDeadListener {
            shizukuAvailable = false
            shizukuPermission = false
            customActive = false
            busy = false
        }

    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { code, _ ->
            if (code == ShizukuManager.REQUEST_CODE) {
                permissionRequesting = false
                refreshShizuku()
                refreshSystemBarState()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        } catch (_: Throwable) {
        }

        refreshShizuku()
        refreshSystemBarState()
        requestShizukuPermissionIfNeeded()

        customActive = ShizukuOverlayController.isBound()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Duos",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                "Custom Status Bar",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "1. Hide the original Android status bar. " +
                    "2. Show the custom status bar."
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Shizuku: " + if (shizukuAvailable) "READY" else "NOT READY",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when {
                            !shizukuAvailable ->
                                "Start Shizuku first, then return to Duos."
                            !shizukuPermission ->
                                "Duos permission is required."
                            else ->
                                "Duos is authorized."
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "System Status Bar: " +
                            if (systemBarHidden) "HIDDEN" else "VISIBLE",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when {
                            systemBarHidden && customActive ->
                                "Native status bar is hidden and the custom bar is visible."
                            systemBarHidden ->
                                "Native status bar is hidden. Custom bar can now be shown."
                            else ->
                                "Native Android status bar is visible."
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Button(
                enabled = shizukuAvailable && shizukuPermission && !busy,
                onClick = { toggleSystemStatusBar() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        busy -> "Please wait..."
                        systemBarHidden -> "Show System Status Bar"
                        else -> "Hide Status Bar"
                    }
                )
            }

            Button(
                enabled =
                    shizukuAvailable &&
                        shizukuPermission &&
                        systemBarHidden &&
                        !busy,
                onClick = { toggleCustomStatusBar() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        busy -> "Please wait..."
                        customActive -> "Hide Custom Status Bar"
                        else -> "Show Custom Status Bar"
                    }
                )
            }

            Text(
                "The custom status bar uses a normal app foreground service " +
                    "and TYPE_APPLICATION_OVERLAY. Shizuku enables the required " +
                    "overlay AppOp automatically; no manual overlay prompt is required."
            )
        }
    }

    private fun toggleSystemStatusBar() {
        busy = true

        lifecycleScope.launch {
            if (systemBarHidden) {
                if (customActive) {
                    ShizukuOverlayController.stop(
                        this@MainActivity,
                        restoreSystemBar = false
                    )
                    customActive = false
                }

                val result = SystemBarController.restore()

                if (result.isSuccess) {
                    systemBarHidden = false
                } else {
                    showError(
                        result.exceptionOrNull()?.message
                            ?: "Could not restore system status bar"
                    )
                }
            } else {
                val result = SystemBarController.hide()

                if (result.isSuccess) {
                    systemBarHidden = true
                } else {
                    showError(
                        result.exceptionOrNull()?.message
                            ?: "Could not hide system status bar"
                    )
                }
            }

            busy = false
            refreshSystemBarState()
        }
    }

    private fun toggleCustomStatusBar() {
        if (!systemBarHidden) {
            showError("Hide the system status bar first.")
            return
        }

        busy = true

        if (customActive) {
            ShizukuOverlayController.stop(
                this,
                restoreSystemBar = true
            ) {
                customActive = false
                busy = false
                refreshSystemBarState()
            }
            return
        }

        ShizukuOverlayController.start(this) { success, message ->
            customActive = success
            busy = false

            if (!success) {
                showError(
                    message.ifBlank {
                        "Could not start custom status bar"
                    }
                )
            }

            refreshSystemBarState()
        }
    }

    private fun refreshShizuku() {
        shizukuAvailable = ShizukuManager.isAvailable()
        shizukuPermission = ShizukuManager.hasPermission()
    }

    private fun refreshSystemBarState() {
        lifecycleScope.launch {
            if (!ShizukuManager.hasPermission()) {
                return@launch
            }

            val hidden = SystemBarController.isHidden()
            systemBarHidden = hidden
            customActive = ShizukuOverlayController.isBound()
        }
    }

    private fun requestShizukuPermissionIfNeeded() {
        if (
            ShizukuManager.isAvailable() &&
                !ShizukuManager.hasPermission() &&
                !permissionRequesting
        ) {
            permissionRequesting = true
            ShizukuManager.requestPermission(permissionResult)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(
            this,
            message.lineSequence().firstOrNull()
                ?: "Unknown error",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onResume() {
        super.onResume()
        refreshShizuku()
        requestShizukuPermissionIfNeeded()
        refreshSystemBarState()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
