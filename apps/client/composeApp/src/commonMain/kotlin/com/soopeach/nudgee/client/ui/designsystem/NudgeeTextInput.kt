package com.soopeach.nudgee.client.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors

@Composable
fun NudgeeTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = minLines,
        enabled = enabled,
        textStyle = TextStyle(
            color = if (enabled) NudgeeColors.ink else NudgeeColors.mutedInk,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
        ),
        modifier = modifier
            .clip(shape)
            .background(if (enabled) Color.White else NudgeeColors.softSurface)
            .border(1.dp, if (enabled) NudgeeColors.line else NudgeeColors.line.copy(alpha = 0.65f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NudgeeColors.mutedInk.copy(alpha = if (enabled) 1f else 0.72f),
                )
            }
            innerTextField()
        },
    )
}
