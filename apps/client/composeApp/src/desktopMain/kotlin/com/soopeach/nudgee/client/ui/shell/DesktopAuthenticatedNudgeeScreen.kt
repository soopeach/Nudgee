package com.soopeach.nudgee.client.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollbarAdapter
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import com.soopeach.nudgee.client.domain.reminder.AuthenticationRequiredException
import com.soopeach.nudgee.client.domain.reminder.ClarificationType
import com.soopeach.nudgee.client.domain.reminder.ParsedReminderDraft
import com.soopeach.nudgee.client.domain.reminder.SupabaseEdgeFunctionReminderParser
import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.domain.task.TaskRecurrence
import com.soopeach.nudgee.client.domain.task.TaskRepository
import com.soopeach.nudgee.client.domain.task.recurrenceLabel
import com.soopeach.nudgee.client.notifications.DesktopActiveReminderPresenter
import com.soopeach.nudgee.client.ui.calendar.CalendarScreen
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSegmentedControl
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextInput
import com.soopeach.nudgee.client.ui.designsystem.NudgeeRecurringDeletionDialog
import com.soopeach.nudgee.client.ui.home.TaskListViewModel
import com.soopeach.nudgee.client.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private enum class DesktopDestination(val label: String) {
    Home("Home"),
    Calendar("Calendar"),
    Settings("Settings"),
}

private enum class DesktopTaskTimeWindow(val label: String, val sectionTitle: String) {
    Today("Today", "Today's nudges"),
    SevenDays("7 days", "Next 7 days"),
    ThirtyDays("30 days", "Next 30 days"),
    AllTime("All", "All nudges"),
}

private enum class DesktopTaskStatusFilter(val label: String, val completed: Boolean) {
    ToDo("To do", completed = false),
    Completed("Completed", completed = true),
}

private enum class DesktopReminderInputMode(val label: String) {
    NaturalLanguage("Natural language"),
    Manual("Set manually"),
}

private enum class DesktopManualFocusTarget { Time, Recurrence }

private data class DesktopShellUiState(
    val destination: DesktopDestination = DesktopDestination.Home,
    val isComposerVisible: Boolean = false,
    val taskBeingEdited: Task? = null,
    val taskPendingDeletion: Task? = null,
    val selectedWindow: DesktopTaskTimeWindow = DesktopTaskTimeWindow.Today,
    val selectedStatus: DesktopTaskStatusFilter = DesktopTaskStatusFilter.ToDo,
)

private class DesktopShellViewModel : ViewModel() {
    private val _state = MutableStateFlow(DesktopShellUiState())
    val state = _state.asStateFlow()
    fun update(transform: (DesktopShellUiState) -> DesktopShellUiState) = _state.update(transform)
}

