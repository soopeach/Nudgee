package com.soopeach.nudgee.client.notifications

import com.soopeach.nudgee.client.domain.task.Task

/**
 * Presents only server-dispatched reminders while the desktop app is running.
 * It deliberately owns no reminder timing; the scheduler remains the source
 * of truth and TaskListViewModel invokes this after its Realtime state changes to
 * `notification_state = sent`.
 */
object DesktopActiveReminderPresenter {
    fun show(task: Task) {
        show(title = "Nudgee reminder", body = task.title)
    }

    fun showTestNotification() {
        show(title = "Nudgee reminder", body = "This is a local notification test.")
    }

    private fun show(title: String, body: String) {
        if (!isMacOs()) return

        runCatching {
            ProcessBuilder(
                "osascript",
                "-e",
                "display notification \"${body.appleScriptLiteral()}\" with title \"${title.appleScriptLiteral()}\"",
            ).start()
        }
    }

    private fun isMacOs(): Boolean = System.getProperty("os.name").contains("mac", ignoreCase = true)
}

private fun String.appleScriptLiteral(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", " ")
    .replace("\n", " ")
