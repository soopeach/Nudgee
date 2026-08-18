package com.soopeach.nudgee.client.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import coil3.compose.SubcomposeAsyncImage

@Composable
fun ProfileScreen(email: String?, avatarUrl: String?, onSignOut: () -> Unit) {
    val initial = email?.firstOrNull()?.uppercase() ?: "N"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
        NudgeeSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            containerColor = NudgeeColors.lavender.copy(alpha = 0.43f),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(initial = initial, avatarUrl = avatarUrl, size = 52.dp)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("Signed in with Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                    Text(email ?: "Account details unavailable", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                }
            }
        }
        Text("Your tasks stay private to this account through Supabase row-level security.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
        Spacer(Modifier.weight(1f))
        NudgeeButton(
            label = "Sign out",
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            style = NudgeeButtonStyle.Dark,
        )
    }
}

@Composable
fun AccountSettingsSection(
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    isDeletingAccount: Boolean,
    accountDeletionError: String?,
) {
    val initial = email?.firstOrNull()?.uppercase() ?: "N"
    var isDeleteDialogVisible by rememberSaveable { mutableStateOf(false) }
    NudgeeSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        containerColor = NudgeeColors.lavender.copy(alpha = 0.43f),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Account", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(initial = initial, avatarUrl = avatarUrl, size = 44.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Signed in with Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                    Text(email ?: "Account details unavailable", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
                }
            }
            Text("Your tasks stay private to this account.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            NudgeeButton(
                label = "Sign out",
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                style = NudgeeButtonStyle.Dark,
            )
            NudgeeTextButton(
                label = "Delete account",
                onClick = { isDeleteDialogVisible = true },
                modifier = Modifier.fillMaxWidth(),
                color = NudgeeColors.mutedInk,
            )
        }
    }
    if (isDeleteDialogVisible) {
        DeleteAccountConfirmationDialog(
            isDeleting = isDeletingAccount,
            error = accountDeletionError,
            onDismiss = { if (!isDeletingAccount) isDeleteDialogVisible = false },
            onConfirm = onDeleteAccount,
        )
    }
}

@Composable
private fun DeleteAccountConfirmationDialog(
    isDeleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isDeleting,
            dismissOnClickOutside = !isDeleting,
        ),
    ) {
        NudgeeSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFE9ED), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA14D5E))
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Delete your account?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                    Text("This action is permanent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFA14D5E))
                }
                Text(
                    "This permanently removes your tasks, reminders, device registrations, and AI reminder history. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NudgeeColors.mutedInk,
                )
                if (isDeleting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NudgeeColors.lavender.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp,
                            color = NudgeeColors.ink,
                        )
                        Text("Deleting your Nudgee data securely…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Confirmation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                    BasicTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(NudgeeColors.softSurface)
                        .border(1.dp, NudgeeColors.line, RoundedCornerShape(15.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NudgeeColors.ink, fontWeight = FontWeight.Bold),
                    singleLine = true,
                    enabled = !isDeleting,
                    decorationBox = { innerTextField ->
                        if (confirmation.isBlank()) {
                            Text("Type DELETE to continue", style = MaterialTheme.typography.bodyLarge, color = NudgeeColors.mutedInk)
                        }
                        innerTextField()
                    },
                    )
                }
                if (error != null) {
                    Text(
                        error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF0F2), RoundedCornerShape(13.dp))
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA14D5E),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    NudgeeButton(
                        label = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isDeleting,
                        style = NudgeeButtonStyle.Secondary,
                    )
                    DeleteAccountButton(
                        label = if (isDeleting) "Deleting…" else "Delete",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = confirmation == "DELETE" && !isDeleting,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Color(0xFFFFE1E7) else Color(0xFFF6EEF0))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = if (enabled) Color(0xFFA14D5E) else Color(0xFFC8ADB3))
    }
}

@Composable
private fun ProfileAvatar(initial: String, avatarUrl: String?, size: androidx.compose.ui.unit.Dp) {
    if (avatarUrl.isNullOrBlank()) {
        Box(Modifier.size(size)) { AvatarInitial(initial) }
        return
    }
    SubcomposeAsyncImage(
        model = avatarUrl,
        contentDescription = "Google profile picture",
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = { AvatarInitial(initial) },
        error = { AvatarInitial(initial) },
    )
}

@Composable
private fun AvatarInitial(initial: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(NudgeeColors.lavender, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
    }
}
