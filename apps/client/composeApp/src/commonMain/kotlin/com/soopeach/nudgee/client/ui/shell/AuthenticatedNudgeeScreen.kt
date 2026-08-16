package com.soopeach.nudgee.client.ui.shell

import androidx.compose.runtime.Composable
import com.soopeach.nudgee.client.domain.task.TaskRepository

/** Selects the platform-appropriate authenticated Nudgee workspace. */
@Composable
expect fun AuthenticatedNudgeeScreen(
    repository: TaskRepository,
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
)
