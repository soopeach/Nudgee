package com.soopeach.nudgee.client.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors

/** Shown while Supabase restores a session or finishes an OAuth redirect. */
@Composable
fun NudgeeStartupScreen(message: String = "Loading your nudges…") {
    Box(
        modifier = Modifier.fillMaxSize().background(NudgeeColors.softSurface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(64.dp).background(NudgeeColors.lavender, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("n", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
            }
            Text("Nudgee", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = NudgeeColors.ink,
                strokeWidth = 2.dp,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
        }
    }
}
