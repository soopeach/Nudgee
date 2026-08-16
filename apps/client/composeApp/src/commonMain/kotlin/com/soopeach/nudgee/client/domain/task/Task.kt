package com.soopeach.nudgee.client.domain.task

data class Task(
    val id: String,
    val title: String,
    val notifyAt: String,
    val completed: Boolean,
    val completedAt: String?,
    val notificationState: String,
)
