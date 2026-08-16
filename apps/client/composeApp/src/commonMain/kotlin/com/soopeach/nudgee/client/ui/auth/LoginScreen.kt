package com.soopeach.nudgee.client.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import nudgee_client.composeapp.generated.resources.Res
import nudgee_client.composeapp.generated.resources.google_sign_in_standard_light
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(
    isConfigured: Boolean,
    isSigningIn: Boolean,
    message: String?,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NudgeeColors.softSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nudgee",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = NudgeeColors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "One gentle reminder, everywhere you need it.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = NudgeeColors.mutedInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NudgeeColors.mint),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isConfigured) "Keep your nudges together." else "Supabase setup is needed first.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                GoogleSignInButton(
                    enabled = isConfigured && !isSigningIn,
                    onClick = onSignIn,
                )
                if (isSigningIn) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Opening Google…",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = NudgeeColors.mutedInk,
                        textAlign = TextAlign.Center,
                    )
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = NudgeeColors.mutedInk,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Uses Google's pre-approved light standard button artwork without altering its aspect ratio. */
@Composable
private fun GoogleSignInButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.google_sign_in_standard_light),
            contentDescription = "Sign in with Google",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(177.dp)
                .height(40.dp)
                .alpha(if (enabled) 1f else 0.55f)
                .clickable(enabled = enabled, onClick = onClick),
        )
    }
}
