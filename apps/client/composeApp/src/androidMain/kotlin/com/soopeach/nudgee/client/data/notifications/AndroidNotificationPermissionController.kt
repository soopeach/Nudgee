package com.soopeach.nudgee.client.data.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soopeach.nudgee.client.notifications.NudgeeNotificationPresenter

@Composable
actual fun rememberNotificationPermissionController(): NotificationPermissionController {
    val context = LocalContext.current
    val activity = context.findActivity()
    return remember(context, activity) { AndroidNotificationPermissionController(context, activity) }
}

private class AndroidNotificationPermissionController(
    private val context: Context,
    private val activity: Activity?,
) : NotificationPermissionController {
    override suspend fun status(): NotificationPermissionStatus {
        val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return if (appNotificationsEnabled && runtimePermissionGranted) {
            NotificationPermissionStatus.Enabled
        } else {
            NotificationPermissionStatus.Disabled
        }
    }

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        } else {
            openSystemSettings()
        }
    }

    override fun openSystemSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override val supportsLocalTestNotification: Boolean = true

    override fun sendLocalTestNotification() {
        NudgeeNotificationPresenter.show(
            context = context,
            title = "Nudgee reminder",
            body = "This is a local notification test.",
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 14_008
