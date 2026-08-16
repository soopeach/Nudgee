package com.soopeach.nudgee.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import com.soopeach.nudgee.client.notifications.AndroidFcmDeviceRegistrar
import com.soopeach.nudgee.client.notifications.NudgeeNotificationPresenter
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NudgeeSupabase.client?.handleDeeplinks(intent)
        NudgeeNotificationPresenter.ensureChannel(this)
        registerFcmTokenForSignedInUser()
        setContent { App() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NudgeeSupabase.client?.handleDeeplinks(intent)
        registerFcmTokenForSignedInUser()
    }

    private fun registerFcmTokenForSignedInUser() {
        activityScope.launch { AndroidFcmDeviceRegistrar.registerCurrentToken() }
    }
}
