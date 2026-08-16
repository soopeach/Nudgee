package com.soopeach.nudgee.client.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * The native client only has the public Supabase key. Gemini remains exclusively
 * inside the authenticated parse-reminder Edge Function.
 */
object NudgeeSupabase {
    private val config = platformSupabaseConfig()

    val client: SupabaseClient? = config.takeIf(SupabaseConfig::isConfigured)?.let {
        createSupabaseClient(
            supabaseUrl = it.url,
            supabaseKey = it.publishableKey,
        ) {
            install(Auth) {
                configurePlatformAuthRedirect()
            }
            install(Functions)
            install(Postgrest)
            install(Realtime)
        }
    }

    val isConfigured: Boolean
        get() = client != null
}
