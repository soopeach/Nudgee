package com.soopeach.nudgee.client.data.supabase

import com.soopeach.nudgee.client.BuildConfig

actual fun platformSupabaseConfig(): SupabaseConfig = SupabaseConfig(
    url = BuildConfig.SUPABASE_URL,
    publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
)