@Composable
actual fun AuthenticatedNudgeeScreen(
    repository: TaskRepository,
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
) {
    val store: TaskListViewModel = viewModel { TaskListViewModel(repository) }
    val taskState by store.state.collectAsState()
    val shellViewModel: DesktopShellViewModel = viewModel { DesktopShellViewModel() }
    val shellState by shellViewModel.state.collectAsState()
    val reminderParser = NudgeeSupabase.client?.let(::SupabaseEdgeFunctionReminderParser)

    LaunchedEffect(store) {
        store.serverDispatchedTasks.collect(DesktopActiveReminderPresenter::show)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NudgeeColors.softSurface),
    ) {
        DesktopSidebar(
            destination = shellState.destination,
            email = email,
            onDestinationSelected = { destination -> shellViewModel.update { it.copy(destination = destination) } },
            onAddTask = { shellViewModel.update { it.copy(isComposerVisible = true) } },
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(NudgeeColors.line),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (shellState.destination) {
                DesktopDestination.Home -> DesktopHomeWorkspace(
                    tasks = taskState.tasks,
                    isLoading = taskState.isLoading,
                    error = taskState.error,
                    selectedWindow = shellState.selectedWindow,
                    selectedStatus = shellState.selectedStatus,
                    onWindowSelected = { window -> shellViewModel.update { it.copy(selectedWindow = window) } },
                    onStatusSelected = { status -> shellViewModel.update { it.copy(selectedStatus = status) } },
                    onAddTask = { shellViewModel.update { it.copy(isComposerVisible = true) } },
                    onToggleTask = store::toggleTask,
                    onDeleteTask = { task -> shellViewModel.update { it.copy(taskPendingDeletion = task) } },
                    onEditTask = { task -> shellViewModel.update { it.copy(taskBeingEdited = task) } },
                )
                DesktopDestination.Calendar -> CalendarScreen(
                    tasks = taskState.tasks,
                    contentPadding = PaddingValues(28.dp),
                    onToggleTask = store::toggleTask,
                    onDeleteTask = { task -> shellViewModel.update { it.copy(taskPendingDeletion = task) } },
                    onUpdateTask = store::updateTask,
                )
                DesktopDestination.Settings -> SettingsScreen(
                    email = email,
                    avatarUrl = avatarUrl,
                    onSignOut = onSignOut,
                    tasks = taskState.tasks,
                    onUpdateRecurringReminder = store::updateTask,
                    onSkipRecurringOccurrence = store::skipRecurringOccurrence,
                    onStopRecurringReminder = store::stopRecurringReminder,
                    contentPadding = PaddingValues(28.dp),
                )
            }
        }
    }

    if (shellState.isComposerVisible) {
        DesktopTaskComposerDialog(
            parser = reminderParser,
            onDismiss = { shellViewModel.update { it.copy(isComposerVisible = false) } },
            onAddTask = { title, notifyAt, recurrenceRule ->
                store.addTask(title, notifyAt, recurrenceRule)
                shellViewModel.update { it.copy(isComposerVisible = false) }
            },
        )
    }

    shellState.taskBeingEdited?.let { task ->
        DesktopTaskEditDialog(
            task = task,
            onDismiss = { shellViewModel.update { it.copy(taskBeingEdited = null) } },
            onSave = { title, notifyAt, recurrenceRule ->
                store.updateTask(task, title, notifyAt, recurrenceRule)
                shellViewModel.update { it.copy(taskBeingEdited = null) }
            },
        )
    }

    shellState.taskPendingDeletion?.let { task ->
        if (task.recurrenceRule != null) {
            NudgeeRecurringDeletionDialog(
                taskTitle = task.title,
                onDismiss = { shellViewModel.update { it.copy(taskPendingDeletion = null) } },
                onDeleteOccurrence = {
                    store.skipRecurringOccurrence(task)
                    shellViewModel.update { it.copy(taskPendingDeletion = null) }
                },
                onStopFutureReminders = {
                    store.stopRecurringReminder(task)
                    shellViewModel.update { it.copy(taskPendingDeletion = null) }
                },
            )
        } else {
            DesktopDeleteConfirmationDialog(
                task = task,
                onDismiss = { shellViewModel.update { it.copy(taskPendingDeletion = null) } },
                onConfirm = {
                    store.deleteTask(task)
                    shellViewModel.update { it.copy(taskPendingDeletion = null) }
                },
            )
        }
    }
}

@Composable
private fun DesktopSidebar(
    destination: DesktopDestination,
    email: String?,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onAddTask: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(248.dp)
            .fillMaxHeight()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Nudgee", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
        Text("A gentle nudge, everywhere.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
        Spacer(Modifier.height(20.dp))
        DesktopDestination.entries.forEach { item ->
            val selected = item == destination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) NudgeeColors.lavenderSurface else Color.Transparent)
                    .clickable { onDestinationSelected(item) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(if (selected) NudgeeColors.lavender else NudgeeColors.line, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) NudgeeColors.ink else NudgeeColors.mutedInk,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        NudgeeButton(label = "Add a nudge", onClick = onAddTask, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        NudgeeSurface(shape = RoundedCornerShape(18.dp), containerColor = Color.White) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Signed in", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.mutedInk)
                Text(email ?: "Nudgee account", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = NudgeeColors.ink)
            }
        }
    }
}

