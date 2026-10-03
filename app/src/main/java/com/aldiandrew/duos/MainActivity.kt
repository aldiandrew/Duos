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
    private var customActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var permissionRequesting = false

    private val binderReceived =
        Shizuku.OnBinderReceivedListener {
            refreshShizuku()
            refreshCustomState()
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
                refreshCustomState()
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
        refreshCustomState()
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
                "Hybrid mode keeps the native SystemUI clock and notification icons, " +
                    "while Duos replaces only the native system-icon group with the Duo indicator."
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
                                "Authorized. No root is required."
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Custom Status Bar: " +
                            if (customActive) "ACTIVE" else "OFF",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (customActive) {
                            "SystemUI stays visible. Clock and notifications remain native; " +
                                "battery, Wi-Fi and cellular indicators are represented by Duo."
                        } else {
                            "SystemUI is untouched until you enable the custom bar."
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Button(
                enabled =
                    shizukuAvailable &&
                        shizukuPermission &&
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
                "Duos uses a normal application overlay for rendering. Shizuku is only the " +
                    "control layer for the SystemUI flags and overlay AppOp. If the custom " +
                    "service stops or fails, the native status bar is restored automatically."
            )
        }
    }

    private fun toggleCustomStatusBar() {
        busy = true

        if (customActive) {
            ShizukuOverlayController.stop(
                this,
                restoreSystemBar = true
            ) {
                customActive = false
                busy = false
                refreshCustomState()
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

            refreshCustomState()
        }
    }

    private fun refreshShizuku() {
        shizukuAvailable = ShizukuManager.isAvailable()
        shizukuPermission = ShizukuManager.hasPermission()
    }

    private fun refreshCustomState() {
        lifecycleScope.launch {
            if (!ShizukuManager.hasPermission()) {
                customActive = false
                return@launch
            }

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
        refreshCustomState()
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
