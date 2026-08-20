package com.soopeach.nudgee.client.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import com.soopeach.nudgee.client.domain.reminder.AuthenticationRequiredException
import com.soopeach.nudgee.client.domain.reminder.DailyParseLimitReachedException
import com.soopeach.nudgee.client.domain.reminder.NaturalLanguageReminderParser
import com.soopeach.nudgee.client.domain.reminder.ParsedReminderDraft
import com.soopeach.nudgee.client.domain.reminder.ReminderParseRequestException
import com.soopeach.nudgee.client.domain.reminder.ReminderParseUsage
import com.soopeach.nudgee.client.domain.reminder.SupabaseEdgeFunctionReminderParser
import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.domain.task.TaskRepository
import com.soopeach.nudgee.client.ui.calendar.CalendarScreen
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeDeleteConfirmationDialog
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSegmentedControl
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextInput
import com.soopeach.nudgee.client.ui.navigation.NudgeeBottomNavigation
import com.soopeach.nudgee.client.ui.navigation.NudgeeDestination
import com.soopeach.nudgee.client.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private enum class TaskTimeWindow(val label: String, val sectionTitle: String) {
    Today("Today", "Today’s nudges"),
    SevenDays("7 days", "Next 7 days"),
    ThirtyDays("30 days", "Next 30 days"),
    AllTime("All", "All nudges"),
}

private enum class TaskStatusFilter(val label: String, val completed: Boolean) {
    ToDo("To do", completed = false),
    Completed("Completed", completed = true),
}

private data class HomeUiState(
    val window: TaskTimeWindow = TaskTimeWindow.Today,
    val status: TaskStatusFilter = TaskStatusFilter.ToDo,
    val destination: NudgeeDestination = NudgeeDestination.Home,
    val isQuickAddVisible: Boolean = false,
    val taskPendingDeletion: Task? = null,
)

private class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()
    fun update(transform: (HomeUiState) -> HomeUiState) = _state.update(transform)
}

private data class QuickAddUiState(
    val mode: ReminderInputMode = ReminderInputMode.NaturalLanguage,
    val naturalLanguage: String = "",
    val parsedReminder: ParsedReminderDraft? = null,
    val clarificationDraft: ParsedReminderDraft? = null,
    val inlineClarificationMessage: String? = null,
    val parserMessage: String? = null,
    val isUnderstanding: Boolean = false,
    val parseUsage: ReminderParseUsage? = null,
    val manualTitle: String = "",
    val manualDate: String = defaultManualDate(),
    val manualTime: String = defaultManualTime(),
    val manualError: String? = null,
)

