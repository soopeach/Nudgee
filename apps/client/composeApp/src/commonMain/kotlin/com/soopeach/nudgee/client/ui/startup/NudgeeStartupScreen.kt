package com.soopeach.nudgee.client.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.ui.designsystem.NudgeeBrandMark
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface

/** Shown while Supabase restores a session or completes an OAuth redirect. */
@Composable
fun NudgeeStartupScreen(message: String = "Loading your nudges…") {
    Box(modifier = Modifier.fillMaxSize().background(NudgeeColors.softSurface)) {
        Box(Modifier.size(180.dp).background(NudgeeColors.sky.copy(alpha = 0.48f), CircleShape).align(Alignment.TopStart))
        Box(Modifier.size(150.dp).background(NudgeeColors.mint.copy(alpha = 0.7f), CircleShape).align(Alignment.BottomEnd))
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NudgeeBrandMark(size = 70.dp)
            Spacer(Modifier.size(18.dp))
            Text("Nudgee", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
            Spacer(Modifier.size(7.dp))
            Text("Small plans, gently held.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            Spacer(Modifier.size(30.dp))
            NudgeeSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NudgeeColors.lavender)
                    Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                }
            }
        }
    }
}
