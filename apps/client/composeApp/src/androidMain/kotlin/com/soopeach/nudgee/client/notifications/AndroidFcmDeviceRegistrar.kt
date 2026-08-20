package com.soopeach.nudgee.client.notifications

import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.soopeach.nudgee.client.BuildConfig
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Registers the FCM token only after Supabase has an authenticated user.
 * `claim_device_token` also safely transfers a token after an account switch.
 */
object AndroidFcmDeviceRegistrar {
    private const val TAG = "NudgeeFcm"

    suspend fun registerCurrentToken(): Result<Unit> = runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        registerToken(token)
    }.onFailure { error -> Log.w(TAG, "FCM token registration failed.", error) }

    suspend fun registerToken(token: String) {
        val supabase = NudgeeSupabase.client ?: return
        if (supabase.auth.currentUserOrNull() == null) {
            Log.d(TAG, "Deferring FCM token registration until the user signs in.")
            return
        }

        supabase.postgrest.rpc(
            function = "claim_device_token",
            parameters = buildJsonObject {
                put("p_platform", "android")
                put("p_token", token)
                put("p_device_name", "${Build.MANUFACTURER} ${Build.MODEL}".take(120))
                // This capability marker lets the server safely choose the
                // data-only message needed for native notification actions.
                put("p_app_version", "notification-actions-v1")
            },
        )
        Log.i(TAG, "FCM token registered for the signed-in Android device.")
    }
}
