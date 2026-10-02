package com.aldiandrew.duos

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var enabledState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DuosTheme {
                DuosScreen(
                    enabled = enabledState,
                    shizukuAvailable = ShizukuController.isAvailable(),
                    shizukuGranted = ShizukuController.hasPermission(),
                    overlayGranted = Settings.canDrawOverlays(this),
                    onToggle = ::setDuosEnabled,
                    onRequestShizuku = { ShizukuController.requestPermission() },
                    onOpenOverlay = ::openOverlaySettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessState()
    }

    private fun refreshAccessState() {
        if (!ShizukuController.hasPermission()) {
            enabledState = false
        }
    }

    private fun setDuosEnabled(enabled: Boolean) {
        if (!enabled) {
            stopDuos()
            enabledState = false
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }

        if (!ShizukuController.hasPermission()) {
            ShizukuController.requestPermission()
            return
        }

        val hidden = ShizukuController.hideSystemBar()
        if (!hidden) {
            ShizukuController.restoreSystemBar()
            enabledState = false
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, StatusBarService::class.java)
            )
            enabledState = true
        } catch (_: Throwable) {
            ShizukuController.restoreSystemBar()
            enabledState = false
        }
    }

    private fun stopDuos() {
        stopService(Intent(this, StatusBarService::class.java))
        ShizukuController.restoreSystemBar()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    @Composable
    private fun DuosScreen(
        enabled: Boolean,
        shizukuAvailable: Boolean,
        shizukuGranted: Boolean,
        overlayGranted: Boolean,
        onToggle: (Boolean) -> Unit,
        onRequestShizuku: () -> Unit,
        onOpenOverlay: () -> Unit
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duos", fontWeight = FontWeight.Bold)
                        Text("Custom Status Bar", fontSize = 12.sp)
                    }
                    IconButton(onClick = { onToggle(!enabled) }) {
                        Icon(
                            if (enabled) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (enabled) "Stop" else "Start"
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                StatusPreview(enabled)

                Text("STATUS BAR", style = MaterialTheme.typography.labelLarge)

                SettingCard(
                    icon = Icons.Outlined.BatteryFull,
                    title = "Custom status bar",
                    subtitle = if (enabled) {
                        "Active — system icons hidden"
                    } else {
                        "Replace the stock status bar"
                    },
                    checked = enabled,
                    onChecked = onToggle
                )

                Text("ACCESS", style = MaterialTheme.typography.labelLarge)

                SettingCard(
                    icon = Icons.Outlined.Security,
                    title = "Shizuku",
                    subtitle = when {
                        !shizukuAvailable -> "Shizuku is not running"
                        shizukuGranted -> "Permission granted"
                        else -> "Permission required"
                    },
                    checked = shizukuGranted,
                    onChecked = { onRequestShizuku() }
                )

                SettingCard(
                    icon = Icons.Outlined.Layers,
                    title = "Display over other apps",
                    subtitle = if (overlayGranted) {
                        "Permission granted"
                    } else {
                        "Tap to grant overlay permission"
                    },
                    checked = overlayGranted,
                    onChecked = { if (!overlayGranted) onOpenOverlay() }
                )

                Text("BATTERY", style = MaterialTheme.typography.labelLarge)

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BatteryPreview()
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text("Duo-style battery", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Circular percentage indicator with charging state.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    "No root • Shizuku powered • Android 13+",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    @Composable
    private fun SettingCard(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String,
        checked: Boolean,
        onChecked: (Boolean) -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = checked, onCheckedChange = onChecked)
            }
        }
    }

    @Composable
    private fun StatusPreview(enabled: Boolean) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("10:42", color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("Wi-Fi", color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                MiniBattery(enabled)
            }
        }
    }

    @Composable
    private fun MiniBattery(enabled: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(25.dp)) {
                val stroke = 2.dp.toPx()
                val r = size.minDimension / 2 - stroke
                drawArc(
                    Color.White,
                    -90f,
                    313f,
                    false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("87%", color = Color.White, fontSize = 12.sp)
        }
    }

    @Composable
    private fun BatteryPreview() {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val r = size.minDimension / 2 - stroke
                drawArc(
                    primaryColor,
                    -90f,
                    313f,
                    false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2)
                )
            }
            Text("87", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun DuosTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = dynamicLightColorScheme(this),
            typography = Typography(),
            content = content
        )
    }
}