/** Keeps the add-task draft alive across recompositions and owns parser work. */
private class QuickAddViewModel(
    private val parser: NaturalLanguageReminderParser?,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickAddUiState())
    val state = _state.asStateFlow()

    init {
        refreshParseUsage()
    }

    fun selectMode(mode: ReminderInputMode) = update { it.copy(mode = mode) }

    fun updateNaturalLanguage(value: String) = update {
        it.copy(
            naturalLanguage = value,
            parserMessage = null,
            parsedReminder = null,
            clarificationDraft = null,
            inlineClarificationMessage = null,
        )
    }

    fun updateManualTitle(value: String) = update { it.copy(manualTitle = value, manualError = null) }
    fun updateManualDate(value: String) = update { it.copy(manualDate = value, manualError = null) }
    fun updateManualTime(value: String) = update { it.copy(manualTime = value, manualError = null) }
    fun dismissParsedReminder() = update { it.copy(parsedReminder = null) }
    fun dismissClarification() = update { current ->
        current.copy(
            clarificationDraft = null,
            inlineClarificationMessage = current.clarificationDraft?.clarification ?: "Add a date or time so Nudgee can schedule it.",
        )
    }
    fun dismissParserMessage() = update { it.copy(parserMessage = null) }

    fun understandReminder() {
        val prompt = state.value.naturalLanguage.trim()
        if (prompt.isBlank()) return
        if (parser == null) {
            update {
                it.copy(
                    parserMessage = "Sign in with Google to let Nudgee understand a natural reminder.",
                )
            }
            return
        }

        update {
            it.copy(
                isUnderstanding = true,
                parserMessage = null,
                parsedReminder = null,
                clarificationDraft = null,
                inlineClarificationMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { parser.parse(prompt) }
                .onSuccess { parsed ->
                    update {
                        val usage = parsed.remainingFreeParses?.let {
                            ReminderParseUsage(
                                usedFreeParses = 10 - it,
                                remainingFreeParses = it,
                                dailyFreeParseLimit = 10,
                                bonusCredits = parsed.bonusCredits ?: 0,
                            )
                        } ?: it.parseUsage
                        if (parsed.needsClarification || parsed.notifyAt == null) {
                            it.copy(parseUsage = usage, clarificationDraft = parsed)
                        } else {
                            it.copy(parseUsage = usage, parsedReminder = parsed)
                        }
                    }
                }
                .onFailure { error ->
                    update {
                        it.copy(
                            parserMessage = error.toReminderParserMessage(),
                            parseUsage = if (error is DailyParseLimitReachedException) {
                                ReminderParseUsage(usedFreeParses = 10, remainingFreeParses = 0, dailyFreeParseLimit = 10, bonusCredits = 0)
                            } else {
                                it.parseUsage
                            },
                        )
                    }
                }
            update { it.copy(isUnderstanding = false) }
        }
    }

    fun editParsedDetails() {
        val draft = state.value.parsedReminder ?: return
        draft.toManualReminderDetails()
            .onSuccess { details ->
                update {
                    it.copy(
                        mode = ReminderInputMode.Manual,
                        manualTitle = draft.title,
                        manualDate = details.date,
                        manualTime = details.time,
                        manualError = null,
                        parsedReminder = null,
                    )
                }
            }
            .onFailure { error ->
                update {
                    it.copy(
                        parsedReminder = null,
                        parserMessage = error.message ?: "Nudgee could not prepare the reminder details.",
                    )
                }
            }
    }

    fun setClarificationDetailsManually() {
        val draft = state.value.clarificationDraft ?: return
        update {
            it.copy(
                mode = ReminderInputMode.Manual,
                manualTitle = draft.title,
                manualError = null,
                clarificationDraft = null,
                inlineClarificationMessage = null,
            )
        }
    }

    fun validateManualReminder(onValid: (title: String, notifyAt: String) -> Unit) {
        val current = state.value
        manualReminderInstant(current.manualDate, current.manualTime)
            .onSuccess { onValid(current.manualTitle.trim(), it) }
            .onFailure { error -> update { it.copy(manualError = error.message) } }
    }

    fun reset() {
        _state.value = QuickAddUiState(parseUsage = _state.value.parseUsage)
    }

    fun refreshParseUsage() {
        val activeParser = parser ?: return
        viewModelScope.launch {
            runCatching { activeParser.usage() }
                .onSuccess { usage -> update { it.copy(parseUsage = usage) } }
        }
    }

    private fun update(transform: (QuickAddUiState) -> QuickAddUiState) = _state.update(transform)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    repository: TaskRepository,
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
) {
    val supabase = NudgeeSupabase.client
    val store: TaskListViewModel = viewModel { TaskListViewModel(repository) }
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel() }
    val quickAddViewModel: QuickAddViewModel = viewModel {
        QuickAddViewModel(supabase?.let(::SupabaseEdgeFunctionReminderParser))
    }
    val homeState by homeViewModel.state.collectAsState()
    val taskState by store.state.collectAsState()
    val tasks = taskState.tasks

    val todayTasks = tasks.filter(Task::isDueToday)
    val todayIncompleteCount = todayTasks.count { !it.completed }
    val todayCompletedCount = todayTasks.count { it.completed }
    val todayProgress = if (todayTasks.isEmpty()) 0f else todayCompletedCount.toFloat() / todayTasks.size
    val filteredTasks = tasks.filter { task ->
        task.completed == homeState.status.completed && task.isInWindow(homeState.window)
    }

    Scaffold(
        containerColor = NudgeeColors.softSurface,
        bottomBar = {
            NudgeeBottomNavigation(current = homeState.destination, onNavigate = { destination -> homeViewModel.update { it.copy(destination = destination) } })
        },
        floatingActionButton = {
            if (homeState.destination == NudgeeDestination.Home) {
                FloatingActionButton(
                    onClick = {
                        // The composer ViewModel survives sheet dismissal, so always
                        // refresh the server-owned allowance before showing it again.
                        quickAddViewModel.refreshParseUsage()
                        homeViewModel.update { it.copy(isQuickAddVisible = true) }
                    },
                    containerColor = NudgeeColors.periwinkle,
                    contentColor = NudgeeColors.ink,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    CenteredPlusIcon()
                }
            }
        },
    ) { contentPadding ->
        if (homeState.destination == NudgeeDestination.Home) LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MobileHeader(incompleteCount = todayIncompleteCount) }
            item { ProgressCard(completedCount = todayCompletedCount, totalCount = todayTasks.size, progress = todayProgress) }
            taskState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NudgeeColors.mutedInk,
                    )
                }
            }
            item {
                TimeWindowSelector(
                    selectedWindow = homeState.window,
                    onSelect = { window -> homeViewModel.update { it.copy(window = window) } },
                )
            }
            item {
                TaskStatusSelector(
                    selectedStatus = homeState.status,
                    onSelect = { status -> homeViewModel.update { it.copy(status = status) } },
                )
            }
            item {
                Text(
                    text = if (homeState.status == TaskStatusFilter.ToDo) {
                        homeState.window.sectionTitle
                    } else {
                        "Completed · ${homeState.window.label}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                )
            }

            if (taskState.isLoading) {
                item { Text("Loading your nudges…", color = NudgeeColors.mutedInk) }
            } else if (filteredTasks.isEmpty()) {
                item { EmptyTaskState(status = homeState.status, window = homeState.window) }
            } else item {
                TaskListGroup(
                    tasks = filteredTasks,
                    onCheckedChange = { task, checked ->
                        if (checked != task.completed) store.toggleTask(task)
                    },
                    onDelete = { task -> homeViewModel.update { it.copy(taskPendingDeletion = task) } },
                )
            }
        } else {
            when (homeState.destination) {
                NudgeeDestination.Calendar -> CalendarScreen(
                    tasks = tasks,
                    contentPadding = contentPadding,
                    onToggleTask = store::toggleTask,
                    onDeleteTask = store::deleteTask,
                    onUpdateTask = store::updateTask,
                )
                NudgeeDestination.Settings -> SettingsScreen(
                    email = email,
                    avatarUrl = avatarUrl,
                    onSignOut = onSignOut,
                    contentPadding = contentPadding,
                )
                NudgeeDestination.Home -> Unit
            }
        }
    }

    if (homeState.isQuickAddVisible) {
        QuickAddSheet(
            viewModel = quickAddViewModel,
            onDismiss = {
                quickAddViewModel.reset()
                homeViewModel.update { it.copy(isQuickAddVisible = false) }
            },
            onAdd = { title, notifyAt ->
                store.addTask(title, notifyAt)
                quickAddViewModel.reset()
                homeViewModel.update {
                    it.copy(
                        status = TaskStatusFilter.ToDo,
                        window = taskWindowFor(notifyAt),
                        isQuickAddVisible = false,
                    )
                }
            },
        )
    }

    homeState.taskPendingDeletion?.let { task ->
        NudgeeDeleteConfirmationDialog(
            taskTitle = task.title,
            onDismiss = { homeViewModel.update { it.copy(taskPendingDeletion = null) } },
            onConfirm = {
                store.deleteTask(task)
                homeViewModel.update { it.copy(taskPendingDeletion = null) }
            },
        )
    }
}

