package com.aldiandrew.duos

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var shizukuAvailable by mutableStateOf(false)
    private var shizukuPermission by mutableStateOf(false)

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshShizuku() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == ShizukuManager.REQUEST_CODE) refreshShizuku()
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        } catch (_: Throwable) {}
        refreshShizuku()
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val scope = rememberCoroutineScope()
        var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
        var active by remember { mutableStateOf(false) }

        LaunchedEffect(shizukuAvailable, shizukuPermission) {
            overlayGranted = Settings.canDrawOverlays(this@MainActivity)
            active = shizukuAvailable && shizukuPermission && ImmersiveController.isHidden()
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Duos", style = MaterialTheme.typography.headlineMedium)
            Text("Custom Status Bar", style = MaterialTheme.typography.titleLarge)
            Text("Duos uses Shizuku like CleanBar. Shizuku executes the privileged SystemUI/settings commands. No root and no ADB WRITE_SECURE_SETTINGS grant is required.")

            StatusCard("Shizuku", shizukuAvailable,
                if (shizukuAvailable) "Shizuku service detected." else "Start Shizuku first.")
            if (!shizukuAvailable) {
                Button(
                    onClick = {
                        try {
                            startActivity(Intent("moe.shizuku.manager.ACTION_SETTINGS"))
                        } catch (_: Throwable) {
                            Toast.makeText(this@MainActivity, "Open Shizuku manually and start the service.", Toast.LENGTH_LONG).show()
                        }
                    },
                    Modifier.fillMaxWidth()
                ) { Text("Open Shizuku") }
            }

            StatusCard("Duos permission", shizukuPermission,
                if (shizukuPermission) "Authorized." else "Authorization is required.")
            if (shizukuAvailable && !shizukuPermission) {
                Button(
                    onClick = { ShizukuManager.requestPermission(permissionResult) },
                    Modifier.fillMaxWidth()
                ) { Text("Authorize Duos in Shizuku") }
            }

            StatusCard("Overlay", overlayGranted,
                "Required for the custom status bar UI.")
            if (!overlayGranted) {
                Button(
                    onClick = {
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ))
                    },
                    Modifier.fillMaxWidth()
                ) { Text("Grant Overlay Permission") }
            }

            Button(
                enabled = shizukuAvailable && shizukuPermission && overlayGranted,
                onClick = {
                    scope.launch {
                        val result = if (active) {
                            ImmersiveController.restoreStatusBar()
                        } else {
                            ImmersiveController.hideStatusBar()
                        }
                        if (result.isFailure) {
                            Toast.makeText(
                                this@MainActivity,
                                result.exceptionOrNull()?.message ?: "Command failed",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        if (!active) {
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                Intent(this@MainActivity, StatusBarService::class.java)
                            )
                        } else {
                            startService(
                                Intent(this@MainActivity, StatusBarService::class.java).apply {
                                    action = StatusBarService.ACTION_STOP
                                }
                            )
                        }
                        active = !active
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (active) "Stop and Restore Status Bar" else "Start Custom Status Bar")
            }

            OutlinedButton(
                onClick = {
                    refreshShizuku()
                    overlayGranted = Settings.canDrawOverlays(this@MainActivity)
                },
                Modifier.fillMaxWidth()
            ) { Text("Refresh") }

            Text("If Shizuku is stopped or authorization is revoked, Duos cannot execute the privileged status-bar commands.")
        }
    }

    @Composable
    private fun StatusCard(title: String, ok: Boolean, description: String) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("$title: ${if (ok) "READY" else "NOT READY"}", style = MaterialTheme.typography.titleMedium)
                Text(description, Modifier.padding(top = 4.dp))
            }
        }
    }

    private fun refreshShizuku() {
        shizukuAvailable = ShizukuManager.isAvailable()
        shizukuPermission = ShizukuManager.hasPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshShizuku()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
