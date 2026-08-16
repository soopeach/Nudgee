package com.soopeach.nudgee.client.data.supabase

import io.github.jan.supabase.auth.AuthConfig

actual fun AuthConfig.configurePlatformAuthRedirect() {
    // Deliberately leave deep links and defaultRedirectUrl unset. On JVM,
    // Supabase-KT starts its built-in loopback callback server for OAuth.
}
