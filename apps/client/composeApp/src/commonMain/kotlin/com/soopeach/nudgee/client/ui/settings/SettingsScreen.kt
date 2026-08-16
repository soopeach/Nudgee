package com.soopeach.nudgee.client.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.data.notifications.NotificationPermissionStatus
import com.soopeach.nudgee.client.data.notifications.platformNotificationSetupDescription
import com.soopeach.nudgee.client.data.notifications.rememberNotificationPermissionController
import com.soopeach.nudgee.client.ui.designsystem.NudgeeBackButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import com.soopeach.nudgee.client.ui.profile.AccountSettingsSection
import kotlinx.datetime.TimeZone

@Composable
fun SettingsScreen(
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val uriHandler = LocalUriHandler.current
    if (selectedCategory == SettingsCategory.Notifications) {
        NotificationSettingsScreen(
            onBack = { selectedCategory = null },
            contentPadding = contentPadding,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
                Text("Make Nudgee feel right for you.", style = MaterialTheme.typography.bodyLarge, color = NudgeeColors.mutedInk)
            }
        }

        item { SettingsSectionLabel("Notifications") }
        item {
            SettingsCard(
                title = "Reminder delivery",
                accent = NudgeeColors.sky,
                onClick = { selectedCategory = SettingsCategory.Notifications },
            ) {
                Text(
                    "Check this device’s delivery setup and how Nudgee sends reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NudgeeColors.mutedInk,
                )
            }
        }

        item { SettingsSectionLabel("Planning") }
        item {
            SettingsCard(title = "Your timezone", accent = NudgeeColors.mint) {
                Text(
                    TimeZone.currentSystemDefault().id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text("New reminders are saved using this device timezone.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            }
        }
        item {
            SettingsCard(title = "Home focus", accent = NudgeeColors.lavender) {
                Text("Today stays focused on current nudges. Browse completed and past tasks in Calendar.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            }
        }

        item { SettingsSectionLabel("Account") }
        item { AccountSettingsSection(email = email, avatarUrl = avatarUrl, onSignOut = onSignOut) }

        item { SettingsSectionLabel("About Nudgee") }
        item {
            SettingsCard(title = "Nudgee for mobile", accent = NudgeeColors.periwinkle) {
                Text("Version 0.1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                Spacer(Modifier.height(4.dp))
                Text("Your tasks are isolated to your account with Supabase row-level security.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            }
        }
        item {
            SettingsCard(
                title = "Support Nudgee",
                accent = NudgeeColors.mint,
                onClick = { uriHandler.openUri("https://buymeacoffee.com/hsjeon584z") },
            ) {
                Text("Buy me a coffee and help Nudgee grow.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = NudgeeColors.mutedInk,
    )
}

@Composable
private fun NotificationSettingsScreen(
    onBack: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val permissionController = rememberNotificationPermissionController()
    var permissionStatus by remember { mutableStateOf<NotificationPermissionStatus?>(null) }
    var statusRefreshVersion by remember { mutableStateOf(0) }

    LaunchedEffect(permissionController, statusRefreshVersion) {
        permissionStatus = permissionController.status()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NudgeeBackButton(label = "Settings", onClick = onBack)
        Text("Notifications", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
        Text("Manage how this device receives Nudgee reminders.", style = MaterialTheme.typography.bodyLarge, color = NudgeeColors.mutedInk)

        SettingsCard(title = "Notification permission", accent = permissionStatus.accentColor()) {
            Text(
                permissionStatus.title(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NudgeeColors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                permissionStatus.description(),
                style = MaterialTheme.typography.bodyMedium,
                color = NudgeeColors.mutedInk,
            )
            if (
                permissionStatus == NotificationPermissionStatus.Enabled ||
                permissionStatus == NotificationPermissionStatus.Disabled ||
                permissionStatus == NotificationPermissionStatus.Unavailable
            ) {
                Spacer(Modifier.height(14.dp))
                NudgeeButton(
                    label = if (permissionStatus == NotificationPermissionStatus.Enabled) {
                        "Open system notification settings"
                    } else if (permissionStatus == NotificationPermissionStatus.Unavailable) {
                        "Open system notification settings"
                    } else {
                        "Turn on notifications"
                    },
                    onClick = {
                        if (
                            permissionStatus == NotificationPermissionStatus.Enabled ||
                            permissionStatus == NotificationPermissionStatus.Unavailable
                        ) {
                            permissionController.openSystemSettings()
                        } else {
                            permissionController.requestPermission()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = if (
                        permissionStatus == NotificationPermissionStatus.Enabled ||
                        permissionStatus == NotificationPermissionStatus.Unavailable
                    ) {
                        NudgeeButtonStyle.Secondary
                    } else {
                        NudgeeButtonStyle.Primary
                    },
                )
                if (permissionStatus == NotificationPermissionStatus.Disabled) {
                    Spacer(Modifier.height(8.dp))
                    NudgeeButton(
                        label = "Open system notification settings",
                        onClick = permissionController::openSystemSettings,
                        modifier = Modifier.fillMaxWidth(),
                        style = NudgeeButtonStyle.Secondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                NudgeeTextButton(
                    label = "Refresh permission status",
                    onClick = { statusRefreshVersion++ },
                )
                if (permissionController.supportsLocalTestNotification) {
                    NudgeeTextButton(
                        label = "Send test notification",
                        onClick = permissionController::sendLocalTestNotification,
                    )
                }
            }
        }

        SettingsCard(title = "Reminder delivery", accent = NudgeeColors.sky) {
            Text(
                "Nudgee schedules reminders securely on the server, then sends them to each registered device.",
                style = MaterialTheme.typography.bodyMedium,
                color = NudgeeColors.mutedInk,
            )
        }
        SettingsCard(title = "This device", accent = NudgeeColors.lavender) {
            Text("Push delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
            Spacer(Modifier.height(6.dp))
            Text(platformNotificationSetupDescription(), style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
        }
    }
}

private fun NotificationPermissionStatus?.title(): String = when (this) {
    NotificationPermissionStatus.Enabled -> "Notifications are on"
    NotificationPermissionStatus.Disabled -> "Notifications are off"
    NotificationPermissionStatus.Unavailable -> "System check unavailable"
    null -> "Checking notification permission…"
}

private fun NotificationPermissionStatus?.description(): String = when (this) {
    NotificationPermissionStatus.Enabled -> "Nudgee can deliver reminders on this device. You can review channel options in system settings."
    NotificationPermissionStatus.Disabled -> "Allow notifications in the system prompt or open this app’s notification settings to turn them back on."
    NotificationPermissionStatus.Unavailable -> "This platform does not expose notification permission status to Nudgee. You can review it in system settings."
    null -> "Reading the current operating-system notification permission."
}

private fun NotificationPermissionStatus?.accentColor() = when (this) {
    NotificationPermissionStatus.Enabled -> NudgeeColors.mint
    NotificationPermissionStatus.Disabled -> NudgeeColors.sky
    NotificationPermissionStatus.Unavailable, null -> NudgeeColors.periwinkle
}

private enum class SettingsCategory { Notifications }

@Composable
private fun SettingsCard(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    NudgeeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = shape,
        containerColor = androidx.compose.ui.graphics.Color.White,
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(accent, RoundedCornerShape(4.dp)))
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
                if (onClick != null) Text("›", style = MaterialTheme.typography.titleLarge, color = NudgeeColors.mutedInk)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