@Composable
private fun DesktopHomeWorkspace(
    tasks: List<Task>,
    isLoading: Boolean,
    error: String?,
    selectedWindow: DesktopTaskTimeWindow,
    selectedStatus: DesktopTaskStatusFilter,
    onWindowSelected: (DesktopTaskTimeWindow) -> Unit,
    onStatusSelected: (DesktopTaskStatusFilter) -> Unit,
    onAddTask: () -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val todayTasks = tasks.filter { it.localDate() == today }
    val completedToday = todayTasks.count(Task::completed)
    val listState = rememberLazyListState()
    val visibleTasks = tasks
        .filter { it.completed == selectedStatus.completed && it.isInWindow(selectedWindow) }
        .sortedBy(Task::notifyAt)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                        Text(
                            if (todayTasks.isEmpty()) "A clear slate for today." else "$completedToday of ${todayTasks.size} nudges complete.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NudgeeColors.mutedInk,
                        )
                    }
                    NudgeeButton(label = "Add a nudge", onClick = onAddTask, modifier = Modifier.widthIn(min = 160.dp))
                }
            }
            error?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk) }
            }
            item { DesktopRhythmCard(completed = completedToday, total = todayTasks.size) }
            item {
                NudgeeSegmentedControl(
                    options = DesktopTaskTimeWindow.entries.map(DesktopTaskTimeWindow::label),
                    selectedIndex = selectedWindow.ordinal,
                    onOptionSelected = { onWindowSelected(DesktopTaskTimeWindow.entries[it]) },
                )
            }
            item {
                NudgeeSegmentedControl(
                    options = DesktopTaskStatusFilter.entries.map(DesktopTaskStatusFilter::label),
                    selectedIndex = selectedStatus.ordinal,
                    onOptionSelected = { onStatusSelected(DesktopTaskStatusFilter.entries[it]) },
                    selectedColor = NudgeeColors.mint,
                )
            }
            item {
                Text(
                    text = if (selectedStatus == DesktopTaskStatusFilter.ToDo) selectedWindow.sectionTitle else "Completed · ${selectedWindow.label}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = NudgeeColors.ink,
                )
            }
            when {
                isLoading -> item { Text("Loading your nudges…", color = NudgeeColors.mutedInk) }
                visibleTasks.isEmpty() -> item { DesktopEmptyState(selectedStatus, selectedWindow, onAddTask) }
                else -> items(visibleTasks, key = Task::id) { task ->
                    DesktopTaskRow(
                        task = task,
                        onToggleTask = { onToggleTask(task) },
                        onEditTask = { onEditTask(task) },
                        onDeleteTask = { onDeleteTask(task) },
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 6.dp),
        )
    }
}

@Composable
private fun DesktopRhythmCard(completed: Int, total: Int) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    NudgeeSurface(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Today’s rhythm", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.mutedInk)
            Text("$completed of $total complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(NudgeeColors.progressTrack)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(NudgeeColors.lavender, CircleShape))
            }
        }
    }
}

