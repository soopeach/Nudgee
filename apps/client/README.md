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

`parse-reminder` emits structured Supabase Edge logs for every parse using a request ID, user ID, input length, timezone, locale, and failure stage. It deliberately does **not** log reminder text. When a parse fails, the native UI shows the first eight characters of the same Reference ID; search that ID in the function logs to diagnose whether validation, credit claiming, Gemini, or response validation failed.

An AI parse credit is only charged for a successful Gemini parse. If Gemini is unavailable, the network request fails, or its response cannot be validated, the Edge Function refunds the same free or rewarded credit before returning the error. Deploy both the refund migration and `parse-reminder` after updating this code:

```bash
cd ../.. # repository root, when starting from apps/client
npx supabase db push
npx supabase functions deploy parse-reminder
```

### Android local configuration

Add these entries to the untracked `apps/client/local.properties` file, alongside `sdk.dir` if needed:

```properties
supabase.url=https://YOUR_PROJECT_REF.supabase.co
supabase.publishableKey=YOUR_SUPABASE_PUBLISHABLE_KEY
```

### Android rewarded-ad test setup

The Android debug build uses Google’s official AdMob sample App ID unless `admob.appId` is set in `local.properties`. This makes SDK integration safe to test before an AdMob account is configured.

```properties
# Replace before publishing a release build.
admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
admob.rewardedAdUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
# Debug builds only. Never commit a device ID or include it in a release build.
admob.testDeviceId=YOUR_ADMOB_TEST_DEVICE_ID
```

The Android client falls back to Google's official rewarded test unit when this value is absent. The rewarded-ad action appears in **Settings → Account → AI reminder allowance** after the daily free allowance is exhausted. Sample IDs are for SDK testing only; replace them before publishing. Release builds fail fast when either production App ID or rewarded unit ID is missing, so a release cannot accidentally ship with Google's sample IDs.

### Rewarded-credit server verification

Nudgee never trusts the Android reward callback to grant AI credits. `verify-rewarded-ad` accepts AdMob's server-side verification (SSV) callback, verifies its ECDSA signature against AdMob's rotating public keys, checks the expected reward configuration, and then writes an idempotent `admob` transaction through `grant_ai_parse_credits`.

After applying the database migration, deploy the callback and configure its production values as Supabase Edge Function secrets:

```bash
cd ../.. # repository root, when starting from apps/client
npx supabase db push
npx supabase functions deploy verify-rewarded-ad
npx supabase secrets set \
  ADMOB_REWARDED_AD_UNIT_ID="ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY" \
  ADMOB_REWARD_ITEM="nudgee_ai_credits" \
  ADMOB_REWARD_AMOUNT="5"
```

In the matching AdMob rewarded-ad unit, enable server-side verification and set this callback URL:

```text
https://YOUR_PROJECT_REF.supabase.co/functions/v1/verify-rewarded-ad
```

Set the reward item to `nudgee_ai_credits` and reward amount to `5`. The app binds the authenticated Supabase user ID and the fixed `nudgee_ai_credits_v1` context to the loaded ad; the callback rejects a different context, reward item/amount, ad unit (when configured), invalid signature, or replayed transaction. Do not point a production ad unit at the callback until the Edge Function is deployed and the three secrets match the AdMob unit.

When testing Nudgee's own rewarded unit, add a test-device ID with `admob.testDeviceId`. It is only passed to Mobile Ads in a debug build. Use `adb logcat -s NudgeeAds` to confirm `isTestDevice=true` before viewing an ad.

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