@Composable
private fun MobileHeader(incompleteCount: Int) {
    Column {
        Text("Nudgee", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (incompleteCount == 0) "You’re all caught up."
            else "$incompleteCount little nudge${if (incompleteCount == 1) "" else "s"} left for today.",
            style = MaterialTheme.typography.bodyLarge,
            color = NudgeeColors.mutedInk,
        )
    }
}

@Composable
private fun ProgressCard(completedCount: Int, totalCount: Int, progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Today’s rhythm", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "$completedCount of $totalCount complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NudgeeColors.ink,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NudgeeColors.progressTrack),
                )
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(NudgeeColors.lavender),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeWindowSelector(
    selectedWindow: TaskTimeWindow,
    onSelect: (TaskTimeWindow) -> Unit,
) {
    NudgeeSegmentedControl(
        options = TaskTimeWindow.entries.map(TaskTimeWindow::label),
        selectedIndex = selectedWindow.ordinal,
        onOptionSelected = { onSelect(TaskTimeWindow.entries[it]) },
    )
}

@Composable
private fun TaskStatusSelector(
    selectedStatus: TaskStatusFilter,
    onSelect: (TaskStatusFilter) -> Unit,
) {
    NudgeeSegmentedControl(
        options = TaskStatusFilter.entries.map(TaskStatusFilter::label),
        selectedIndex = selectedStatus.ordinal,
        onOptionSelected = { onSelect(TaskStatusFilter.entries[it]) },
        selectedColor = NudgeeColors.mint,
    )
}

