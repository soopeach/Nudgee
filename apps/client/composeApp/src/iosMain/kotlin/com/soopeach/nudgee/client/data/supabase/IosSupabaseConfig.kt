package com.soopeach.nudgee.client.data.supabase

import platform.Foundation.NSBundle

actual fun platformSupabaseConfig(): SupabaseConfig = SupabaseConfig(
    url = NSBundle.mainBundle.objectForInfoDictionaryKey("SupabaseUrl") as? String ?: "",
    publishableKey = NSBundle.mainBundle.objectForInfoDictionaryKey("SupabasePublishableKey") as? String ?: "",
)
