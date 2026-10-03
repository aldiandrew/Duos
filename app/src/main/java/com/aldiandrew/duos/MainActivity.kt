package com.aldiandrew.duos

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var shizukuAvailable by mutableStateOf(false)
    private var shizukuPermission by mutableStateOf(false)

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refreshShizuku()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        refreshShizuku()
    }

    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { code, _ ->
            if (code == ShizukuManager.REQUEST_CODE) {
                refreshShizuku()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        } catch (_: Throwable) {
        }

        refreshShizuku()

        if (!getSharedPreferences("duos", MODE_PRIVATE)
                .getBoolean("enabled", false)
        ) {
            lifecycleScope.launch {
                if (ShizukuManager.hasPermission()) {
                    StatusBarHider.restore()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

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
        val scope = rememberCoroutineScope()

        var overlayGranted by remember {
            mutableStateOf(Settings.canDrawOverlays(this))
        }

        var active by remember {
            mutableStateOf(false)
        }

        LaunchedEffect(shizukuAvailable, shizukuPermission) {
            overlayGranted =
                Settings.canDrawOverlays(this@MainActivity)

            active =
                shizukuAvailable &&
                    shizukuPermission &&
                    StatusBarHider.isHidden()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                "Hide the stock status bar with Shizuku and display a custom overlay. No root and no ADB WRITE_SECURE_SETTINGS grant is required."
            )

            StatusCard(
                "Shizuku",
                shizukuAvailable,
                if (shizukuAvailable) {
                    "Shizuku service detected."
                } else {
                    "Start Shizuku first."
                }
            )

            if (!shizukuAvailable) {
                Button(
                    onClick = {
                        try {
                            startActivity(
                                Intent("moe.shizuku.manager.ACTION_SETTINGS")
                            )
                        } catch (_: Throwable) {
                            Toast.makeText(
                                this@MainActivity,
                                "Open Shizuku manually and start the service.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Shizuku")
                }
            }

            StatusCard(
                "Duos permission",
                shizukuPermission,
                if (shizukuPermission) {
                    "Authorized."
                } else {
                    "Authorization is required."
                }
            )

            if (shizukuAvailable && !shizukuPermission) {
                Button(
                    onClick = {
                        ShizukuManager.requestPermission(permissionResult)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Authorize Duos in Shizuku")
                }
            }

            StatusCard(
                "Overlay",
                overlayGranted,
                "Required for the custom status bar UI."
            )

            if (!overlayGranted) {
                Button(
                    onClick = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Overlay Permission")
                }
            }

            Button(
                enabled = shizukuAvailable &&
                    shizukuPermission &&
                    overlayGranted,
                onClick = {
                    if (active) {
                        startService(
                            Intent(
                                this@MainActivity,
                                StatusBarService::class.java
                            ).apply {
                                action = StatusBarService.ACTION_STOP
                            }
                        )
                        active = false
                    } else {
                        try {
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                Intent(
                                    this@MainActivity,
                                    StatusBarService::class.java
                                ).apply {
                                    action = StatusBarService.ACTION_START
                                }
                            )
                            active = true
                        } catch (e: Throwable) {
                            Toast.makeText(
                                this@MainActivity,
                                e.message ?: "Could not start custom status bar",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (active) {
                        "Stop and Restore Status Bar"
                    } else {
                        "Start Custom Status Bar"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    refreshShizuku()
                    overlayGranted =
                        Settings.canDrawOverlays(this@MainActivity)

                    scope.launch {
                        active =
                            shizukuAvailable &&
                                shizukuPermission &&
                                StatusBarHider.isHidden()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh")
            }

            Text(
                "If Shizuku is stopped or authorization is revoked, Duos cannot execute the privileged status-bar commands."
            )
        }
    }

    @Composable
    private fun StatusCard(
        title: String,
        ok: Boolean,
        description: String
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    title + ": " + if (ok) "READY" else "NOT READY",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    description,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
        } catch (_: Throwable) {
        }

        super.onDestroy()
    }
}