@Composable
private fun TaskListGroup(
    tasks: List<Task>,
    onCheckedChange: (Task, Boolean) -> Unit,
    onDelete: (Task) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            tasks.forEachIndexed { index, task ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 58.dp),
                        color = NudgeeColors.line,
                    )
                }
                MobileTaskRow(
                    task = task,
                    onCheckedChange = { onCheckedChange(task, it) },
                    onDelete = { onDelete(task) },
                )
            }
        }
    }
}

@Composable
private fun MobileTaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NudgeeCheckControl(
            checked = task.completed,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (task.completed) NudgeeColors.mutedInk else NudgeeColors.ink,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
            )
            Spacer(Modifier.height(4.dp))
            Text(task.reminderLabel(), style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (task.completed) NudgeeColors.mint else NudgeeColors.sky, CircleShape),
        )
        TextButton(onClick = onDelete, contentPadding = PaddingValues(4.dp)) {
            Text("×", style = MaterialTheme.typography.titleLarge, color = NudgeeColors.mutedInk)
        }
    }
}

@Composable
private fun CenteredPlusIcon() {
    Canvas(Modifier.size(24.dp)) {
        val center = size.width / 2
        val arm = size.width * 0.28f
        val strokeWidth = size.width * 0.11f

        drawLine(
            color = NudgeeColors.ink,
            start = androidx.compose.ui.geometry.Offset(center - arm, center),
            end = androidx.compose.ui.geometry.Offset(center + arm, center),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = NudgeeColors.ink,
            start = androidx.compose.ui.geometry.Offset(center, center - arm),
            end = androidx.compose.ui.geometry.Offset(center, center + arm),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NudgeeCheckControl(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val petalColor = if (checked) NudgeeColors.lavender else NudgeeColors.sky
    val centerColor = if (checked) NudgeeColors.lavender else Color.White

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val center = size.width / 2
            val petalRadius = size.width * 0.22f
            val petalOffset = size.width * 0.19f
            val centerRadius = size.width * 0.24f

            listOf(
                androidx.compose.ui.geometry.Offset(center, center - petalOffset),
                androidx.compose.ui.geometry.Offset(center + petalOffset, center),
                androidx.compose.ui.geometry.Offset(center, center + petalOffset),
                androidx.compose.ui.geometry.Offset(center - petalOffset, center),
            ).forEach { petalCenter ->
                drawCircle(
                    color = petalColor,
                    radius = petalRadius,
                    center = petalCenter,
                )
            }
            drawCircle(color = centerColor, radius = centerRadius, center = center.let { androidx.compose.ui.geometry.Offset(it, it) })

            if (!checked) {
                drawCircle(
                    color = NudgeeColors.sky.copy(alpha = 0.7f),
                    radius = centerRadius,
                    center = androidx.compose.ui.geometry.Offset(center, center),
                    style = Stroke(width = size.width * 0.065f),
                )
            } else {
                val start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.51f)
                val middle = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height * 0.61f)
                val end = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.40f)
                drawLine(Color.White, start, middle, strokeWidth = size.width * 0.075f, cap = StrokeCap.Round)
                drawLine(Color.White, middle, end, strokeWidth = size.width * 0.075f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun EmptyTaskState(status: TaskStatusFilter, window: TaskTimeWindow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦", style = MaterialTheme.typography.headlineMedium, color = NudgeeColors.lavender)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (status == TaskStatusFilter.Completed) {
                    "No completed tasks in this window."
                } else if (window == TaskTimeWindow.Today) {
                    "Nothing else for today."
                } else {
                    "No nudges in this window yet."
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = NudgeeColors.mutedInk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuickAddSheet(
    viewModel: QuickAddViewModel,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!state.isUnderstanding) onDismiss() },
        sheetState = sheetState,
        containerColor = NudgeeColors.softSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 80.dp),
        ) {
            Spacer(Modifier.height(8.dp))
                    Text("Add a nudge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                    Text("Keep it simple. Nudgee handles the timing.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                    Spacer(Modifier.height(16.dp))
                    NudgeeSegmentedControl(
                        options = ReminderInputMode.entries.map(ReminderInputMode::label),
                        selectedIndex = state.mode.ordinal,
                        onOptionSelected = { viewModel.selectMode(ReminderInputMode.entries[it]) },
                        selectedColor = NudgeeColors.sky.copy(alpha = 0.45f),
                    )
                    Spacer(Modifier.height(16.dp))
                    when (state.mode) {
                        ReminderInputMode.NaturalLanguage -> NaturalLanguageReminderForm(
                            naturalLanguage = state.naturalLanguage,
                            clarificationMessage = state.inlineClarificationMessage,
                            isUnderstanding = state.isUnderstanding,
                            usage = state.parseUsage,
                            onNaturalLanguageChange = viewModel::updateNaturalLanguage,
                            onUnderstand = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                viewModel.understandReminder()
                            },
                        )
                        ReminderInputMode.Manual -> ManualReminderForm(
                            title = state.manualTitle,
                            date = state.manualDate,
                            time = state.manualTime,
                            error = state.manualError,
                            onTitleChange = viewModel::updateManualTitle,
                            onDateChange = viewModel::updateManualDate,
                            onTimeChange = viewModel::updateManualTime,
                            onAdd = { viewModel.validateManualReminder(onAdd) },
                        )
                    }
        }
    }

    state.parsedReminder?.let { draft ->
        NaturalReminderConfirmationDialog(
            draft = draft,
            onDismiss = viewModel::dismissParsedReminder,
            onEditDetails = viewModel::editParsedDetails,
            onConfirm = {
                onAdd(draft.title, requireNotNull(draft.notifyAt))
            },
        )
    }

    state.clarificationDraft?.let { draft ->
        ReminderClarificationDialog(
            message = draft.clarification ?: "When should I remind you?",
            onKeepEditing = viewModel::dismissClarification,
            onSetManually = viewModel::setClarificationDetailsManually,
        )
    }

    state.parserMessage?.let { message ->
        ReminderParseErrorDialog(
            message = message,
            onDismiss = viewModel::dismissParserMessage,
        )
    }

    if (state.isUnderstanding) {
        UnderstandingReminderDialog(prompt = state.naturalLanguage)
    }
}

