package com.soopeach.nudgee.client.data.advertising

import androidx.compose.runtime.Composable

enum class RewardedAdStatus {
    Loading,
    Ready,
    Unavailable,
}

/**
 * Platform boundary for rewarded-ad presentation. A client callback never grants
 * credits; production rewards will be issued only after AdMob SSV reaches Nudgee.
 */
interface RewardedAdController {
    val status: RewardedAdStatus

    fun load()
    /**
     * The callback only means that the full-screen ad closed. It is never a
     * reward grant: the server-side AdMob callback remains the source of truth.
     */
    fun show(onDismissed: () -> Unit = {})
}

@Composable
expect fun rememberRewardedAdController(): RewardedAdController?
