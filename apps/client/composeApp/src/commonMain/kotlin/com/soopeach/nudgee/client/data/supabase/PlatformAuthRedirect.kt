package com.soopeach.nudgee.client.data.supabase

import io.github.jan.supabase.auth.AuthConfig

/**
 * Configures the OAuth return path for each platform.
 *
 * Mobile returns through the Nudgee deep link, while Desktop uses Supabase's
 * loopback HTTP callback server so a completed browser sign-in reaches the
 * running JVM application.
 */
expect fun AuthConfig.configurePlatformAuthRedirect()
