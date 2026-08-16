package com.soopeach.nudgee.client.data.supabase

actual fun platformSupabaseConfig(): SupabaseConfig = SupabaseConfig(
    url = System.getenv("NUDGEE_SUPABASE_URL").orEmpty(),
    publishableKey = System.getenv("NUDGEE_SUPABASE_PUBLISHABLE_KEY").orEmpty(),
)
