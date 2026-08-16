package com.soopeach.nudgee.client.data.tasks

import com.soopeach.nudgee.client.domain.task.Task
import com.soopeach.nudgee.client.domain.task.TaskRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresListDataFlow
import io.github.jan.supabase.realtime.PrimaryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseTaskRepository(
    private val supabase: SupabaseClient,
) : TaskRepository {
    override suspend fun fetchTasks(): List<Task> = supabase
        .from(TASKS_TABLE)
        .select {
            order(column = "notify_at", order = Order.ASCENDING)
        }
        .decodeList<TaskDto>()
        .map(TaskDto::toDomain)

    override fun observeTasks(): Flow<List<Task>> = callbackFlow {
        val channel = supabase.channel("tasks:${currentUserId()}")
        val changes: Job = launch {
            channel.postgresListDataFlow<TaskDto>(
                schema = "public",
                table = TASKS_TABLE,
                primaryKey = PrimaryKey("id") { task: TaskDto -> task.id },
            ).collect { tasks ->
                trySend(tasks.map(TaskDto::toDomain).sortedBy(Task::notifyAt))
            }
        }
        channel.subscribe(blockUntilSubscribed = true)

        awaitClose {
            changes.cancel()
            launch { channel.unsubscribe() }
        }
    }

    override suspend fun createTask(title: String, notifyAt: String): Task {
        return supabase
            .from(TASKS_TABLE)
            .insert(
                TaskCreateDto(
                    userId = currentUserId(),
                    title = title.trim(),
                    notifyAt = notifyAt,
                    timezone = TimeZone.currentSystemDefault().id,
                ),
            ) {
                select()
            }
            .decodeSingle<TaskDto>()
            .toDomain()
    }

    override suspend fun setCompleted(taskId: String, completed: Boolean): Task {
        return supabase
            .from(TASKS_TABLE)
            .update(
                TaskCompletionDto(
                    completed = completed,
                    completedAt = if (completed) Clock.System.now().toString() else null,
                ),
            ) {
                select()
                filter { eq("id", taskId) }
            }
            .decodeSingle<TaskDto>()
            .toDomain()
    }

    override suspend fun updateTask(taskId: String, title: String, notifyAt: String): Task {
        return supabase
            .from(TASKS_TABLE)
            .update(
                TaskUpdateDto(
                    title = title.trim(),
                    notifyAt = notifyAt,
                    timezone = TimeZone.currentSystemDefault().id,
                    notificationState = "pending",
                ),
            ) {
                select()
                filter { eq("id", taskId) }
            }
            .decodeSingle<TaskDto>()
            .toDomain()
    }

    override suspend fun deleteTask(taskId: String) {
        supabase
            .from(TASKS_TABLE)
            .delete {
                filter { eq("id", taskId) }
            }
    }

    private fun currentUserId(): String = requireNotNull(supabase.auth.currentUserOrNull()?.id) {
        "Your session has expired. Sign in again."
    }

    private companion object {
        const val TASKS_TABLE = "tasks"
    }
}

@Serializable
private data class TaskDto(
    val id: String,
    val title: String,
    @SerialName("notify_at") val notifyAt: String,
    val completed: Boolean,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("notification_state") val notificationState: String = "pending",
)

@Serializable
private data class TaskCreateDto(
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("notify_at") val notifyAt: String,
    val timezone: String,
    val completed: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("notification_state") val notificationState: String = "pending",
)

@Serializable
private data class TaskCompletionDto(
    val completed: Boolean,
    @SerialName("completed_at") val completedAt: String?,
)

@Serializable
private data class TaskUpdateDto(
    val title: String,
    @SerialName("notify_at") val notifyAt: String,
    val timezone: String,
    @SerialName("notification_state") val notificationState: String,
)

private fun TaskDto.toDomain() = Task(
    id = id,
    title = title,
    notifyAt = notifyAt,
    completed = completed,
    completedAt = completedAt,
    notificationState = notificationState,
)
