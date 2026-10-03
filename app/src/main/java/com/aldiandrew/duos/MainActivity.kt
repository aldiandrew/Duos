package com.aldiandrew.duos

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var shizukuAvailable by mutableStateOf(false)
    private var shizukuPermission by mutableStateOf(false)
    private var customActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var batteryColorHex by mutableStateOf("")
    private var batteryColorError by mutableStateOf("")
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
        batteryColorHex =
            DuoPreferences.getBatteryColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Battery Color",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Default follows the SystemUI icon color. Set a custom HEX color " +
                            "to use your own battery-ring color, including while charging.",
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    OutlinedTextField(
                        value = batteryColorHex,
                        onValueChange = {
                            batteryColorHex = it
                            batteryColorError = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        singleLine = true,
                        label = { Text("HEX, e.g. #FF6B00") },
                        isError = batteryColorError.isNotBlank(),
                        supportingText = {
                            Text(
                                batteryColorError.ifBlank {
                                    "Leave empty to match SystemUI."
                                }
                            )
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = batteryColorHex.isNotBlank() &&
                                parseBatteryColor(batteryColorHex) != null,
                            onClick = {
                                val parsed = parseBatteryColor(batteryColorHex)
                                if (parsed == null) {
                                    batteryColorError =
                                        "Enter a valid color such as #FF6B00."
                                } else {
                                    DuoPreferences.setBatteryColor(this@MainActivity, parsed)
                                    batteryColorHex = DuoPreferences.colorToHex(parsed)
                                    batteryColorError = ""
                                }
                            }
                        ) {
                            Text("Apply")
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                DuoPreferences.clearBatteryColor(this@MainActivity)
                                batteryColorHex = ""
                                batteryColorError = ""
                            }
                        ) {
                            Text("SystemUI")
                        }
                    }

                    val preview = parseBatteryColor(batteryColorHex)
                    if (preview != null) {
                        Surface(
                            color = ComposeColor(preview),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Text(
                                DuoPreferences.colorToHex(preview),
                                color = if (isDarkPreview(preview)) {
                                    ComposeColor.White
                                } else {
                                    ComposeColor.Black
                                },
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
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

    private fun parseBatteryColor(value: String): Int? {
        val normalized = value.trim().let {
            if (it.startsWith("#")) it else "#$it"
        }

        if (!normalized.matches(Regex("#[0-9A-Fa-f]{6}"))) {
            return null
        }

        return try {
            Color.parseColor(normalized)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isDarkPreview(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val luminance =
            (0.299 * r) + (0.587 * g) + (0.114 * b)

        return luminance < 150.0
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
