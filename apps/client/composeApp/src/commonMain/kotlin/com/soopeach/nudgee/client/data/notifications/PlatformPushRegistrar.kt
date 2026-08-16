package com.soopeach.nudgee.client.data.notifications

/** Invoked after a Supabase session is ready, never before authentication. */
expect suspend fun registerPlatformPushToken()
