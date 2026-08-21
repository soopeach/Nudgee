package com.soopeach.nudgee.client.ui.feedback

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformToast(): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) { { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }
}
