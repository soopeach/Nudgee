package com.soopeach.nudgee.client.data.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.soopeach.nudgee.client.notifications.DesktopActiveReminderPresenter

@Composable
actual fun rememberNotificationPermissionController(): NotificationPermissionController = remember {
    object : NotificationPermissionController {
        override suspend fun status() = NotificationPermissionStatus.Unavailable
        override fun requestPermission() = openSystemSettings()
        override fun openSystemSettings() {
            runCatching {
                ProcessBuilder(
                    "open",
                    "x-apple.systempreferences:com.apple.preference.notifications",
                ).start()
            }
        }

        override val supportsLocalTestNotification: Boolean = true

        override fun sendLocalTestNotification() {
            DesktopActiveReminderPresenter.showTestNotification()
        }
    }
}
