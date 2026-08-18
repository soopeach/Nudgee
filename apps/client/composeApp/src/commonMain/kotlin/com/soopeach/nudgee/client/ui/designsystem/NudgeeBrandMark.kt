package com.soopeach.nudgee.client.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors

/** Shared compact mark used while the authenticated workspace is unavailable. */
@Composable
fun NudgeeBrandMark(
    size: Dp = 58.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(NudgeeColors.periwinkle, RoundedCornerShape(size * 0.34f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "n",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = NudgeeColors.ink,
        )
    }
}