private enum class ReminderInputMode(val label: String) {
    NaturalLanguage("Natural language"),
    Manual("Set manually"),
}

@Composable
private fun NaturalLanguageReminderForm(
    naturalLanguage: String,
    clarificationMessage: String?,
    isUnderstanding: Boolean,
    usage: ReminderParseUsage?,
    onNaturalLanguageChange: (String) -> Unit,
    onUnderstand: () -> Unit,
) {
    val hasAiAllowance = usage == null || usage.remainingFreeParses > 0 || usage.bonusCredits > 0
    Text("Tell Nudgee naturally", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
    Spacer(Modifier.height(8.dp))
    usage?.let { ParseUsageCard(it) }
    if (usage != null) Spacer(Modifier.height(10.dp))
    NudgeeTextInput(
        value = naturalLanguage,
        onValueChange = onNaturalLanguageChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        placeholder = if (hasAiAllowance) "e.g. Remind me to buy eggs in 1 hour" else "Free AI reminders reset tomorrow",
        enabled = hasAiAllowance,
    )
    Spacer(Modifier.height(16.dp))
    NudgeeButton(
        label = "Understand reminder",
        onClick = onUnderstand,
        enabled = hasAiAllowance && naturalLanguage.isNotBlank() && !isUnderstanding,
        modifier = Modifier.fillMaxWidth(),
    )
    clarificationMessage?.let {
        Spacer(Modifier.height(12.dp))
        ClarificationHintCard(it)
    }
}

@Composable
private fun ClarificationHintCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NudgeeColors.mint.copy(alpha = 0.28f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Nudgee needs one more detail",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = NudgeeColors.ink,
        )
        Text(message, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
    }
}

