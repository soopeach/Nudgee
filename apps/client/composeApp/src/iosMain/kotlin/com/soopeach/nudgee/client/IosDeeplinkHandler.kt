package com.soopeach.nudgee.client

import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import io.github.jan.supabase.auth.handleDeeplinks
import platform.Foundation.NSURL

fun handleNudgeeDeeplink(url: NSURL) {
    NudgeeSupabase.client?.handleDeeplinks(url)
}
