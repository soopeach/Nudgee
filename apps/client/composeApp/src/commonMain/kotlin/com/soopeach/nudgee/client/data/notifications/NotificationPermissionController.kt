package com.soopeach.nudgee.client.data.notifications

import androidx.compose.runtime.Composable

enum class NotificationPermissionStatus {
    Enabled,
    Disabled,
    Unavailable,
}

interface NotificationPermissionController {
    suspend fun status(): NotificationPermissionStatus
    fun requestPermission()
    fun openSystemSettings()
    val supportsLocalTestNotification: Boolean
    fun sendLocalTestNotification()
}

@Composable
expect fun rememberNotificationPermissionController(): NotificationPermissionController
