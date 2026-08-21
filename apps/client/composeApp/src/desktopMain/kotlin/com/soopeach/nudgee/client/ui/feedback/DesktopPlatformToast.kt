package com.soopeach.nudgee.client.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformToast(): (String) -> Unit = remember { {} }
