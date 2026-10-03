# Third-Party Notices

This file records third-party source projects, libraries, platform technologies, and build tooling used by Duos.

## 1. kvmy666/duoStatusBar

Repository:

https://github.com/kvmy666/duoStatusBar

License: **GNU General Public License v3.0 (GPL-3.0)**

Duos adapts portions of the project's compact Duo visual/state approach, including relevant Canvas-rendering concepts, mappings, and geometry.

The original project uses LSPosed/Xposed/SystemUI hooks. Duos does not use that hook architecture; the adapted material is integrated into Duos' application-overlay architecture.

The repository's GPL-3.0 license is included as `LICENSE`.

## 2. Rikka Shizuku

Repository:

https://github.com/RikkaApps/Shizuku

Artifacts used by Duos:

- `dev.rikka.shizuku:api:13.1.5`
- `dev.rikka.shizuku:provider:13.1.5`

License: **Apache License 2.0**

Shizuku is used as Duos' privileged control layer. The Shizuku application itself is not bundled with Duos.

## 3. AndroidX / Jetpack Compose

Repository:

https://github.com/androidx/androidx

Libraries used by Duos include:

- AndroidX Core KTX 1.17.0
- AndroidX Activity Compose 1.13.0
- AndroidX Lifecycle Runtime KTX 2.10.0
- Jetpack Compose BOM 2026.06.00
- Jetpack Compose UI
- Jetpack Compose Material 3

License: **Apache License 2.0**, subject to the notices applicable to the individual distributed components and any bundled third-party material.

## 4. Android Open Source Project (AOSP)

Source:

https://source.android.com/

Duos targets Android 16/AOSP and uses Android platform APIs plus the Android status-bar shell mechanisms available on the target system.

Duos does not copy AOSP SystemUI source code into this repository.

AOSP is a large collection of components and does not have a single license covering every file; individual components may carry their own applicable licenses. Users redistributing AOSP-derived material should consult the notices of the specific components involved.

## 5. Kotlin

Repository:

https://github.com/JetBrains/kotlin

Kotlin is used for the Duos source code and Kotlin/Compose build integration.

License: **Apache License 2.0**

## 6. Gradle

Repository:

https://github.com/gradle/gradle

Gradle is used as the project's build system.

Version used by CI: **9.1.0**

License: **Apache License 2.0**

## 7. Android Gradle Plugin

The Android Gradle Plugin is used to build the Android application.

Version used by the project: **9.0.1**

The Android Gradle Plugin is distributed under the **Apache License 2.0**. Its own distribution and notices govern the tool itself; it is build tooling and is not bundled as application source inside the Duos APK.

## 8. GitHub Actions build tooling

The project uses GitHub-hosted Actions for CI, including:

- `actions/checkout@v5`
- `actions/setup-java@v5`
- `android-actions/setup-android@v4`
- `gradle/actions/setup-gradle@v5`
- `actions/upload-artifact@v4`

These are build-time services/actions and are not bundled into the Duos application. Their respective repositories and licenses apply to the actions themselves.

## Design / behavioral references

The following projects or concepts informed the design but are **not bundled as source or assets**:

### O.status

Used only as a visual/interaction reference. No O.status source code or assets are included in Duos.

### CleanBar

Used as a behavioral reference for SystemUI shell-command sequencing. No CleanBar source code is included in Duos.

## Relationship to Duos

The presence of a third-party project or library in this notice does not imply endorsement by its authors.

Where a third-party license applies to adapted or included material, that license remains applicable to the relevant material. The main Duos repository is distributed under GPL-3.0; see `LICENSE` for the complete license text.

For a concise project-level overview, see `README.md`.

