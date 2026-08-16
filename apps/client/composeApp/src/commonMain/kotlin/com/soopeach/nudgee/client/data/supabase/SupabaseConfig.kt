package com.soopeach.nudgee.client.data.supabase

data class SupabaseConfig(
    val url: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean
        get() = url.startsWith("https://") && publishableKey.isNotBlank()
}

expect fun platformSupabaseConfig(): SupabaseConfig
