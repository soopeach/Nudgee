package com.soopeach.nudgee.client.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextInput
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    contentPadding: PaddingValues = PaddingValues(),
    onToggleTask: (Task) -> Unit = {},
    onDeleteTask: (Task) -> Unit = {},
    onUpdateTask: (Task, String, String) -> Unit = { _, _, _ -> },
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var displayedMonth by remember { mutableStateOf(LocalDate(today.year, today.monthNumber, 1)) }
    var selectedDate by remember { mutableStateOf(today) }
    var taskInDetail by remember { mutableStateOf<Task?>(null) }
    val selectedTasks = tasks.filter { it.dateInDeviceTimezone() == selectedDate }.sortedBy { it.notifyAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Calendar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
            Spacer(Modifier.height(4.dp))
            Text("See what you’ve finished and what’s next.", style = MaterialTheme.typography.bodyLarge, color = NudgeeColors.mutedInk)
        }
        item {
            CalendarMonth(
                month = displayedMonth,
                selectedDate = selectedDate,
                today = today,
                tasks = tasks,
                onPreviousMonth = { displayedMonth = displayedMonth.plus(DatePeriod(months = -1)) },
                onNextMonth = { displayedMonth = displayedMonth.plus(DatePeriod(months = 1)) },
                onDateSelected = { selectedDate = it },
            )
        }
        item {
            Text(selectedDateHeading(selectedDate, today), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
        }
        if (selectedTasks.isEmpty()) {
            item { EmptyDayCard() }
        } else {
            items(selectedTasks, key = { it.id }) { task ->
                CalendarTaskCard(task = task, onClick = { taskInDetail = task })
            }
        }
    }

    taskInDetail?.let { task ->
        CalendarTaskDetailSheet(
            task = task,
            onDismiss = { taskInDetail = null },
            onToggleTask = {
                onToggleTask(task)
                taskInDetail = null
            },
            onDeleteTask = {
                onDeleteTask(task)
                taskInDetail = null
            },
            onSave = { title, notifyAt ->
                onUpdateTask(task, title, notifyAt)
                taskInDetail = null
            },
        )
    }
}

