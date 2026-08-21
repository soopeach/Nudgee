package com.soopeach.nudgee.client.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soopeach.nudgee.client.NudgeeColors

@Composable
fun NudgeeRecurringDeletionDialog(
    taskTitle: String,
    onDismiss: () -> Unit,
    onDeleteOccurrence: () -> Unit,
    onStopFutureReminders: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NudgeeSurface(
            modifier = Modifier.widthIn(max = 390.dp).padding(horizontal = 24.dp),
            shape = RoundedCornerShape(30.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(NudgeeColors.lavenderSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↻", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.lavender)
                }
                Text("Manage repeating reminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink, textAlign = TextAlign.Center)
                Text(taskTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink, textAlign = TextAlign.Center)
                Text("Skip this reminder and keep the next one scheduled, or stop the repeating reminder altogether.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                NudgeeButton("Skip this occurrence", onDeleteOccurrence, Modifier.fillMaxWidth(), style = NudgeeButtonStyle.Secondary)
                NudgeeButton("Stop future reminders", onStopFutureReminders, Modifier.fillMaxWidth(), style = NudgeeButtonStyle.Dark)
                Text("Completed history stays in your account.", style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk, textAlign = TextAlign.Center)
                NudgeeTextButton("Cancel", onDismiss)
            }
        }
    }
}
