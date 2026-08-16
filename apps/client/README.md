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

## Supabase and Gemini reminder parsing

Natural-language reminders use the existing authenticated `parse-reminder` Supabase Edge Function. The native app never receives `GEMINI_API_KEY` or a Supabase service-role key.

### Android local configuration

Add these entries to the untracked `apps/client/local.properties` file, alongside `sdk.dir` if needed:

```properties
supabase.url=https://YOUR_PROJECT_REF.supabase.co
supabase.publishableKey=YOUR_SUPABASE_PUBLISHABLE_KEY
```

### Android FCM

1. Register Android package `com.soopeach.nudgee.client` in the existing Firebase project.
2. Download its `google-services.json` to `apps/client/composeApp/google-services.json`. The file is intentionally git-ignored.
3. On Android 13+, grant the notification permission when Nudgee requests it.

After a Google-authenticated Supabase session is available, the app obtains the FCM token and calls the database's `claim_device_token` RPC with platform `android`. Token refreshes use the same ownership-safe path. The deployed `dispatch-notifications` Edge Function sends Android tokens through FCM on the `nudgee_reminders` notification channel.

### iOS local configuration

For a local run, set `SupabaseUrl` and `SupabasePublishableKey` in `iosApp/iosApp/Info.plist`. Both values are public client configuration values; do not put a service-role key or Gemini API key in this project.

### Desktop local configuration

Desktop reads its public Supabase configuration from environment variables:

```bash
cd apps/client
NUDGEE_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co \
NUDGEE_SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY \
./gradlew :composeApp:run
```

The Desktop target has a dedicated sidebar workspace with task CRUD, shared Supabase Realtime storage, and Google sign-in. It opens the browser for Google OAuth, then receives the completed session through Supabase-KT's local loopback callback server. Desktop push delivery is not implemented yet.

### OAuth redirect setup

Android and iOS use the `nudgee://auth` callback URL. Desktop uses a local loopback callback with an available port. Add **both** entries to **Supabase Dashboard → Authentication → URL Configuration → Redirect URLs**:

```text
nudgee://auth
http://localhost:**
```

Android and iOS are already registered to receive the `nudgee` URL scheme in their app manifests. The Desktop loopback pattern belongs in Supabase's redirect allow-list only; Google OAuth should continue to use the existing Supabase callback configured in Google Cloud.

## Task data and Realtime

After login, the native home reads and writes the existing `public.tasks` table through Supabase RLS. It fetches the current task list on entry and subscribes to the `public.tasks` Realtime publication, so changes from the web app or another client are reflected in the native UI. The table must remain enabled in the `supabase_realtime` publication; the repository's schema SQL already does this.
