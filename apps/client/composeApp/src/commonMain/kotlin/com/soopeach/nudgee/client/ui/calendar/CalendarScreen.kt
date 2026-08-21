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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.domain.task.TaskRecurrence
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSegmentedControl
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class CalendarUiState(
    val displayedMonth: LocalDate,
    val selectedDate: LocalDate,
    val taskInDetail: Task? = null,
)

private class CalendarViewModel(today: LocalDate) : ViewModel() {
    private val _state = MutableStateFlow(CalendarUiState(LocalDate(today.year, today.monthNumber, 1), today))
    val state = _state.asStateFlow()
    fun update(transform: (CalendarUiState) -> CalendarUiState) = _state.update(transform)
}

private data class CalendarTaskDetailUiState(
    val isEditing: Boolean,
    val title: String,
    val date: String,
    val time: String,
    val recurrence: TaskRecurrence,
    val error: String? = null,
    val isConfirmingDeletion: Boolean = false,
)

private class CalendarTaskDetailViewModel(task: Task) : ViewModel() {
    private val _state = MutableStateFlow(
        CalendarTaskDetailUiState(
            isEditing = false,
            title = task.title,
            date = task.dateInDeviceTimezone().toString(),
            time = task.timeIn24HourFormat(),
            recurrence = TaskRecurrence.fromRule(task.recurrenceRule),
        ),
    )
    val state = _state.asStateFlow()

    fun startEditing() = update { it.copy(isEditing = true, error = null) }
    fun cancelEditing() = update { it.copy(isEditing = false, error = null) }
    fun updateTitle(value: String) = update { it.copy(title = value, error = null) }
    fun updateDate(value: String) = update { it.copy(date = value, error = null) }
    fun updateTime(value: String) = update { it.copy(time = value, error = null) }
    fun updateRecurrence(value: TaskRecurrence) = update { it.copy(recurrence = value, error = null) }
    fun showDeleteConfirmation() = update { it.copy(isConfirmingDeletion = true) }
    fun dismissDeleteConfirmation() = update { it.copy(isConfirmingDeletion = false) }

    fun validateSave(onValid: (title: String, notifyAt: String, recurrenceRule: String?) -> Unit) {
        val current = state.value
        taskUpdateInstant(current.date, current.time)
            .onSuccess { onValid(current.title.trim(), it, current.recurrence.rule) }
            .onFailure { error -> update { it.copy(error = error.message ?: "Enter a valid date and time.") } }
    }

    private fun update(transform: (CalendarTaskDetailUiState) -> CalendarTaskDetailUiState) = _state.update(transform)
}

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    contentPadding: PaddingValues = PaddingValues(),
    onToggleTask: (Task) -> Unit = {},
    onDeleteTask: (Task) -> Unit = {},
    onUpdateTask: (Task, String, String, String?) -> Unit = { _, _, _, _ -> },
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val calendarViewModel: CalendarViewModel = viewModel { CalendarViewModel(today) }
    val state by calendarViewModel.state.collectAsState()
    val selectedTasks = tasks.filter { it.dateInDeviceTimezone() == state.selectedDate }.sortedBy { it.notifyAt }

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
                month = state.displayedMonth,
                selectedDate = state.selectedDate,
                today = today,
                tasks = tasks,
                onPreviousMonth = { calendarViewModel.update { it.copy(displayedMonth = it.displayedMonth.plus(DatePeriod(months = -1))) } },
                onNextMonth = { calendarViewModel.update { it.copy(displayedMonth = it.displayedMonth.plus(DatePeriod(months = 1))) } },
                onDateSelected = { date -> calendarViewModel.update { it.copy(selectedDate = date) } },
            )
        }
        item {
            Text(selectedDateHeading(state.selectedDate, today), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
        }
        if (selectedTasks.isEmpty()) {
            item { EmptyDayCard() }
        } else {
            items(selectedTasks, key = { it.id }) { task ->
                CalendarTaskCard(task = task, onClick = { calendarViewModel.update { it.copy(taskInDetail = task) } })
            }
        }
    }

    state.taskInDetail?.let { task ->
        CalendarTaskDetailSheet(
            task = task,
            onDismiss = { calendarViewModel.update { it.copy(taskInDetail = null) } },
            onToggleTask = {
                onToggleTask(task)
                calendarViewModel.update { it.copy(taskInDetail = null) }
            },
            onDeleteTask = {
                onDeleteTask(task)
                calendarViewModel.update { it.copy(taskInDetail = null) }
            },
            onSave = { title, notifyAt, recurrenceRule ->
                onUpdateTask(task, title, notifyAt, recurrenceRule)
                calendarViewModel.update { it.copy(taskInDetail = null) }
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
fun CalendarTaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    onToggleTask: () -> Unit,
    onDeleteTask: () -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    val detailViewModel: CalendarTaskDetailViewModel = viewModel(key = task.id) { CalendarTaskDetailViewModel(task) }
    val state by detailViewModel.state.collectAsState()

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
                text = if (state.isEditing) "Edit task" else "Task details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = NudgeeColors.ink,
            )

            if (state.isEditing) {
                NudgeeTextInput(
                    value = state.title,
                    onValueChange = detailViewModel::updateTitle,
                    placeholder = "Task title",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeTextInput(
                        value = state.date,
                        onValueChange = detailViewModel::updateDate,
                        placeholder = "YYYY-MM-DD",
                        modifier = Modifier.weight(1.35f),
                    )
                    NudgeeTextInput(
                        value = state.time,
                        onValueChange = detailViewModel::updateTime,
                        placeholder = "HH:MM",
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Does it repeat?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                NudgeeSegmentedControl(
                    options = TaskRecurrence.entries.map(TaskRecurrence::label),
                    selectedIndex = state.recurrence.ordinal,
                    onOptionSelected = { detailViewModel.updateRecurrence(TaskRecurrence.entries[it]) },
                    selectedColor = NudgeeColors.mint.copy(alpha = 0.56f),
                )
                state.error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton(
                        label = "Cancel",
                        onClick = detailViewModel::cancelEditing,
                        modifier = Modifier.weight(1f),
                        style = NudgeeButtonStyle.Secondary,
                    )
                    NudgeeButton(
                        label = "Save changes",
                        onClick = { detailViewModel.validateSave(onSave) },
                        modifier = Modifier.weight(1f),
                        enabled = state.title.isNotBlank() && state.date.isNotBlank() && state.time.isNotBlank(),
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
                if (state.isConfirmingDeletion) {
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
                            onClick = detailViewModel::dismissDeleteConfirmation,
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
                        onClick = detailViewModel::startEditing,
                        modifier = Modifier.fillMaxWidth(),
                        style = NudgeeButtonStyle.Secondary,
                    )
                    NudgeeTextButton(
                        label = "Delete task",
                        onClick = detailViewModel::showDeleteConfirmation,
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