@Composable
private fun CalendarMonth(
    month: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    tasks: List<Task>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val daysInMonth = daysInMonth(month.year, month.monthNumber)
    val leadingEmptyDays = month.dayOfWeek.ordinal
    NudgeeSurface(
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MonthButton("‹", onPreviousMonth)
                Text(monthName(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                MonthButton("›", onNextMonth)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.width(40.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = NudgeeColors.mutedInk,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            val cells = (0 until leadingEmptyDays).map { null } + (1..daysInMonth).map { day -> month.plus(DatePeriod(days = day - 1)) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    week.forEach { date ->
                        if (date == null) Spacer(Modifier.width(40.dp).height(42.dp))
                        else CalendarDay(
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            tasks = tasks.filter { it.dateInDeviceTimezone() == date },
                            onClick = { onDateSelected(date) },
                        )
                    }
                    repeat(7 - week.size) { Spacer(Modifier.width(40.dp).height(42.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MonthButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            color = NudgeeColors.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CalendarDay(date: LocalDate, isSelected: Boolean, isToday: Boolean, tasks: List<Task>, onClick: () -> Unit) {
    val dotColor = when {
        tasks.any { !it.completed } -> NudgeeColors.sky
        tasks.isNotEmpty() -> NudgeeColors.mint
        else -> null
    }
    Column(
        modifier = Modifier
            .width(40.dp)
            .height(42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .background(
                    if (isSelected) NudgeeColors.lavender.copy(alpha = 0.16f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (isSelected) NudgeeColors.lavender else NudgeeColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        dotColor?.let { Box(Modifier.size(5.dp).background(it, CircleShape)) }
    }
}

@Composable
private fun CalendarTaskCard(task: Task, onClick: () -> Unit) {
    NudgeeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (task.completed) NudgeeColors.mint else NudgeeColors.sky, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (task.completed) NudgeeColors.mutedInk else NudgeeColors.ink, textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None)
                Text(if (task.completed) "Completed" else task.timeInDeviceTimezone(), style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    onToggleTask: () -> Unit,
    onDeleteTask: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var isEditing by remember(task.id) { mutableStateOf(false) }
    var title by remember(task.id) { mutableStateOf(task.title) }
    var date by remember(task.id) { mutableStateOf(task.dateInDeviceTimezone().toString()) }
    var time by remember(task.id) { mutableStateOf(task.timeIn24HourFormat()) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }
    var isConfirmingDeletion by remember(task.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NudgeeColors.softSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 42.dp, height = 4.dp).background(NudgeeColors.line, CircleShape))
            }
            Text(
                text = if (isEditing) "Edit task" else "Task details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = NudgeeColors.ink,
            )

            if (isEditing) {
                NudgeeTextInput(
                    value = title,
                    onValueChange = { title = it; error = null },
                    placeholder = "Task title",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeTextInput(
                        value = date,
                        onValueChange = { date = it; error = null },
                        placeholder = "YYYY-MM-DD",
                        modifier = Modifier.weight(1.35f),
                    )
                    NudgeeTextInput(
                        value = time,
                        onValueChange = { time = it; error = null },
                        placeholder = "HH:MM",
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton(
                        label = "Cancel",
                        onClick = { isEditing = false; error = null },
                        modifier = Modifier.weight(1f),
                        style = NudgeeButtonStyle.Secondary,
                    )
                    NudgeeButton(
                        label = "Save changes",
                        onClick = {
                            taskUpdateInstant(date, time)
                                .onSuccess { onSave(title.trim(), it) }
                                .onFailure { error = it.message ?: "Enter a valid date and time." }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank(),
                    )
                }
            } else {
                NudgeeSurface(shape = RoundedCornerShape(22.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = task.title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NudgeeColors.ink,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            text = "${selectedDateHeading(task.dateInDeviceTimezone(), Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)} · ${task.timeInDeviceTimezone()}",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NudgeeColors.mutedInk,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            text = if (task.completed) "Completed" else "Still to do",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (task.completed) NudgeeColors.mutedInk else NudgeeColors.lavender,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                if (isConfirmingDeletion) {
                    NudgeeSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = NudgeeColors.lavenderSurface,
                    ) {
                        Text(
                            text = "Delete this task from all your Nudgee devices?",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NudgeeColors.ink,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NudgeeButton(
                            label = "Keep task",
                            onClick = { isConfirmingDeletion = false },
                            modifier = Modifier.weight(1f),
                            style = NudgeeButtonStyle.Secondary,
                        )
                        NudgeeButton(
                            label = "Delete",
                            onClick = onDeleteTask,
                            modifier = Modifier.weight(1f),
                            style = NudgeeButtonStyle.Dark,
                        )
                    }
                } else {
                    NudgeeButton(
                        label = if (task.completed) "Mark as to do" else "Mark complete",
                        onClick = onToggleTask,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NudgeeButton(
                        label = "Edit task",
                        onClick = { isEditing = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = NudgeeButtonStyle.Secondary,
                    )
                    NudgeeTextButton(
                        label = "Delete task",
                        onClick = { isConfirmingDeletion = true },
                        modifier = Modifier.fillMaxWidth(),
                        color = NudgeeColors.mutedInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDayCard() {
    NudgeeSurface(
        shape = RoundedCornerShape(24.dp),
    ) {
        Text("A quiet day — add a nudge when you’re ready.", modifier = Modifier.padding(22.dp), style = MaterialTheme.typography.bodyLarge, color = NudgeeColors.mutedInk)
    }
}

private fun Task.dateInDeviceTimezone(): LocalDate = kotlinx.datetime.Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun Task.timeInDeviceTimezone(): String {
    val local = kotlinx.datetime.Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = (local.hour % 12).takeIf { it != 0 } ?: 12
    val minute = local.minute.toString().padStart(2, '0')
    return "$hour:$minute ${if (local.hour < 12) "AM" else "PM"}"
}

private fun Task.timeIn24HourFormat(): String = kotlinx.datetime.Instant.parse(notifyAt)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .time
    .toString()
    .take(5)

private fun taskUpdateInstant(date: String, time: String): Result<String> = runCatching {
    LocalDateTime.parse("${date.trim()}T${time.trim()}")
        .toInstant(TimeZone.currentSystemDefault())
        .toString()
}

private fun selectedDateHeading(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plus(DatePeriod(days = 1)) -> "Tomorrow"
    else -> "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}"
}

private fun monthName(month: LocalDate): String = "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}"

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 31
}