@Composable
private fun ReminderClarificationDialog(
    message: String,
    onKeepEditing: () -> Unit,
    onSetManually: () -> Unit,
) {
    Dialog(onDismissRequest = onKeepEditing) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(NudgeeColors.mint.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                }
                Text("One more detail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                Text("Add a date or time, then Nudgee can schedule it.", style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
                NudgeeButton(label = "Set reminder time", onClick = onSetManually, modifier = Modifier.fillMaxWidth())
                NudgeeButton(
                    label = "Keep editing",
                    onClick = onKeepEditing,
                    modifier = Modifier.fillMaxWidth(),
                    style = NudgeeButtonStyle.Secondary,
                )
            }
        }
    }
}

@Composable
private fun ReminderParseErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Couldn’t use AI reminder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                NudgeeButton(label = "Got it", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ParseUsageCard(usage: ReminderParseUsage) {
    val availableCredits = usage.remainingFreeParses + usage.bonusCredits
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NudgeeColors.sky.copy(alpha = 0.28f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (usage.bonusCredits > 0) {
                Text(
                    "${usage.remainingFreeParses} of ${usage.dailyFreeParseLimit} free left today",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                )
                Text(
                    "${usage.bonusCredits} reward credits ready · resets at local midnight",
                    style = MaterialTheme.typography.bodySmall,
                    color = NudgeeColors.mutedInk,
                )
            } else {
                Text(
                    "${usage.remainingFreeParses} of ${usage.dailyFreeParseLimit} free left today",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                )
                Text(
                    "Resets at local midnight",
                    style = MaterialTheme.typography.bodySmall,
                    color = NudgeeColors.mutedInk,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(if (availableCredits == 0) NudgeeColors.lavenderSurface else NudgeeColors.mint.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$availableCredits left",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (availableCredits == 0) NudgeeColors.lavender else NudgeeColors.ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun UnderstandingReminderDialog(prompt: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(NudgeeColors.lavenderSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            color = NudgeeColors.lavender,
                            trackColor = NudgeeColors.periwinkle.copy(alpha = 0.32f),
                            strokeWidth = 3.dp,
                        )
                    }
                }
                Text(
                    text = "Nudgee is on it",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NudgeeColors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Finding the task and the right time for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NudgeeColors.mutedInk,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NudgeeColors.softSurface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "“$prompt”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NudgeeColors.ink,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(7.dp).background(NudgeeColors.mint, CircleShape))
                    Text(
                        text = "Almost there…",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NudgeeColors.mutedInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun NaturalReminderConfirmationDialog(
    draft: ParsedReminderDraft,
    onDismiss: () -> Unit,
    onEditDetails: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Ready to schedule?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text(draft.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                Text(draft.reservationTimeLabel(), modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NudgeeColors.lavenderSurface).padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.lavender)
                Text("You can fine-tune the details before saving.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NudgeeButton(
                        label = "Edit details",
                        onClick = onEditDetails,
                        modifier = Modifier.weight(1f),
                        style = NudgeeButtonStyle.Secondary,
                    )
                    NudgeeButton(
                        label = "Schedule",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualReminderForm(
    title: String,
    date: String,
    time: String,
    error: String?,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Text("Set every detail", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
    Spacer(Modifier.height(8.dp))
    NudgeeTextInput(
        value = title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = "Task title",
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NudgeeTextInput(
            value = date,
            onValueChange = onDateChange,
            modifier = Modifier.weight(1.35f),
            placeholder = "YYYY-MM-DD",
        )
        NudgeeTextInput(
            value = time,
            onValueChange = onTimeChange,
            modifier = Modifier.weight(1f),
            placeholder = "HH:MM",
        )
    }
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
    }
    Spacer(Modifier.height(24.dp))
    AddTaskButton(onClick = onAdd, enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank())
}

@Composable
private fun AddTaskButton(onClick: () -> Unit, enabled: Boolean) {
    NudgeeButton(
        label = "Add task",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    )
}

private fun defaultManualDate(): String = Clock.System.now()
    .plus(1, DateTimeUnit.HOUR)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
    .toString()

private fun defaultManualTime(): String = Clock.System.now()
    .plus(1, DateTimeUnit.HOUR)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .time
    .toString()
    .take(5)

private fun manualReminderInstant(date: String, time: String): Result<String> = runCatching {
    val notifyAt = LocalDateTime.parse("${date.trim()}T${time.trim()}")
        .toInstant(TimeZone.currentSystemDefault())
    require(notifyAt > Clock.System.now()) { "Choose a future date and time." }
    notifyAt.toString()
}

private data class ManualReminderDetails(
    val date: String,
    val time: String,
)

private fun ParsedReminderDraft.toManualReminderDetails(): Result<ManualReminderDetails> = runCatching {
    val local = Instant.parse(requireNotNull(notifyAt))
        .toLocalDateTime(TimeZone.currentSystemDefault())
    ManualReminderDetails(
        date = local.date.toString(),
        time = local.time.toString().take(5),
    )
}

private fun Throwable.toReminderParserMessage(): String {
    if (this is AuthenticationRequiredException) return message ?: "Sign in with Google before using AI reminders."
    if (this is DailyParseLimitReachedException) return message ?: "You’ve used all 10 free reminder parses for today. Try again tomorrow."

    if (this is ReminderParseRequestException) {
        val reference = requestId?.let { " Reference: ${it.take(8)}" }.orEmpty()
        return "Nudgee couldn’t understand that reminder. Please try again or set it manually.$reference"
    }

    val details = message.orEmpty()
    if (
        details.contains("daily_parse_limit_reached", ignoreCase = true) ||
        details.contains("429") ||
        details.contains("free reminder parses", ignoreCase = true)
    ) {
        return "You’ve used all 10 free reminder parses for today. Try again tomorrow."
    }
    return "Nudgee couldn’t understand that reminder. Please try again or set it manually."
}

private fun ParsedReminderDraft.reservationTimeLabel(): String = runCatching {
    val notifyInstant = requireNotNull(notifyAt).let(Instant::parse)
    val remainingSeconds = notifyInstant.epochSeconds - Clock.System.now().epochSeconds
    // Parsing and rendering take a few seconds. Round up so a task parsed as “in 3 hours”
    // never appears as “in 2 hours” solely because 3:00:00 became 2:59:58.
    val minutesUntil = (remainingSeconds.coerceAtLeast(0) + 59) / 60
    when {
        minutesUntil in 1L..59L -> "in $minutesUntil minute${if (minutesUntil == 1L) "" else "s"}"
        minutesUntil in 60L..(23L * 60L + 59L) -> {
            val hours = minutesUntil / 60
            "in $hours hour${if (hours == 1L) "" else "s"}"
        }
        else -> "for ${notifyInstant.toLocalDateTime(TimeZone.currentSystemDefault()).let { local ->
            val hour = (local.hour % 12).let { if (it == 0) 12 else it }
            val minute = local.minute.toString().padStart(2, '0')
            "${local.date.month.shortName()} ${local.date.dayOfMonth} at $hour:$minute ${if (local.hour < 12) "AM" else "PM"}"
        }}"
    }
}.getOrDefault("at the selected time")

private fun Task.isInWindow(window: TaskTimeWindow): Boolean = runCatching {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val taskDate = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
    when (window) {
        TaskTimeWindow.Today -> taskDate == today
        TaskTimeWindow.SevenDays -> taskDate in today..today.plus(6, DateTimeUnit.DAY)
        TaskTimeWindow.ThirtyDays -> taskDate in today..today.plus(29, DateTimeUnit.DAY)
        TaskTimeWindow.AllTime -> true
    }
}.getOrDefault(false)

private fun Task.isDueToday(): Boolean = isInWindow(TaskTimeWindow.Today)

private fun taskWindowFor(notifyAt: String): TaskTimeWindow = runCatching {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val taskDate = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
    when {
        taskDate == today -> TaskTimeWindow.Today
        taskDate in today..today.plus(6, DateTimeUnit.DAY) -> TaskTimeWindow.SevenDays
        taskDate in today..today.plus(29, DateTimeUnit.DAY) -> TaskTimeWindow.ThirtyDays
        else -> TaskTimeWindow.AllTime
    }
}.getOrDefault(TaskTimeWindow.AllTime)

private fun Task.reminderLabel(): String = runCatching {
    val local = Instant.parse(notifyAt).toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = (local.hour % 12).let { if (it == 0) 12 else it }
    val minute = local.minute.toString().padStart(2, '0')
    val meridiem = if (local.hour < 12) "AM" else "PM"
    "${local.date.dayOfWeek.shortName()}, ${local.date.month.shortName()} ${local.date.dayOfMonth} · $hour:$minute $meridiem"
}.getOrDefault("Reminder set")

private fun kotlinx.datetime.DayOfWeek.shortName(): String = when (this) {
    kotlinx.datetime.DayOfWeek.MONDAY -> "Mon"
    kotlinx.datetime.DayOfWeek.TUESDAY -> "Tue"
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Wed"
    kotlinx.datetime.DayOfWeek.THURSDAY -> "Thu"
    kotlinx.datetime.DayOfWeek.FRIDAY -> "Fri"
    kotlinx.datetime.DayOfWeek.SATURDAY -> "Sat"
    kotlinx.datetime.DayOfWeek.SUNDAY -> "Sun"
}

private fun kotlinx.datetime.Month.shortName(): String = when (this) {
    kotlinx.datetime.Month.JANUARY -> "Jan"
    kotlinx.datetime.Month.FEBRUARY -> "Feb"
    kotlinx.datetime.Month.MARCH -> "Mar"
    kotlinx.datetime.Month.APRIL -> "Apr"
    kotlinx.datetime.Month.MAY -> "May"
    kotlinx.datetime.Month.JUNE -> "Jun"
    kotlinx.datetime.Month.JULY -> "Jul"
    kotlinx.datetime.Month.AUGUST -> "Aug"
    kotlinx.datetime.Month.SEPTEMBER -> "Sep"
    kotlinx.datetime.Month.OCTOBER -> "Oct"
    kotlinx.datetime.Month.NOVEMBER -> "Nov"
    kotlinx.datetime.Month.DECEMBER -> "Dec"
}
