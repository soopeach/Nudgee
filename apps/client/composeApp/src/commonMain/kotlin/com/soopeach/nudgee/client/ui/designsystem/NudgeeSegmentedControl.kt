package com.soopeach.nudgee.client.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors

@Composable
fun NudgeeSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = NudgeeColors.lavenderSurface,
) {
    require(options.isNotEmpty()) { "NudgeeSegmentedControl needs at least one option." }
    val usesScrollableOptions = options.size > 4

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(7.dp)
            .then(
                if (usesScrollableOptions) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val bringIntoViewRequester = remember(option) { BringIntoViewRequester() }
            LaunchedEffect(selected, usesScrollableOptions) {
                if (selected && usesScrollableOptions) bringIntoViewRequester.bringIntoView()
            }
            Box(
                modifier = Modifier
                    .then(if (usesScrollableOptions) Modifier.widthIn(min = 108.dp) else Modifier.weight(1f))
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) selectedColor else NudgeeColors.softSurface.copy(alpha = 0.8f))
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .clickable { onOptionSelected(index) }
                    .padding(vertical = 11.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) NudgeeColors.ink else NudgeeColors.mutedInk,
                    maxLines = if (usesScrollableOptions) 1 else 2,
                    softWrap = !usesScrollableOptions,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
