package com.soopeach.nudgee.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import com.soopeach.nudgee.client.data.notifications.registerPlatformPushToken
import com.soopeach.nudgee.client.data.tasks.SupabaseTaskRepository
import com.soopeach.nudgee.client.ui.auth.LoginScreen
import com.soopeach.nudgee.client.ui.shell.AuthenticatedNudgeeScreen
import com.soopeach.nudgee.client.ui.startup.NudgeeStartupScreen
import com.soopeach.nudgee.client.ui.notifications.NotificationPermissionPrompt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun App() {
    NudgeeTheme {
        val supabase = NudgeeSupabase.client
        val coroutineScope = rememberCoroutineScope()
        val authViewModel: AuthViewModel = viewModel { AuthViewModel() }
        val authState by authViewModel.state.collectAsState()
        // Collecting this flow makes the composition switch to Home after the
        // platform deep-link handler exchanges the OAuth code for a session.
        val sessionStatus by supabase?.auth?.sessionStatus?.collectAsState()
            ?: remember { mutableStateOf<SessionStatus?>(null) }
        val isSignedIn = sessionStatus is SessionStatus.Authenticated
        val isRestoringSession = supabase != null &&
            (sessionStatus == null || sessionStatus is SessionStatus.Initializing)

        LaunchedEffect(isSignedIn) {
            if (isSignedIn) {
                authViewModel.completeSignIn()
                registerPlatformPushToken()
            }
        }

        // Opening the provider browser returns before its deep link has finished exchanging the
        // authorization code. Keep the app on the transition screen instead of briefly rendering
        // Login again. The timeout gives a cancelled external flow a way back to Login.
        LaunchedEffect(authState.isSigningIn, isSignedIn) {
            if (authState.isSigningIn && !isSignedIn) {
                delay(OAUTH_TRANSITION_TIMEOUT_MILLIS)
                if (!isSignedIn) {
                    authViewModel.failSignIn("Sign-in was not completed. Please try again.")
                }
            }
        }

        when {
            isRestoringSession -> NudgeeStartupScreen()
            authState.isSigningIn -> NudgeeStartupScreen(message = "Signing you in…")
            isSignedIn -> {
            val configuredSupabase = requireNotNull(supabase)
            AuthenticatedNudgeeScreen(
                repository = remember(configuredSupabase) { SupabaseTaskRepository(configuredSupabase) },
                email = configuredSupabase.auth.currentUserOrNull()?.email,
                avatarUrl = configuredSupabase.auth.currentUserOrNull()?.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                    ?: configuredSupabase.auth.currentUserOrNull()?.userMetadata?.get("picture")?.jsonPrimitive?.contentOrNull,
                onSignOut = {
                    coroutineScope.launch {
                        // A server-side account deletion revokes the Auth user before
                        // this callback runs. Always clear local storage even if the
                        // remote sign-out endpoint therefore rejects the old token.
                        runCatching { configuredSupabase.auth.signOut() }
                        configuredSupabase.auth.clearSession()
                    }
                },
            )
            NotificationPermissionPrompt()
            }
            else -> {
            LoginScreen(
                isConfigured = supabase != null,
                isSigningIn = authState.isSigningIn,
                message = authState.message ?: if (supabase == null) "Add the public Supabase URL and publishable key, then rebuild." else null,
                onSignIn = {
                    if (supabase == null) return@LoginScreen
                    coroutineScope.launch {
                        authViewModel.beginSignIn()
                        runCatching { supabase.auth.signInWith(Google) }
                            .onFailure {
                                authViewModel.failSignIn(it.message ?: "Google sign-in could not start.")
                            }
                    }
                },
            )
            }
        }
    }
}

private data class AuthUiState(val isSigningIn: Boolean = false, val message: String? = null)

private class AuthViewModel : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()
    fun beginSignIn() { _state.value = AuthUiState(isSigningIn = true) }
    fun completeSignIn() { _state.value = AuthUiState() }
    fun failSignIn(message: String) { _state.value = AuthUiState(message = message) }
}

private const val OAUTH_TRANSITION_TIMEOUT_MILLIS = 90_000L
