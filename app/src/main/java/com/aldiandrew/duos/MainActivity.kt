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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
        var secureGranted by remember {
            mutableStateOf(StatusBarHider.isWriteSecureSettingsAvailable(this))
        }
        var active by remember { mutableStateOf(StatusBarHider.isHidden(this)) }

        LaunchedEffect(Unit) {
            overlayGranted = Settings.canDrawOverlays(this@MainActivity)
            secureGranted = StatusBarHider.isWriteSecureSettingsAvailable(this@MainActivity)
            active = StatusBarHider.isHidden(this@MainActivity)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Duos",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Custom Status Bar",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "No root and no Xposed. Duos uses an application overlay for the custom UI and the ADB-granted WRITE_SECURE_SETTINGS permission for the CleanBar-style immersive policy."
            )

            StatusCard(
                title = "Draw over other apps",
                ok = overlayGranted,
                description = "Required for the custom status bar to appear above other apps."
            )

            if (!overlayGranted) {
                Button(
                    onClick = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + packageName)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Overlay Permission")
                }
            }

            StatusCard(
                title = "ADB secure-settings access",
                ok = secureGranted,
                description = "Android does not provide a normal runtime dialog for WRITE_SECURE_SETTINGS."
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Run once from ADB:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "adb shell pm grant " +
                            packageName +
                            " android.permission.WRITE_SECURE_SETTINGS",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "After the command succeeds, return to Duos and tap Refresh."
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    secureGranted =
                        StatusBarHider.isWriteSecureSettingsAvailable(this@MainActivity)
                    overlayGranted = Settings.canDrawOverlays(this@MainActivity)
                    active = StatusBarHider.isHidden(this@MainActivity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Permissions")
            }

            Button(
                enabled = overlayGranted && secureGranted,
                onClick = {
                    val result = StatusBarHider.hide(this@MainActivity)
                    if (result.isFailure) {
                        Toast.makeText(
                            this@MainActivity,
                            result.exceptionOrNull()?.message ?: "Could not hide status bar",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    getSharedPreferences("duos", MODE_PRIVATE)
                        .edit()
                        .putBoolean("enabled", true)
                        .apply()

                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, StatusBarService::class.java)
                    )
                    active = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (active) "Custom Status Bar Running" else "Start Custom Status Bar")
            }

            OutlinedButton(
                enabled = active,
                onClick = {
                    startService(
                        Intent(this@MainActivity, StatusBarService::class.java).apply {
                            action = StatusBarService.ACTION_STOP
                        }
                    )
                    StatusBarHider.restore(this@MainActivity)
                    getSharedPreferences("duos", MODE_PRIVATE)
                        .edit()
                        .putBoolean("enabled", false)
                        .apply()
                    active = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop and Restore System Status Bar")
            }

            Text(
                text = "Important: WRITE_SECURE_SETTINGS is an ADB-granted privileged permission. The APK cannot grant itself this permission. Android/OEM versions may also reject or ignore the legacy global policy_control immersive setting; if that happens, an ordinary overlay cannot remove the system status bar by itself."
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
            Row(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title + ": " + if (ok) "READY" else "NOT READY",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = description,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
