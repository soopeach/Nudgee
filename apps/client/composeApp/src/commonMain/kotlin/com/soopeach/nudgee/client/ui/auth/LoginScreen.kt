package com.soopeach.nudgee.client.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.ui.designsystem.NudgeeBrandMark
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface

@Composable
fun LoginScreen(
    isConfigured: Boolean,
    isSigningIn: Boolean,
    message: String?,
    onSignIn: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(NudgeeColors.softSurface),
    ) {
        // Soft color fields make this feel like a welcome screen, while keeping
        // contrast and the primary action unmistakably clear.
        Box(Modifier.size(210.dp).background(NudgeeColors.sky.copy(alpha = 0.5f), CircleShape).align(Alignment.TopEnd))
        Box(Modifier.size(170.dp).background(NudgeeColors.mint.copy(alpha = 0.7f), CircleShape).align(Alignment.BottomStart))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NudgeeBrandMark(size = 62.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Nudgee",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = NudgeeColors.mutedInk,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A gentle nudge,\nright on time.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = NudgeeColors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Keep your plans close and get reminded\nwhen it matters.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = NudgeeColors.mutedInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            NudgeeSurface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                shape = RoundedCornerShape(26.dp),
                containerColor = androidx.compose.ui.graphics.Color.White,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text(
                        text = if (isConfigured) "Your nudges, in every place." else "Supabase setup is needed first.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NudgeeColors.ink,
                        textAlign = TextAlign.Center,
                    )
                    GoogleSignInButton(enabled = isConfigured && !isSigningIn, isLoading = isSigningIn, onClick = onSignIn)
                    if (message != null) {
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NudgeeColors.lavenderSurface, RoundedCornerShape(13.dp))
                                .padding(11.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = NudgeeColors.mutedInk,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "By continuing, you agree to keep things\ndelightfully organised.",
                style = MaterialTheme.typography.bodySmall,
                color = NudgeeColors.mutedInk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Mirrors the web CTA while retaining the recognisable Google G mark. */
@Composable
private fun GoogleSignInButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .border(1.dp, NudgeeColors.line, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = NudgeeColors.ink)
            Spacer(Modifier.size(10.dp))
            Text("Opening Google…", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
        } else {
            GoogleGMark(modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(11.dp))
            Text("Continue with Google", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
        }
    }
}

/** Canvas keeps the Google mark portable: Android's common resource loader cannot render SVG files. */
@Composable
private fun GoogleGMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.19f
        val inset = stroke / 2f
        val arcSize = size.minDimension - stroke
        val topLeft = Offset(inset, inset)
        val style = Stroke(width = stroke, cap = StrokeCap.Butt)
        drawArc(color = androidx.compose.ui.graphics.Color(0xFFEA4335), startAngle = 205f, sweepAngle = 82f, useCenter = false, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(arcSize, arcSize), style = style)
        drawArc(color = androidx.compose.ui.graphics.Color(0xFF4285F4), startAngle = 287f, sweepAngle = 107f, useCenter = false, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(arcSize, arcSize), style = style)
        drawArc(color = androidx.compose.ui.graphics.Color(0xFF34A853), startAngle = 34f, sweepAngle = 88f, useCenter = false, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(arcSize, arcSize), style = style)
        drawArc(color = androidx.compose.ui.graphics.Color(0xFFFBBC05), startAngle = 122f, sweepAngle = 83f, useCenter = false, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(arcSize, arcSize), style = style)
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, size.height * 0.50f),
            end = Offset(size.width, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
    }
}
