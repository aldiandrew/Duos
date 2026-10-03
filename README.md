# Duos

**Duos** is a universal Android custom status-bar overlay for Android 16/AOSP. It keeps the native SystemUI clock and notification icons while replacing the native system-icon group with a compact Duo-style indicator.

The project is designed for **no-root** use. **Shizuku** is used as the privileged control layer for the SystemUI status-bar flags and the application overlay AppOp; the visual indicator itself runs as a normal Android foreground service using `TYPE_APPLICATION_OVERLAY`.

> **Status:** Active development / testing  
> **Target:** Android 16 / AOSP  
> **Minimum SDK:** Android 8.0 (API 26)  
> **Root:** Not required  
> **Shizuku:** Required for the current replacement mode

## Why Duos exists

A conventional status-bar overlay cannot safely replace Android's native battery, Wi-Fi, signal, clock, and notification indicators by itself. Duos therefore uses a hybrid architecture:

```text
Native Android SystemUI
├── Clock                 ← stays native
├── Notification icons   ← stay native
└── System icon group    ← delegated to Duos

Duos
└── Duo Canvas overlay   ← battery / Wi-Fi / cellular / state indicators

Shizuku
└── privileged control   ← SystemUI flags + overlay AppOp
```

This approach avoids rebuilding the entire status bar and reduces dependence on ROM-specific SystemUI internals.

## Current features

### Hybrid custom status bar
Duos keeps the SystemUI status-bar container visible and disables only the native system-icon group. Clock and notification icons remain native.

### Duo-style system indicators
The custom renderer can display:

- Battery ring and percentage
- Charging state
- Wi-Fi signal
- Cellular signal dots
- 2G / 3G / 4G / 5G generation
- Airplane mode
- Do Not Disturb
- VPN smart-middle state
- SystemUI light/dark foreground adaptation

### Appearance controls
Current appearance settings include:

- Indicator size: 28–60dp
- Automatic safe-area positioning
- Horizontal fine adjustment: ±24dp
- Vertical fine adjustment: ±24dp
- Reset to automatic
- Per-state/per-indicator colors
- SystemUI color fallback
- Charging pulse animation
- Visual styles:
  - Duo
  - Compact
  - Minimal
  - Ring

### Quick Settings
A Duos Quick Settings Tile can start or stop the custom status bar without opening the main application.

### Configuration backup
Duos can export, import, and reset its visual configuration.

The JSON configuration currently covers:

- Indicator size
- Position mode
- Horizontal and vertical offsets
- Visual style
- Battery colors
- Wi-Fi color
- Signal color
- Network color

## Safety and recovery

Duos follows a deliberate safety rule:

> If the custom status-bar service stops or fails to start, the native SystemUI status bar must be restored.

The service therefore attempts to restore the native status bar during startup failure and destruction.

Stopping the custom status bar also restores the native SystemUI state.

## Permissions and requirements

### Required

1. Android 8.0 / API 26 or newer
2. Shizuku installed and running
3. Shizuku permission granted to Duos
4. Display-over-other-apps AppOp, which Duos attempts to grant automatically through Shizuku

### Why Shizuku is required

The current replacement mode needs privileged SystemUI control equivalent to:

```sh
cmd statusbar send-disable-flag system-icons
```

and Duos also uses Shizuku to grant its overlay AppOp when necessary.

Without Shizuku, a normal application can render an overlay, but it cannot reliably disable the native SystemUI system-icon group on a stock Android installation.

## Architecture

### SystemUI control

`SystemBarController.kt` contains the privileged state transitions:

- `showCustomBarShell()`
  - exits demo mode
  - disables the native `system-icons` group
  - clears immersive status-bar policy so the native status-bar container remains visible
- `restore()`
  - re-enables native status-bar flags
  - removes immersive policy
  - exits demo mode

### Overlay rendering

`CustomStatusBarService.kt` is a normal Android foreground service.

It uses:

- `TYPE_APPLICATION_OVERLAY`
- `WindowInsets`
- `DisplayCutout`
- dynamic size and position updates
- Canvas-based rendering through `DuoIndicatorView`

The renderer intentionally avoids a Shizuku UserService for WindowManager operations. This keeps the overlay owned by the normal application UID instead of the shell/Shizuku service context.

### State model

`DuoStatusModel.kt` provides the state model and mapping functions used by the renderer.

The service reads best-effort device state from Android APIs such as:

- `BatteryManager`
- `ConnectivityManager`
- `WifiManager`
- `TelephonyManager`
- `NotificationManager`
- `PowerManager`
- `Settings.Global`

