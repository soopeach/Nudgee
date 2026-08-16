package com.soopeach.nudgee.client.data.supabase

import io.github.jan.supabase.auth.AuthConfig

actual fun AuthConfig.configurePlatformAuthRedirect() {
    scheme = "nudgee"
    host = "auth"
    defaultRedirectUrl = "nudgee://auth"
}
