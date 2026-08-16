package com.soopeach.nudgee.client.data.notifications

/** APNs/FCM registration is intentionally implemented separately from Android. */
actual suspend fun registerPlatformPushToken() = Unit
