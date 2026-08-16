package com.soopeach.nudgee.client.data.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

@Composable
actual fun rememberNotificationPermissionController(): NotificationPermissionController = remember {
    IosNotificationPermissionController()
}

private class IosNotificationPermissionController : NotificationPermissionController {
    override suspend fun status(): NotificationPermissionStatus = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized,
                UNAuthorizationStatusProvisional,
                UNAuthorizationStatusEphemeral,
                -> NotificationPermissionStatus.Enabled
                else -> NotificationPermissionStatus.Disabled
            }
            continuation.resume(status)
        }
    }

    override fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            completionHandler = { _, _ -> },
        )
    }

    override fun openSystemSettings() {
        val notificationSettingsUrl = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)
        val appSettingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        val destination = notificationSettingsUrl ?: appSettingsUrl ?: return

        UIApplication.sharedApplication.openURL(
            url = destination,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }

    override val supportsLocalTestNotification: Boolean = false

    override fun sendLocalTestNotification() = Unit
}
