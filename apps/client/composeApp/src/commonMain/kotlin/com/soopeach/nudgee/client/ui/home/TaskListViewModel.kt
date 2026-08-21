package com.soopeach.nudgee.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.domain.task.TaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** Keeps task I/O and Realtime collection out of Compose components. */
class TaskListViewModel(
    private val repository: TaskRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TaskListUiState())
    val state: StateFlow<TaskListUiState> = _state.asStateFlow()
    private val _serverDispatchedTasks = MutableSharedFlow<Task>(extraBufferCapacity = 8)
    /** Emits once when the server reaches a reminder's scheduled dispatch attempt. */
    val serverDispatchedTasks: SharedFlow<Task> = _serverDispatchedTasks.asSharedFlow()
    private var realtimeJob: Job? = null

    init {
        start()
    }

    private fun start() {
        if (realtimeJob != null) return
        realtimeJob = viewModelScope.launch {
            runCatching { repository.fetchTasks() }
                .onSuccess { tasks -> _state.update { it.copy(tasks = tasks, isLoading = false, error = null) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.toUserMessage()) } }

            var didReceiveInitialRealtimeSnapshot = false
            var notificationStates = emptyMap<String, String>()
            val locallyPresentedTaskIds = mutableSetOf<String>()
            repository.observeTasks()
                .catch { error -> _state.update { it.copy(error = error.toUserMessage()) } }
                .collect { tasks ->
                    if (didReceiveInitialRealtimeSnapshot) {
                        tasks.forEach { task ->
                            if (task.notificationState == NOTIFICATION_PENDING) {
                                // Editing/rescheduling deliberately creates a new reminder attempt.
                                locallyPresentedTaskIds.remove(task.id)
                            }
                            if (
                                !task.completed &&
                                task.notificationState.isTerminalDispatchState() &&
                                notificationStates[task.id] != task.notificationState &&
                                locallyPresentedTaskIds.add(task.id)
                            ) {
                                _serverDispatchedTasks.tryEmit(task)
                            }
                        }
                        locallyPresentedTaskIds.retainAll(tasks.mapTo(mutableSetOf(), Task::id))
                    }
                    notificationStates = tasks.associate { it.id to it.notificationState }
                    didReceiveInitialRealtimeSnapshot = true
                    _state.update { it.copy(tasks = tasks, isLoading = false, error = null) }
                }
        }
    }

    private fun stop() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    fun addTask(
        title: String,
        notifyAt: String,
        recurrenceRule: String? = null,
        onSuccess: () -> Unit = {},
    ) = mutate(onSuccess) {
        repository.createTask(title, notifyAt, recurrenceRule).also { created ->
            _state.update { state -> state.copy(tasks = state.tasks.upsert(created)) }
        }
    }

    fun toggleTask(task: Task, onSuccess: () -> Unit = {}) = mutate(onSuccess) {
        repository.setCompleted(task.id, !task.completed).also { updated ->
            _state.update { state -> state.copy(tasks = state.tasks.upsert(updated)) }
        }
    }

    fun updateTask(
        task: Task,
        title: String,
        notifyAt: String,
        recurrenceRule: String? = task.recurrenceRule,
        onSuccess: () -> Unit = {},
    ) = mutate(onSuccess) {
        val replacesRemainingRecurringSchedule = !task.completed && (task.recurrenceRule != null || recurrenceRule != null)
        val updated = if (replacesRemainingRecurringSchedule) {
            repository.replaceRecurringSchedule(task.id, title, notifyAt, recurrenceRule)
        } else {
            repository.updateTask(task.id, title, notifyAt, recurrenceRule)
        }
        _state.update { state ->
            state.copy(tasks = state.tasks.filterNot { it.id == task.id }.upsert(updated))
        }
    }

    fun deleteTask(task: Task, onSuccess: () -> Unit = {}) = mutate(onSuccess) {
        repository.deleteTask(task.id)
        _state.update { state -> state.copy(tasks = state.tasks.filterNot { it.id == task.id }) }
    }

    fun skipRecurringOccurrence(task: Task, onSuccess: () -> Unit = {}) = mutate(onSuccess) {
        repository.skipRecurringOccurrence(task.id)
        val refreshedTasks = repository.fetchTasks()
        _state.update { it.copy(tasks = refreshedTasks) }
    }

    fun stopRecurringReminder(task: Task, onSuccess: () -> Unit = {}) = mutate(onSuccess) {
        repository.stopRecurringReminder(task.id)
        val refreshedTasks = repository.fetchTasks()
        _state.update { it.copy(tasks = refreshedTasks) }
    }

    private fun mutate(onSuccess: () -> Unit = {}, operation: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { operation() }
                .onSuccess { onSuccess() }
                .onFailure { error -> _state.update { it.copy(error = error.toUserMessage()) } }
            _state.update { it.copy(isSaving = false) }
        }
    }

    override fun onCleared() {
        stop()
    }
}

private const val NOTIFICATION_PENDING = "pending"

private fun String.isTerminalDispatchState(): Boolean = this == "sent" || this == "failed"

private fun List<Task>.upsert(incoming: Task): List<Task> =
    (filterNot { it.id == incoming.id } + incoming).sortedBy(Task::notifyAt)

private fun Throwable.toUserMessage(): String = message ?: "Tasks could not be updated. Please try again."
