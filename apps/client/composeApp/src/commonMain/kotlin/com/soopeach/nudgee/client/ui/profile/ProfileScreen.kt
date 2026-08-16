package com.soopeach.nudgee.client.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
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
fun AccountSettingsSection(email: String?, avatarUrl: String?, onSignOut: () -> Unit) {
    val initial = email?.firstOrNull()?.uppercase() ?: "N"
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
        }
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
