package com.soopeach.nudgee.client.ui.shell

import androidx.compose.runtime.Composable
import com.soopeach.nudgee.client.domain.task.TaskRepository
import com.soopeach.nudgee.client.ui.home.MobileHomeScreen

@Composable
actual fun AuthenticatedNudgeeScreen(
    repository: TaskRepository,
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
) {
    MobileHomeScreen(repository = repository, email = email, avatarUrl = avatarUrl, onSignOut = onSignOut)
}
