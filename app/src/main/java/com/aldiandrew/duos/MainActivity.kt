package com.aldiandrew.duos

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var shizukuAvailable by mutableStateOf(false)
    private var shizukuPermission by mutableStateOf(false)
    private var active by mutableStateOf(false)
    private var starting by mutableStateOf(false)

    private val binderReceived =
        Shizuku.OnBinderReceivedListener {
            refreshShizuku()
        }

    private val binderDead =
        Shizuku.OnBinderDeadListener {
            shizukuAvailable = false
            shizukuPermission = false
            active = false
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

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        active = ShizukuOverlayController.isBound()

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
                "The custom bar uses a Shizuku UserService so it can be placed above the Android SystemUI status bar without root."
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
                                Intent(
                                    "moe.shizuku.manager.ACTION_SETTINGS"
                                )
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
                "Custom status bar",
                active,
                if (active) {
                    "Running above SystemUI."
                } else {
                    "Not running."
                }
            )

            Button(
                enabled = shizukuAvailable &&
                    shizukuPermission &&
                    !starting,
                onClick = {
                    if (active) {
                        starting = true
                        ShizukuOverlayController.stop {
                            active = false
                            starting = false
                        }
                    } else {
                        starting = true

                        ShizukuOverlayController.start(
                            this@MainActivity
                        ) { success, message ->
                            starting = false
                            active = success

                            if (!success) {
                                Toast.makeText(
                                    this@MainActivity,
                                    message.ifBlank {
                                        "Could not start custom status bar"
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        starting -> "Starting..."
                        active -> "Stop Custom Status Bar"
                        else -> "Start Custom Status Bar"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    refreshShizuku()
                    active = ShizukuOverlayController.isBound()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh")
            }

            Text(
                "SystemUI remains underneath the custom layer and is not used as the visible status bar."
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
        active = ShizukuOverlayController.isBound()
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
