package com.soopeach.nudgee.client.ui.feedback

import androidx.compose.runtime.Composable

/** Android uses the OS toast; other targets can provide their own native feedback later. */
@Composable
expect fun rememberPlatformToast(): (String) -> Unit
