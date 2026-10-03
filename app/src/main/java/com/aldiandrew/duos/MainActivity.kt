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
import androidx.compose.material3.Slider
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

    private var indicatorSizeDp by mutableStateOf(36f)
    private var automaticPosition by mutableStateOf(true)
    private var horizontalOffsetDp by mutableStateOf(0f)
    private var verticalOffsetDp by mutableStateOf(0f)

    private var batteryNormalHex by mutableStateOf("")
    private var batteryChargingHex by mutableStateOf("")
    private var batteryLowHex by mutableStateOf("")
    private var batteryPowerSaverHex by mutableStateOf("")
    private var wifiColorHex by mutableStateOf("")
    private var signalColorHex by mutableStateOf("")
    private var networkColorHex by mutableStateOf("")

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
        loadPreferences()

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
            Text("Duos", style = MaterialTheme.typography.headlineMedium)
            Text("Custom Status Bar", style = MaterialTheme.typography.titleLarge)

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
                enabled = shizukuAvailable && shizukuPermission && !busy,
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
                        "Indicator Size & Position",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Size: ${indicatorSizeDp.toInt()} dp",
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Slider(
                        value = indicatorSizeDp,
                        onValueChange = {
                            indicatorSizeDp = it
                            DuoPreferences.setIndicatorSizeDp(this@MainActivity, it)
                        },
                        valueRange = 28f..60f,
                        steps = 31,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Position",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Automatic")
                        Button(
                            onClick = {
                                automaticPosition = !automaticPosition
                                DuoPreferences.setAutomaticPosition(
                                    this@MainActivity,
                                    automaticPosition
                                )
                            }
                        ) {
                            Text(if (automaticPosition) "ON" else "OFF")
                        }
                    }

                    Text(
                        "Horizontal: ${horizontalOffsetDp.toInt()} dp",
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Slider(
                        value = horizontalOffsetDp,
                        onValueChange = {
                            horizontalOffsetDp = it
                            automaticPosition = false
                            DuoPreferences.setHorizontalOffsetDp(this@MainActivity, it)
                            DuoPreferences.setAutomaticPosition(
                                this@MainActivity,
                                false
                            )
                        },
                        valueRange = -24f..24f,
                        steps = 47,
                        enabled = !automaticPosition,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Vertical: ${verticalOffsetDp.toInt()} dp",
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Slider(
                        value = verticalOffsetDp,
                        onValueChange = {
                            verticalOffsetDp = it
                            automaticPosition = false
                            DuoPreferences.setVerticalOffsetDp(this@MainActivity, it)
                            DuoPreferences.setAutomaticPosition(
                                this@MainActivity,
                                false
                            )
                        },
                        valueRange = -24f..24f,
                        steps = 47,
                        enabled = !automaticPosition,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            DuoPreferences.resetPosition(this@MainActivity)
                            automaticPosition = true
                            horizontalOffsetDp = 0f
                            verticalOffsetDp = 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to Automatic")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Indicator Colors",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Leave any field empty and press SystemUI to follow the native icon color.",
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    ColorSetting(
                        title = "Battery — Normal",
                        value = batteryNormalHex,
                        onValueChange = { batteryNormalHex = it },
                        onApply = {
                            parseColorOrNull(batteryNormalHex)?.let {
                                DuoPreferences.setBatteryNormalColor(this@MainActivity, it)
                                batteryNormalHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearBatteryNormalColor(this@MainActivity)
                            batteryNormalHex = ""
                        },
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    ColorSetting(
                        title = "Battery — Charging",
                        value = batteryChargingHex,
                        onValueChange = { batteryChargingHex = it },
                        onApply = {
                            parseColorOrNull(batteryChargingHex)?.let {
                                DuoPreferences.setBatteryChargingColor(this@MainActivity, it)
                                batteryChargingHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearBatteryChargingColor(this@MainActivity)
                            batteryChargingHex = ""
                        }
                    )

                    ColorSetting(
                        title = "Battery — Low",
                        value = batteryLowHex,
                        onValueChange = { batteryLowHex = it },
                        onApply = {
                            parseColorOrNull(batteryLowHex)?.let {
                                DuoPreferences.setBatteryLowColor(this@MainActivity, it)
                                batteryLowHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearBatteryLowColor(this@MainActivity)
                            batteryLowHex = ""
                        }
                    )

                    ColorSetting(
                        title = "Battery — Power Saver",
                        value = batteryPowerSaverHex,
                        onValueChange = { batteryPowerSaverHex = it },
                        onApply = {
                            parseColorOrNull(batteryPowerSaverHex)?.let {
                                DuoPreferences.setBatteryPowerSaverColor(this@MainActivity, it)
                                batteryPowerSaverHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearBatteryPowerSaverColor(this@MainActivity)
                            batteryPowerSaverHex = ""
                        }
                    )

                    ColorSetting(
                        title = "Wi-Fi",
                        value = wifiColorHex,
                        onValueChange = { wifiColorHex = it },
                        onApply = {
                            parseColorOrNull(wifiColorHex)?.let {
                                DuoPreferences.setWifiColor(this@MainActivity, it)
                                wifiColorHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearWifiColor(this@MainActivity)
                            wifiColorHex = ""
                        }
                    )

                    ColorSetting(
                        title = "Signal",
                        value = signalColorHex,
                        onValueChange = { signalColorHex = it },
                        onApply = {
                            parseColorOrNull(signalColorHex)?.let {
                                DuoPreferences.setSignalColor(this@MainActivity, it)
                                signalColorHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearSignalColor(this@MainActivity)
                            signalColorHex = ""
                        }
                    )

                    ColorSetting(
                        title = "Network",
                        value = networkColorHex,
                        onValueChange = { networkColorHex = it },
                        onApply = {
                            parseColorOrNull(networkColorHex)?.let {
                                DuoPreferences.setNetworkColor(this@MainActivity, it)
                                networkColorHex = DuoPreferences.colorToHex(it)
                            }
                        },
                        onSystemUi = {
                            DuoPreferences.clearNetworkColor(this@MainActivity)
                            networkColorHex = ""
                        }
                    )
                }
            }

            Text(
                "Duos uses a normal application overlay for rendering. Shizuku is only the " +
                    "control layer for the SystemUI flags and overlay AppOp. If the custom " +
                    "service stops or fails, the native status bar is restored automatically."
            )
        }
    }

    @Composable
    private fun ColorSetting(
        title: String,
        value: String,
        onValueChange: (String) -> Unit,
        onApply: () -> Unit,
        onSystemUi: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier) {
            Text(title, style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("HEX") }
                )

                Column {
                    Button(
                        enabled = parseColorOrNull(value) != null,
                        onClick = onApply
                    ) {
                        Text("Apply")
                    }

                    Button(onClick = onSystemUi) {
                        Text("SystemUI")
                    }
                }
            }
        }
    }

    private fun loadPreferences() {
        batteryNormalHex =
            DuoPreferences.getBatteryNormalColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        batteryChargingHex =
            DuoPreferences.getBatteryChargingColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        batteryLowHex =
            DuoPreferences.getBatteryLowColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        batteryPowerSaverHex =
            DuoPreferences.getBatteryPowerSaverColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        wifiColorHex =
            DuoPreferences.getWifiColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        signalColorHex =
            DuoPreferences.getSignalColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        networkColorHex =
            DuoPreferences.getNetworkColorOverride(this)?.let {
                DuoPreferences.colorToHex(it)
            } ?: ""

        indicatorSizeDp = DuoPreferences.getIndicatorSizeDp(this)
        automaticPosition = DuoPreferences.isAutomaticPosition(this)
        horizontalOffsetDp = DuoPreferences.getHorizontalOffsetDp(this)
        verticalOffsetDp = DuoPreferences.getVerticalOffsetDp(this)
    }

    private fun parseColorOrNull(value: String): Int? {
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
            message.lineSequence().firstOrNull() ?: "Unknown error",
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
