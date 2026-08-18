package com.soopeach.nudgee.client

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
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
        configureAdMobTestDevice()
        MobileAds.initialize(this)
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

    private fun configureAdMobTestDevice() {
        val testDeviceId = BuildConfig.ADMOB_TEST_DEVICE_ID.trim()
        if (!BuildConfig.DEBUG || testDeviceId.isEmpty()) return

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(testDeviceId))
                .build(),
        )
        Log.i(AD_MOB_TAG, "AdMob debug test device configured.")
    }

    private companion object {
        const val AD_MOB_TAG = "NudgeeAds"
    }
}
