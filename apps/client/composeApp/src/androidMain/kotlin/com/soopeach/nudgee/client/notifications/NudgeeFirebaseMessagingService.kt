package com.soopeach.nudgee.client.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.soopeach.nudgee.client.MainActivity
import com.soopeach.nudgee.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NudgeeFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch { AndroidFcmDeviceRegistrar.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        NudgeeNotificationPresenter.show(
            context = this,
            title = message.notification?.title ?: "Nudgee reminder",
            body = message.notification?.body ?: message.data["title"] ?: "You have a reminder.",
            taskId = message.data["taskId"],
            actionToken = message.data["actionToken"],
        )
    }

    private companion object {
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

object NudgeeNotificationPresenter {
    private const val CHANNEL_ID = "nudgee_reminders"
    private const val CHANNEL_NAME = "Nudgee reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Task reminders from Nudgee"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        taskId: String? = null,
        actionToken: String? = null,
    ) {
        ensureChannel(context)
        val notificationId = ("$taskId:$actionToken".hashCode() and Int.MAX_VALUE)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        if (!taskId.isNullOrBlank() && !actionToken.isNullOrBlank()) {
            builder
                .addAction(
                    android.R.drawable.ic_popup_sync,
                    "Snooze 10m",
                    reminderActionPendingIntent(context, notificationId, taskId, actionToken, NudgeeReminderActionReceiver.ACTION_SNOOZE),
                )
                .addAction(
                    android.R.drawable.checkbox_on_background,
                    "Complete",
                    reminderActionPendingIntent(context, notificationId, taskId, actionToken, NudgeeReminderActionReceiver.ACTION_COMPLETE),
                )
        }
        val notification = builder.build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    private fun reminderActionPendingIntent(
        context: Context,
        notificationId: Int,
        taskId: String,
        actionToken: String,
        action: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        "$notificationId:$action".hashCode(),
        Intent(context, NudgeeReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(NudgeeReminderActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(NudgeeReminderActionReceiver.EXTRA_ACTION_TOKEN, actionToken)
            putExtra(NudgeeReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
