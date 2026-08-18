package com.soopeach.nudgee.client.data.advertising

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.soopeach.nudgee.client.BuildConfig
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import io.github.jan.supabase.auth.auth

private const val RewardedCreditCustomData = "nudgee_ai_credits_v1"
private const val AdMobLogTag = "NudgeeAds"

@Composable
actual fun rememberRewardedAdController(): RewardedAdController? {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { activity?.let(::AndroidRewardedAdController) }
}

private class AndroidRewardedAdController(
    private val activity: Activity,
) : RewardedAdController {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    override var status by mutableStateOf(RewardedAdStatus.Unavailable)
        private set

    override fun load() {
        if (isLoading || rewardedAd != null) return

        isLoading = true
        status = RewardedAdStatus.Loading
        val request = AdRequest.Builder().build()
        Log.i(AdMobLogTag, "Loading rewarded ad. isTestDevice=${request.isTestDevice(activity)}")
        RewardedAd.load(
            activity,
            BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    val userId = NudgeeSupabase.client
                        ?.auth
                        ?.currentUserOrNull()
                        ?.id
                        ?: run {
                            isLoading = false
                            status = RewardedAdStatus.Unavailable
                            return
                        }

                    ad.setServerSideVerificationOptions(
                        ServerSideVerificationOptions.Builder()
                            .setUserId(userId)
                            .setCustomData(RewardedCreditCustomData)
                            .build(),
                    )
                    rewardedAd = ad
                    isLoading = false
                    status = RewardedAdStatus.Ready
                    Log.i(AdMobLogTag, "Rewarded ad loaded with SSV options attached.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    status = RewardedAdStatus.Unavailable
                    Log.w(AdMobLogTag, "Rewarded ad failed to load: ${error.message}")
                }
            },
        )
    }

    override fun show(onDismissed: () -> Unit) {
        val ad = rewardedAd ?: run {
            load()
            return
        }

        rewardedAd = null
        status = RewardedAdStatus.Loading
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // A close is not a grant. The verified server-side AdMob callback
                // is the only path that changes a user's credit balance.
                onDismissed()
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                status = RewardedAdStatus.Unavailable
                onDismissed()
                load()
            }
        }
        ad.show(activity) { /* The SSV callback, not this client callback, grants credits. */ }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
