package com.soopeach.nudgee.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.domain.task.TaskRecurrence
import com.soopeach.nudgee.client.ui.designsystem.NudgeeBackButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeRecurringDeletionDialog
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSegmentedControl
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextInput
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Composable
fun RecurringRemindersSettingsScreen(
    tasks: List<Task>,
    onBack: () -> Unit,
    onUpdate: (Task, String, String, String?) -> Unit,
    onSkip: (Task) -> Unit,
    onStop: (Task) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val recurringTasks = tasks.filter { !it.completed && it.recurrenceRule != null }.sortedBy(Task::notifyAt)
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var managingTask by remember { mutableStateOf<Task?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            kotlinx.coroutines.delay(2_800)
            feedbackMessage = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NudgeeBackButton("Settings", onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Repeating reminders", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text("Update the rhythm, skip one occurrence, or stop a reminder when it no longer fits.", style = MaterialTheme.typography.bodyLarge, color = NudgeeColors.mutedInk)
                Text("Times are shown in ${TimeZone.currentSystemDefault().id}.", style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
            }
        }
        feedbackMessage?.let { message ->
            item {
                NudgeeSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), containerColor = NudgeeColors.mint.copy(alpha = 0.34f)) {
                    Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                }
            }
        }
        if (recurringTasks.isEmpty()) {
            item {
                NudgeeSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Text("No repeating reminders yet. Choose a repeat pattern when you add a nudge and it will appear here.", modifier = Modifier.padding(22.dp), style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                }
            }
        } else {
            items(recurringTasks, key = Task::id) { task ->
                NudgeeSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                        Text("REPEATS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.mutedInk)
                        Text(task.recurringSummary(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.lavender)
                        NudgeeSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            containerColor = NudgeeColors.lavenderSurface,
                        ) {
                            Text(
                                "NEXT  ·  ${task.recurringScheduleLabel()}",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = NudgeeColors.ink,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NudgeeButton("Edit", { editingTask = task }, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                            NudgeeButton("Manage", { managingTask = task }, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                        }
                    }
                }
            }
        }
    }

    editingTask?.let { task ->
        RecurringReminderEditDialog(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { title, notifyAt, recurrenceRule ->
                onUpdate(task, title, notifyAt, recurrenceRule)
                feedbackMessage = "Reminder updated."
                editingTask = null
            },
        )
    }
    managingTask?.let { task ->
        NudgeeRecurringDeletionDialog(
            taskTitle = task.title,
            onDismiss = { managingTask = null },
            onDeleteOccurrence = { onSkip(task); feedbackMessage = "This occurrence was skipped. The next one stays scheduled."; managingTask = null },
            onStopFutureReminders = { onStop(task); feedbackMessage = "Future reminders stopped. Completed history stays here."; managingTask = null },
        )
    }
}

@Composable
private fun RecurringReminderEditDialog(task: Task, onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    val local = Instant.parse(task.notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    var title by remember(task.id) { mutableStateOf(task.title) }
    var date by remember(task.id) { mutableStateOf(local.date.toString()) }
    var time by remember(task.id) { mutableStateOf(local.time.toString().take(5)) }
    var recurrence by remember(task.id) { mutableStateOf(TaskRecurrence.fromRule(task.recurrenceRule)) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NudgeeSurface(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit repeating reminder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                NudgeeTextInput(title, { title = it; error = null }, "Task title", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeTextInput(date, { date = it; error = null }, "YYYY-MM-DD", Modifier.weight(1.35f))
                    NudgeeTextInput(time, { time = it; error = null }, "HH:MM", Modifier.weight(1f))
                }
                Text("Does it repeat?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                NudgeeSegmentedControl(TaskRecurrence.entries.map(TaskRecurrence::label), recurrence.ordinal, { recurrence = TaskRecurrence.entries[it] }, selectedColor = NudgeeColors.mint.copy(alpha = 0.56f))
                Text("After each delivery, Nudgee schedules the next task automatically.", style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk) }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton("Cancel", onDismiss, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                    NudgeeButton("Save", {
                        runCatching {
                            val result = LocalDateTime.parse("${date.trim()}T${time.trim()}").toInstant(TimeZone.currentSystemDefault())
                            require(result > Clock.System.now()) { "Choose a future date and time." }
                            result.toString()
                        }.onSuccess { onSave(title.trim(), it, recurrence.rule) }.onFailure { error = it.message ?: "Enter a valid future date and time." }
                    }, Modifier.weight(1f), enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank())
                }
            }
        }
    }
}

private fun Task.recurringScheduleLabel(): String = runCatching {
    val local = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    "${local.date} · ${local.time.toString().take(5)}"
}.getOrDefault("Reminder set")

private fun Task.recurringSummary(): String = runCatching {
    val local = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    val time = local.time.toString().take(5)
    when (TaskRecurrence.fromRule(recurrenceRule)) {
        TaskRecurrence.Daily -> "Every day at $time"
        TaskRecurrence.Weekdays -> "Every weekday at $time"
        TaskRecurrence.Weekends -> "Every weekend at $time"
        TaskRecurrence.Weekly -> "Every ${local.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} at $time"
        TaskRecurrence.DoesNotRepeat -> "One-time reminder"
    }
}.getOrDefault("Repeating reminder")
