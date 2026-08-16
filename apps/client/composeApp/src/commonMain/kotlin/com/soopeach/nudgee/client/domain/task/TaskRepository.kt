package com.soopeach.nudgee.client.domain.task

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun fetchTasks(): List<Task>
    fun observeTasks(): Flow<List<Task>>
    suspend fun createTask(title: String, notifyAt: String): Task
    suspend fun updateTask(taskId: String, title: String, notifyAt: String): Task
    suspend fun setCompleted(taskId: String, completed: Boolean): Task
    suspend fun deleteTask(taskId: String)
}
