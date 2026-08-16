package com.soopeach.nudgee.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NudgeeColors {
    val lavender = Color(0xFF767BF2)
    val periwinkle = Color(0xFFB5BAFF)
    val sky = Color(0xFFAEE2FF)
    val mint = Color(0xFFD9F9DF)
    val ink = Color(0xFF191F28)
    val mutedInk = Color(0xFF6B7684)
    val softSurface = Color(0xFFF2F4F6)
    val line = Color(0xFFE5E8EB)
    val lavenderSurface = Color(0xFFF0F1FF)
    val progressTrack = Color(0xFFE1E5EE)
}

@Composable
fun NudgeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NudgeeColors.lavender,
            secondary = NudgeeColors.sky,
            tertiary = NudgeeColors.mint,
            background = NudgeeColors.softSurface,
            surface = Color.White,
            onPrimary = NudgeeColors.ink,
            onBackground = NudgeeColors.ink,
            onSurface = NudgeeColors.ink,
        ),
        content = content,
    )
}
