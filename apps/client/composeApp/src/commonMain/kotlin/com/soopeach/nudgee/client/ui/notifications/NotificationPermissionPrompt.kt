package com.soopeach.nudgee.client.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.data.notifications.NotificationPermissionStatus
import com.soopeach.nudgee.client.data.notifications.rememberNotificationPermissionController
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import kotlinx.coroutines.delay

/**
 * Reminders are Nudgee's core promise, so a disabled OS permission is surfaced
 * each new app session until the user enables it. "Not now" only dismisses the
 * prompt for the current session.
 */
@Composable
fun NotificationPermissionPrompt() {
    val controller = rememberNotificationPermissionController()
    var status by remember { mutableStateOf<NotificationPermissionStatus?>(null) }
    var dismissedForSession by remember { mutableStateOf(false) }

    LaunchedEffect(controller, dismissedForSession) {
        if (dismissedForSession) return@LaunchedEffect
        while (true) {
            status = controller.status()
            if (status != NotificationPermissionStatus.Disabled) break
            delay(750)
        }
    }

    if (!dismissedForSession && status == NotificationPermissionStatus.Disabled) {
        Dialog(onDismissRequest = { dismissedForSession = true }) {
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
                            .size(46.dp)
                            .background(NudgeeColors.sky.copy(alpha = 0.62f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✦", style = MaterialTheme.typography.titleLarge, color = NudgeeColors.ink)
                    }
                    Text(
                        "Turn on reminders",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NudgeeColors.ink,
                    )
                    Text(
                        "Nudgee needs notification permission to remind you at the time you chose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NudgeeColors.mutedInk,
                    )
                    NudgeeButton(
                        label = "Turn on notifications",
                        onClick = controller::requestPermission,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NudgeeButton(
                        label = "Open system settings",
                        onClick = controller::openSystemSettings,
                        modifier = Modifier.fillMaxWidth(),
                        style = NudgeeButtonStyle.Secondary,
                    )
                    NudgeeTextButton(
                        label = "Not now",
                        onClick = { dismissedForSession = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
