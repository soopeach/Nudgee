package com.soopeach.nudgee.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import com.soopeach.nudgee.client.data.notifications.registerPlatformPushToken
import com.soopeach.nudgee.client.data.tasks.SupabaseTaskRepository
import com.soopeach.nudgee.client.ui.auth.LoginScreen
import com.soopeach.nudgee.client.ui.shell.AuthenticatedNudgeeScreen
import com.soopeach.nudgee.client.ui.startup.NudgeeStartupScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun App() {
    NudgeeTheme {
        val supabase = NudgeeSupabase.client
        val coroutineScope = rememberCoroutineScope()
        var isSigningIn by remember { mutableStateOf(false) }
        var signInMessage by remember { mutableStateOf<String?>(null) }
        // Collecting this flow makes the composition switch to Home after the
        // platform deep-link handler exchanges the OAuth code for a session.
        val sessionStatus by supabase?.auth?.sessionStatus?.collectAsState()
            ?: remember { mutableStateOf<SessionStatus?>(null) }
        val isSignedIn = sessionStatus is SessionStatus.Authenticated
        val isRestoringSession = supabase != null &&
            (sessionStatus == null || sessionStatus is SessionStatus.Initializing)

        LaunchedEffect(isSignedIn) {
            if (isSignedIn) {
                isSigningIn = false
                registerPlatformPushToken()
            }
        }

        // Opening the provider browser returns before its deep link has finished exchanging the
        // authorization code. Keep the app on the transition screen instead of briefly rendering
        // Login again. The timeout gives a cancelled external flow a way back to Login.
        LaunchedEffect(isSigningIn, isSignedIn) {
            if (isSigningIn && !isSignedIn) {
                delay(OAUTH_TRANSITION_TIMEOUT_MILLIS)
                if (!isSignedIn) {
                    isSigningIn = false
                    signInMessage = "Sign-in was not completed. Please try again."
                }
            }
        }

        when {
            isRestoringSession -> NudgeeStartupScreen()
            isSigningIn -> NudgeeStartupScreen(message = "Signing you in…")
            isSignedIn -> {
            val configuredSupabase = requireNotNull(supabase)
            AuthenticatedNudgeeScreen(
                repository = remember(configuredSupabase) { SupabaseTaskRepository(configuredSupabase) },
                email = configuredSupabase.auth.currentUserOrNull()?.email,
                avatarUrl = configuredSupabase.auth.currentUserOrNull()?.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                    ?: configuredSupabase.auth.currentUserOrNull()?.userMetadata?.get("picture")?.jsonPrimitive?.contentOrNull,
                onSignOut = {
                    coroutineScope.launch { configuredSupabase.auth.signOut() }
                },
            )
            }
            else -> {
            LoginScreen(
                isConfigured = supabase != null,
                isSigningIn = isSigningIn,
                message = signInMessage ?: if (supabase == null) "Add the public Supabase URL and publishable key, then rebuild." else null,
                onSignIn = {
                    if (supabase == null) return@LoginScreen
                    coroutineScope.launch {
                        isSigningIn = true
                        signInMessage = null
                        runCatching { supabase.auth.signInWith(Google) }
                            .onFailure {
                                isSigningIn = false
                                signInMessage = it.message ?: "Google sign-in could not start."
                            }
                    }
                },
            )
            }
        }
    }
}

private const val OAUTH_TRANSITION_TIMEOUT_MILLIS = 90_000L