## Project structure

```text
app/src/main/java/com/aldiandrew/duos/
├── CustomStatusBarService.kt
├── DuoIndicatorView.kt
├── DuoPreferences.kt
├── DuoStatusModel.kt
├── DuosTileService.kt
├── MainActivity.kt
├── ShizukuManager.kt
├── ShizukuOverlayController.kt
└── SystemBarController.kt
```

Additional project files:

```text
LICENSE
THIRD_PARTY_NOTICES.md
.github/workflows/build.yml
```

## Build

Duos is built with GitHub Actions.

Current build configuration:

| Component | Version |
| --- | --- |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 26 |
| Java | 17 |
| Gradle | 9.1.0 |
| Android Gradle Plugin | 9.0.1 |
| Kotlin / Compose plugin | 2.2.10 |
| Compose BOM | 2026.06.00 |
| Shizuku API / Provider | 13.1.5 |

The workflow builds a debug APK and uploads the result as the `Duos-debug-apk` artifact.

## Third-party source and licenses

This section distinguishes **adapted source code** from **libraries used by the build/application**.

### 1. kvmy666/duoStatusBar

Repository:

https://github.com/kvmy666/duoStatusBar

License:

**GNU General Public License v3.0 (GPL-3.0)**

Duos adapts portions of the project's compact visual/state approach, especially the Canvas-based Duo indicator concepts, mappings, and related geometry/model logic.

Duos does **not** use the original project's LSPosed/Xposed/SystemUI hook architecture.

The adapted material is integrated into Duos' own `TYPE_APPLICATION_OVERLAY` architecture.

The full GPL-3.0 text is included in this repository as `LICENSE`.

See also:

- `THIRD_PARTY_NOTICES.md`
- `LICENSE`

### 2. Rikka Shizuku

Repository:

https://github.com/RikkaApps/Shizuku

Used artifacts:

- `dev.rikka.shizuku:api:13.1.5`
- `dev.rikka.shizuku:provider:13.1.5`

License:

**Apache License 2.0**

Shizuku provides the privileged IPC/control mechanism used by Duos. Duos does not bundle the Shizuku application itself.

### 3. AndroidX

AndroidX is used for the Android application and UI stack, including:

- AndroidX Core KTX
- AndroidX Activity Compose
- AndroidX Lifecycle Runtime KTX
- Jetpack Compose UI
- Jetpack Compose Material 3

License:

**Apache License 2.0**, subject to the individual component's published notices and any bundled third-party notices.

Source:

https://github.com/androidx/androidx

### 4. Android Open Source Project (AOSP)

Duos targets Android 16/AOSP and uses public Android platform APIs and SystemUI shell interfaces exposed by the platform.

Source:

https://source.android.com/

License:

AOSP contains code under the Apache License 2.0 as well as components with their own applicable licenses. Duos does **not** copy AOSP SystemUI source code into this repository; it invokes supported platform interfaces/commands through Shizuku.

### 5. Kotlin

Kotlin is used for the application source and build tooling.

Source:

https://github.com/JetBrains/kotlin

License:

**Apache License 2.0**

### 6. Gradle

Gradle is used as the build system.

Source:

https://github.com/gradle/gradle

License:

**Apache License 2.0**

## References that are not bundled source

The project has also been informed by Android documentation and by the visual behavior of other custom status-bar projects.

Those references are not represented as bundled source unless explicitly identified in the third-party section above.

In particular:

- The O.status visual concept was used as a design reference, not as bundled source or asset material.
- CleanBar-style SystemUI command sequencing was used as a behavioral reference; no CleanBar source is bundled in Duos.

## Licensing of Duos

Duos is distributed under the **GNU GPL v3.0** because the project contains adapted GPL-3.0 material from `kvmy666/duoStatusBar`.

See the full license text in [LICENSE](LICENSE).

Third-party attribution details are also available in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Disclaimer

Duos changes SystemUI state through privileged Android shell interfaces. Behavior can vary between Android versions, AOSP builds, and vendor-customized SystemUI implementations.

Always verify the restore path on the target device before relying on the application as a permanent status-bar replacement.

## Development notes

The project deliberately avoids:

- root-only requirements
- LSPosed/Xposed dependencies
- direct SystemUI source modifications
- a custom reimplementation of the entire native status bar
- Shizuku UserService WindowManager rendering

The intended architecture is:

```text
SystemUI native
      +
Duos overlay
      +
Shizuku control
```

which keeps the project modular and easier to test across Android 16 devices.
