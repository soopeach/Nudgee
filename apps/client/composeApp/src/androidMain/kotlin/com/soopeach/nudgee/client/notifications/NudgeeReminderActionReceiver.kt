package com.soopeach.nudgee.client.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.soopeach.nudgee.client.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Executes a server-owned reminder action from Android notification buttons. */
class NudgeeReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_SNOOZE -> "snooze"
            ACTION_COMPLETE -> "complete"
            else -> return
        }
        val actionToken = intent.getStringExtra(EXTRA_ACTION_TOKEN) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val result = postAction(action, actionToken)
                if (result.success && notificationId >= 0) {
                    context.getSystemService(NotificationManager::class.java).cancel(notificationId)
                    Log.i(TAG, "Reminder $action action completed.")
                } else {
                    Log.w(TAG, "Reminder $action action failed: HTTP ${result.statusCode}.")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Reminder $action action could not be sent.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postAction(action: String, actionToken: String): ActionResult {
        val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/reminder-action").openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.outputStream.bufferedWriter().use {
                it.write(JSONObject().put("action", action).put("actionToken", actionToken).toString())
            }
            ActionResult(connection.responseCode in 200..299, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private data class ActionResult(val success: Boolean, val statusCode: Int)

    companion object {
        const val TAG = "NudgeeReminderAction"
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val ACTION_SNOOZE = "com.soopeach.nudgee.REMINDER_SNOOZE"
        const val ACTION_COMPLETE = "com.soopeach.nudgee.REMINDER_COMPLETE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_ACTION_TOKEN = "action_token"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
