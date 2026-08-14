# Nudgee Compose Multiplatform client

This module is the native client for Android, iOS, Windows, and macOS. The React app remains under `apps/web`.

## Targets

- Android: `androidTarget`
- iOS: `iosX64`, `iosArm64`, `iosSimulatorArm64`
- Desktop: JVM target with DMG, MSI, and DEB distributions

## Open the project

Open `apps/client` in Android Studio or IntelliJ IDEA with the Kotlin Multiplatform plugin. Gradle will download the Android, Kotlin, and Compose dependencies.

For iOS, use a macOS machine with Xcode installed. Open `iosApp/iosApp.xcodeproj`; its build phase invokes the Gradle wrapper to build and embed the shared `ComposeApp` framework, and `MainViewController()` is the shared Compose entry point.

## Planned platform adapters

- `commonMain`: shared UI, task state, Supabase API, and authentication contracts
- `androidMain`: Firebase Cloud Messaging and Android notification APIs
- `iosMain`: APNs registration and iOS notification APIs
- `desktopMain`: Windows/macOS notification and tray/menu-bar integrations

Notification scheduling remains server-owned. This client should register device targets and consume realtime task updates; it must not schedule reminders with local timers.
