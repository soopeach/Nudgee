package com.soopeach.nudgee.client.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors

@Composable
fun NudgeeBottomNavigation(
    current: NudgeeDestination,
    onNavigate: (NudgeeDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .shadow(14.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NudgeeDestination.entries.forEach { destination ->
            NudgeeNavigationItem(
                destination = destination,
                selected = current == destination,
                onClick = { onNavigate(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NudgeeNavigationItem(
    destination: NudgeeDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(21.dp)
    val iconColor = if (selected) NudgeeColors.ink else NudgeeColors.mutedInk
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(if (selected) NudgeeColors.mint else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NudgeeNavIcon(destination = destination, color = iconColor, modifier = Modifier.size(27.dp))
        Text(
            text = destination.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = iconColor,
        )
    }
}

@Composable
private fun NudgeeNavIcon(destination: NudgeeDestination, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (destination) {
            NudgeeDestination.Home -> drawHomeIcon(color)
            NudgeeDestination.Calendar -> drawCalendarIcon(color)
            NudgeeDestination.Settings -> drawSettingsIcon(color)
        }
    }
}

private fun DrawScope.drawHomeIcon(color: Color) {
    val stroke = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(size.width * .16f, size.height * .47f)
        lineTo(size.width * .5f, size.height * .18f)
        lineTo(size.width * .84f, size.height * .47f)
        lineTo(size.width * .76f, size.height * .47f)
        lineTo(size.width * .76f, size.height * .82f)
        lineTo(size.width * .24f, size.height * .82f)
        lineTo(size.width * .24f, size.height * .47f)
        close()
    }
    drawPath(path, color, style = stroke)
    drawLine(color, Offset(size.width * .43f, size.height * .82f), Offset(size.width * .43f, size.height * .62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width * .57f, size.height * .82f), Offset(size.width * .57f, size.height * .62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawCalendarIcon(color: Color) {
    val stroke = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(color, topLeft = Offset(size.width * .16f, size.height * .22f), size = Size(size.width * .68f, size.height * .62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = stroke)
    drawLine(color, Offset(size.width * .16f, size.height * .43f), Offset(size.width * .84f, size.height * .43f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width * .34f, size.height * .12f), Offset(size.width * .34f, size.height * .31f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width * .66f, size.height * .12f), Offset(size.width * .66f, size.height * .31f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    listOf(.34f, .5f, .66f).forEach { x ->
        drawCircle(color, radius = 1.8.dp.toPx(), center = Offset(size.width * x, size.height * .62f))
    }
}

private fun DrawScope.drawSettingsIcon(color: Color) {
    val strokeWidth = 1.75.dp.toPx()
    val xStart = size.width * .17f
    val xEnd = size.width * .83f
    val rows = listOf(.28f to .63f, .5f to .37f, .72f to .57f)
    rows.forEach { (yFactor, knobFactor) ->
        val y = size.height * yFactor
        drawLine(color, Offset(xStart, y), Offset(xEnd, y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        val knobSize = 6.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * knobFactor - knobSize / 2, y - knobSize / 2),
            size = Size(knobSize, knobSize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
        )
    }
}