@Composable
private fun DesktopTaskRow(task: Task, onToggleTask: () -> Unit, onEditTask: () -> Unit, onDeleteTask: () -> Unit) {
    NudgeeSurface(shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(if (task.completed) NudgeeColors.mint else NudgeeColors.sky, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (task.completed) NudgeeColors.mutedInk else NudgeeColors.ink,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(listOfNotNull(task.desktopReminderLabel(), task.recurrenceLabel()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
            }
            NudgeeTextButton(label = if (task.completed) "Reopen" else "Complete", onClick = onToggleTask)
            NudgeeTextButton(label = "Edit", onClick = onEditTask)
            NudgeeTextButton(label = "Delete", onClick = onDeleteTask, color = NudgeeColors.mutedInk)
        }
    }
}

@Composable
private fun DesktopEmptyState(
    status: DesktopTaskStatusFilter,
    window: DesktopTaskTimeWindow,
    onAddTask: () -> Unit,
) {
    NudgeeSurface(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("✦", style = MaterialTheme.typography.headlineMedium, color = NudgeeColors.lavender)
            Text(
                when {
                    status == DesktopTaskStatusFilter.Completed -> "No completed tasks in this window."
                    window == DesktopTaskTimeWindow.Today -> "Nothing else for today."
                    else -> "No nudges in this window yet."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NudgeeColors.ink,
            )
            Text(
                if (status == DesktopTaskStatusFilter.ToDo) "Add a gentle nudge whenever you need one." else "Your completed nudges will stay here.",
                style = MaterialTheme.typography.bodyMedium,
                color = NudgeeColors.mutedInk,
            )
            if (status == DesktopTaskStatusFilter.ToDo) {
                NudgeeButton(label = "Add a nudge", onClick = onAddTask, style = NudgeeButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun DesktopTaskComposerDialog(
    parser: SupabaseEdgeFunctionReminderParser?,
    onDismiss: () -> Unit,
    onAddTask: (String, String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(DesktopReminderInputMode.NaturalLanguage) }
    var naturalLanguage by remember { mutableStateOf("") }
    var parsedReminder by remember { mutableStateOf<ParsedReminderDraft?>(null) }
    var clarificationDraft by remember { mutableStateOf<ParsedReminderDraft?>(null) }
    var parserMessage by remember { mutableStateOf<String?>(null) }
    var isUnderstanding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultDesktopDate()) }
    var time by remember { mutableStateOf(defaultDesktopTime()) }
    var recurrence by remember { mutableStateOf(TaskRecurrence.DoesNotRepeat) }
    var manualError by remember { mutableStateOf<String?>(null) }
    var manualFocusTarget by remember { mutableStateOf<DesktopManualFocusTarget?>(null) }

    Dialog(
        onDismissRequest = { if (!isUnderstanding) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NudgeeSurface(modifier = Modifier.width(500.dp), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Add a nudge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text("Keep it simple. Nudgee handles the timing.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                NudgeeSegmentedControl(
                    options = DesktopReminderInputMode.entries.map(DesktopReminderInputMode::label),
                    selectedIndex = mode.ordinal,
                    onOptionSelected = { mode = DesktopReminderInputMode.entries[it] },
                    selectedColor = NudgeeColors.sky.copy(alpha = 0.45f),
                )
                when (mode) {
                    DesktopReminderInputMode.NaturalLanguage -> {
                        Text("Tell Nudgee naturally", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                        NudgeeTextInput(
                            value = naturalLanguage,
                            onValueChange = {
                                naturalLanguage = it
                                parserMessage = null
                                parsedReminder = null
                                clarificationDraft = null
                            },
                            placeholder = "e.g. Remind me to buy eggs in 1 hour",
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        NudgeeButton(
                            label = "Understand reminder",
                            onClick = {
                                if (parser == null) {
                                    parserMessage = "Sign in with Google to let Nudgee understand a natural reminder."
                                } else {
                                    scope.launch {
                                        isUnderstanding = true
                                        parserMessage = null
                                        parsedReminder = null
                                        clarificationDraft = null
                                        runCatching { parser.parse(naturalLanguage) }
                                            .onSuccess { draft ->
                                                if (draft.needsClarification || draft.notifyAt == null) {
                                                    clarificationDraft = draft
                                                } else {
                                                    parsedReminder = draft
                                                }
                                            }
                                            .onFailure { error ->
                                                parserMessage = when (error) {
                                                    is AuthenticationRequiredException -> error.message
                                                    else -> error.message ?: "Nudgee could not understand that reminder. Please try again."
                                                }
                                            }
                                        isUnderstanding = false
                                    }
                                }
                            },
                            enabled = naturalLanguage.isNotBlank() && !isUnderstanding,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        parserMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk) }
                    }
                    DesktopReminderInputMode.Manual -> DesktopManualTaskFields(
                        title = title,
                        date = date,
                        time = time,
                        recurrence = recurrence,
                        focusTarget = manualFocusTarget,
                        error = manualError,
                        onTitleChange = { title = it; manualError = null },
                        onDateChange = { date = it; manualError = null },
                        onTimeChange = { time = it; manualError = null; manualFocusTarget = null },
                        onRecurrenceChange = { recurrence = it; manualFocusTarget = null },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton("Cancel", onDismiss, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                    if (mode == DesktopReminderInputMode.Manual) {
                        NudgeeButton(
                            label = "Add task",
                            onClick = {
                                desktopReminderInstant(date, time)
                                    .onSuccess { onAddTask(title.trim(), it, recurrence.rule) }
                                    .onFailure { manualError = it.message ?: "Enter a valid future date and time." }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank(),
                        )
                    }
                }
            }
        }
    }

    parsedReminder?.let { draft ->
        DesktopNaturalReminderConfirmationDialog(
            draft = draft,
            onDismiss = { parsedReminder = null },
            onEditDetails = {
                draft.toDesktopManualDetails().onSuccess { details ->
                    title = draft.title
                    date = details.date
                    time = details.time
                    recurrence = TaskRecurrence.fromRule(draft.recurrenceRule)
                    manualFocusTarget = null
                    manualError = null
                    mode = DesktopReminderInputMode.Manual
                    parsedReminder = null
                }.onFailure { error -> parserMessage = error.message ?: "Nudgee could not prepare the reminder details." }
            },
            onConfirm = { onAddTask(draft.title, requireNotNull(draft.notifyAt), draft.recurrenceRule); parsedReminder = null },
        )
    }
    clarificationDraft?.let { draft ->
        DesktopReminderClarificationDialog(
            draft = draft,
            onKeepEditing = { clarificationDraft = null },
            onSetManually = {
                val details = draft.notifyAt?.let { draft.toDesktopManualDetails().getOrNull() }
                title = draft.title
                details?.let {
                    date = it.date
                    time = it.time
                }
                recurrence = TaskRecurrence.fromRule(draft.recurrenceRule)
                manualFocusTarget = when (draft.clarificationType) {
                    ClarificationType.Time -> DesktopManualFocusTarget.Time
                    ClarificationType.Recurrence -> DesktopManualFocusTarget.Recurrence
                    null -> null
                }
                manualError = null
                mode = DesktopReminderInputMode.Manual
                clarificationDraft = null
            },
        )
    }
    if (isUnderstanding) DesktopUnderstandingReminderDialog(naturalLanguage)
}

@Composable
private fun DesktopTaskEditDialog(task: Task, onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    val local = Instant.parse(task.notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    var title by remember(task.id) { mutableStateOf(task.title) }
    var date by remember(task.id) { mutableStateOf(local.date.toString()) }
    var time by remember(task.id) { mutableStateOf(local.time.toString().take(5)) }
    var recurrence by remember(task.id) { mutableStateOf(TaskRecurrence.fromRule(task.recurrenceRule)) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NudgeeSurface(modifier = Modifier.width(500.dp), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Edit nudge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text("Changing the time prepares this reminder to be sent again.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                DesktopManualTaskFields(
                    title = title,
                    date = date,
                    time = time,
                    recurrence = recurrence,
                    focusTarget = null,
                    error = error,
                    onTitleChange = { title = it; error = null },
                    onDateChange = { date = it; error = null },
                    onTimeChange = { time = it; error = null },
                    onRecurrenceChange = { recurrence = it },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton("Cancel", onDismiss, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                    NudgeeButton(
                        label = "Save changes",
                        onClick = {
                            desktopReminderInstant(date, time)
                                .onSuccess { onSave(title.trim(), it, recurrence.rule) }
                                .onFailure { error = it.message ?: "Enter a valid future date and time." }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopDeleteConfirmationDialog(task: Task, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NudgeeSurface(modifier = Modifier.width(410.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Delete task?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text("“${task.title}” will be removed from all your Nudgee devices.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton("Keep it", onDismiss, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                    NudgeeButton("Delete", onConfirm, Modifier.weight(1f), style = NudgeeButtonStyle.Dark)
                }
            }
        }
    }
}

@Composable
private fun DesktopManualTaskFields(
    title: String,
    date: String,
    time: String,
    recurrence: TaskRecurrence,
    focusTarget: DesktopManualFocusTarget?,
    error: String?,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onRecurrenceChange: (TaskRecurrence) -> Unit,
) {
    val timeFocusRequester = remember { FocusRequester() }
    val recurrenceFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusTarget) {
        when (focusTarget) {
            DesktopManualFocusTarget.Time -> timeFocusRequester.requestFocus()
            DesktopManualFocusTarget.Recurrence -> recurrenceFocusRequester.requestFocus()
            null -> Unit
        }
    }
    NudgeeTextInput(value = title, onValueChange = onTitleChange, placeholder = "Task title", modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NudgeeTextInput(value = date, onValueChange = onDateChange, placeholder = "YYYY-MM-DD", modifier = Modifier.weight(1.35f))
        NudgeeTextInput(value = time, onValueChange = onTimeChange, placeholder = "HH:MM", modifier = Modifier.weight(1f).focusRequester(timeFocusRequester))
    }
    Spacer(Modifier.height(12.dp))
    Text("Does it repeat?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
    Spacer(Modifier.height(7.dp))
    NudgeeSegmentedControl(
        options = TaskRecurrence.entries.map(TaskRecurrence::label),
        selectedIndex = recurrence.ordinal,
        onOptionSelected = { onRecurrenceChange(TaskRecurrence.entries[it]) },
        modifier = Modifier
            .focusRequester(recurrenceFocusRequester)
            .focusable()
            .then(
                if (focusTarget == DesktopManualFocusTarget.Recurrence) {
                    Modifier.border(2.dp, NudgeeColors.periwinkle, RoundedCornerShape(22.dp)).padding(2.dp)
                } else {
                    Modifier
                },
            ),
        selectedColor = NudgeeColors.mint.copy(alpha = 0.56f),
    )
    Text(
        if (recurrence == TaskRecurrence.DoesNotRepeat) {
            "This is a one-time nudge."
        } else {
            "After this reminder is delivered, Nudgee automatically creates and schedules the next task — even if you do not complete this one."
        },
        style = MaterialTheme.typography.bodySmall,
        color = NudgeeColors.mutedInk,
    )
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk) }
}

@Composable
private fun DesktopNaturalReminderConfirmationDialog(
    draft: ParsedReminderDraft,
    onDismiss: () -> Unit,
    onEditDetails: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NudgeeSurface(modifier = Modifier.width(430.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Ready to schedule?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text(draft.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                Text(
                    draft.desktopReservationTimeLabel(),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NudgeeColors.lavenderSurface).padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.lavender,
                )
                draft.recurrenceRule?.let { rule ->
                    Text(TaskRecurrence.fromRule(rule).label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.mutedInk)
                    Text(
                        "After this reminder is delivered, Nudgee automatically creates and schedules the next task — even if you do not complete this one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NudgeeColors.mutedInk,
                    )
                }
                Text("You can fine-tune the details before saving.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton("Edit details", onEditDetails, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                    NudgeeButton("Schedule", onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DesktopReminderClarificationDialog(
    draft: ParsedReminderDraft,
    onKeepEditing: () -> Unit,
    onSetManually: () -> Unit,
) {
    val isRecurrenceClarification = draft.clarificationType == ClarificationType.Recurrence
    Dialog(onDismissRequest = onKeepEditing) {
        NudgeeSurface(modifier = Modifier.width(430.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).background(NudgeeColors.mint.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                }
                Text(
                    if (isRecurrenceClarification) "Choose a repeat pattern" else "One more detail",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NudgeeColors.ink,
                )
                Text(
                    draft.clarification ?: "When should I remind you?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NudgeeColors.mutedInk,
                )
                Text(
                    if (isRecurrenceClarification) {
                        "You can choose one of Nudgee’s supported repeat patterns below."
                    } else {
                        "Add a date or time, then Nudgee can schedule it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NudgeeColors.mutedInk,
                )
                Text(
                    "This request already used one AI credit. Continue manually to finish it without using another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NudgeeColors.mutedInk,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton("Not now", onKeepEditing, Modifier.weight(1f), style = NudgeeButtonStyle.Secondary)
                    NudgeeButton(
                        if (isRecurrenceClarification) "Choose repeat" else "Set reminder time",
                        onSetManually,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopUnderstandingReminderDialog(prompt: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        NudgeeSurface(modifier = Modifier.width(390.dp), shape = RoundedCornerShape(30.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(42.dp), color = NudgeeColors.lavender, trackColor = NudgeeColors.periwinkle.copy(alpha = 0.32f), strokeWidth = 3.dp)
                Text("Nudgee is on it", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text("Finding the task and the right time for you.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = NudgeeColors.mutedInk)
                Text("“$prompt”", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NudgeeColors.softSurface).padding(16.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = NudgeeColors.ink)
            }
        }
    }
}

private fun Task.localDate() = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun Task.isInWindow(window: DesktopTaskTimeWindow): Boolean = runCatching {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    when (window) {
        DesktopTaskTimeWindow.Today -> localDate() == today
        DesktopTaskTimeWindow.SevenDays -> localDate() in today..today.plus(6, DateTimeUnit.DAY)
        DesktopTaskTimeWindow.ThirtyDays -> localDate() in today..today.plus(29, DateTimeUnit.DAY)
        DesktopTaskTimeWindow.AllTime -> true
    }
}.getOrDefault(false)

private fun Task.desktopReminderLabel(): String {
    val local = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = (local.hour % 12).takeIf { it != 0 } ?: 12
    return "${local.date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${local.date.dayOfMonth} · $hour:${local.minute.toString().padStart(2, '0')} ${if (local.hour < 12) "AM" else "PM"}"
}

private fun defaultDesktopDate() = Clock.System.now().plus(1, DateTimeUnit.HOUR).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private fun defaultDesktopTime() = Clock.System.now().plus(1, DateTimeUnit.HOUR).toLocalDateTime(TimeZone.currentSystemDefault()).time.toString().take(5)

private fun desktopReminderInstant(date: String, time: String): Result<String> = runCatching {
    val notifyAt = LocalDateTime.parse("${date.trim()}T${time.trim()}").toInstant(TimeZone.currentSystemDefault())
    require(notifyAt > Clock.System.now()) { "Choose a future date and time." }
    notifyAt.toString()
}

private data class DesktopManualReminderDetails(val date: String, val time: String)

private fun ParsedReminderDraft.toDesktopManualDetails(): Result<DesktopManualReminderDetails> = runCatching {
    val local = Instant.parse(requireNotNull(notifyAt)).toLocalDateTime(TimeZone.currentSystemDefault())
    DesktopManualReminderDetails(date = local.date.toString(), time = local.time.toString().take(5))
}

private fun ParsedReminderDraft.desktopReservationTimeLabel(): String = runCatching {
    val notifyInstant = Instant.parse(requireNotNull(notifyAt))
    val remainingSeconds = notifyInstant.epochSeconds - Clock.System.now().epochSeconds
    val minutesUntil = (remainingSeconds.coerceAtLeast(0) + 59) / 60
    when {
        minutesUntil in 1L..59L -> "in $minutesUntil minute${if (minutesUntil == 1L) "" else "s"}"
        minutesUntil in 60L..(23L * 60L + 59L) -> {
            val hours = minutesUntil / 60
            "in $hours hour${if (hours == 1L) "" else "s"}"
        }
        else -> desktopReminderLabelFor(notifyInstant)
    }
}.getOrDefault("at the selected time")

private fun desktopReminderLabelFor(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = (local.hour % 12).takeIf { it != 0 } ?: 12
    return "${local.date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${local.date.dayOfMonth} at $hour:${local.minute.toString().padStart(2, '0')} ${if (local.hour < 12) "AM" else "PM"}"
}
