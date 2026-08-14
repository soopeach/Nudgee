package com.soopeach.nudgee.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NudgeeColors {
    val lavender = Color(0xFF9FA1FF)
    val periwinkle = Color(0xFFB5BAFF)
    val sky = Color(0xFFAEE2FF)
    val mint = Color(0xFFD9F9DF)
    val ink = Color(0xFF30345D)
    val mutedInk = Color(0xFF686D92)
    val softSurface = Color(0xFFF4F7FF)
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
