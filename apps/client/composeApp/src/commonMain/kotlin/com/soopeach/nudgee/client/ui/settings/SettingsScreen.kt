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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors
import com.soopeach.nudgee.client.data.advertising.RewardedAdController
import com.soopeach.nudgee.client.data.advertising.RewardedAdStatus
import com.soopeach.nudgee.client.data.advertising.rememberRewardedAdController
import com.soopeach.nudgee.client.data.supabase.NudgeeSupabase
import com.soopeach.nudgee.client.data.notifications.NotificationPermissionStatus
import com.soopeach.nudgee.client.data.notifications.platformNotificationSetupDescription
import com.soopeach.nudgee.client.data.notifications.rememberNotificationPermissionController
import com.soopeach.nudgee.client.ui.designsystem.NudgeeBackButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButton
import com.soopeach.nudgee.client.ui.designsystem.NudgeeButtonStyle
import com.soopeach.nudgee.client.ui.designsystem.NudgeeSurface
import com.soopeach.nudgee.client.ui.designsystem.NudgeeTextButton
import com.soopeach.nudgee.client.ui.profile.AccountSettingsSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import com.soopeach.nudgee.client.domain.reminder.NaturalLanguageReminderParser
import com.soopeach.nudgee.client.domain.reminder.ReminderParseUsage
import com.soopeach.nudgee.client.domain.reminder.SupabaseEdgeFunctionReminderParser
import com.soopeach.nudgee.client.domain.account.SupabaseAccountDeletionService

private class SettingsViewModel : ViewModel() {
    private val _selectedCategory = MutableStateFlow<SettingsCategory?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()
    fun show(category: SettingsCategory) { _selectedCategory.value = category }
    fun closeCategory() { _selectedCategory.value = null }
}

private class NotificationSettingsViewModel : ViewModel() {
    private val _permissionStatus = MutableStateFlow<NotificationPermissionStatus?>(null)
    val permissionStatus = _permissionStatus.asStateFlow()
    private val _refreshVersion = MutableStateFlow(0)
    val refreshVersion = _refreshVersion.asStateFlow()
    fun updatePermissionStatus(status: NotificationPermissionStatus) { _permissionStatus.value = status }
    fun refresh() { _refreshVersion.value++ }
}

private data class AiParseUsageUiState(
    val usage: ReminderParseUsage? = null,
    val isLoading: Boolean = false,
)

private class AiParseUsageViewModel(
    private val parser: NaturalLanguageReminderParser?,
) : ViewModel() {
    private val _state = MutableStateFlow(AiParseUsageUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        val activeParser = parser ?: return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching { activeParser.usage() }
                .onSuccess { usage -> _state.update { it.copy(usage = usage, isLoading = false) } }
                .onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }

    /**
     * An AdMob SSV callback can arrive after the full-screen ad closes. Poll a
     * small, bounded number of times so the UI catches up without ever making a
     * client-side reward decision.
     */
    fun refreshAfterRewardedAd() {
        val activeParser = parser ?: return
        val baselineBonusCredits = _state.value.usage?.bonusCredits ?: 0
        viewModelScope.launch {
            repeat(5) { attempt ->
                delay(if (attempt == 0) 2_500 else 3_000)
                runCatching { activeParser.usage() }
                    .onSuccess { usage ->
                        _state.update { it.copy(usage = usage, isLoading = false) }
                        if (usage.bonusCredits > baselineBonusCredits) return@launch
                    }
                    .onFailure { _state.update { it.copy(isLoading = false) } }
            }
        }
    }
}

private data class AccountDeletionUiState(
    val isDeleting: Boolean = false,
    val error: String? = null,
)

private class AccountDeletionViewModel(
    private val service: SupabaseAccountDeletionService?,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountDeletionUiState())
    val state = _state.asStateFlow()

    fun deleteAccount(onDeleted: () -> Unit) {
        val activeService = service ?: run {
            _state.value = AccountDeletionUiState(error = "Account deletion is unavailable until Supabase is configured.")
            return
        }
        _state.value = AccountDeletionUiState(isDeleting = true)
        viewModelScope.launch {
            runCatching { activeService.deleteCurrentAccount() }
                .onSuccess { onDeleted() }
                .onFailure { error -> _state.value = AccountDeletionUiState(error = error.message ?: "Nudgee could not delete your account. Please try again.") }
        }
    }
}

@Composable
fun SettingsScreen(
    email: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
    val aiUsageViewModel: AiParseUsageViewModel = viewModel {
        AiParseUsageViewModel(NudgeeSupabase.client?.let(::SupabaseEdgeFunctionReminderParser))
    }
    val accountDeletionViewModel: AccountDeletionViewModel = viewModel {
        AccountDeletionViewModel(NudgeeSupabase.client?.let(::SupabaseAccountDeletionService))
    }
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val aiUsageState by aiUsageViewModel.state.collectAsState()
    val accountDeletionState by accountDeletionViewModel.state.collectAsState()
    val rewardedAdController = rememberRewardedAdController()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        aiUsageViewModel.refresh()
    }
    LaunchedEffect(rewardedAdController, aiUsageState.usage?.remainingFreeParses) {
        if (aiUsageState.usage?.remainingFreeParses == 0) {
            rewardedAdController?.load()
        }
    }
    if (selectedCategory == SettingsCategory.Notifications) {
        NotificationSettingsScreen(
            onBack = viewModel::closeCategory,
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
                onClick = { viewModel.show(SettingsCategory.Notifications) },
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
        item {
            AccountSettingsSection(
                email = email,
                avatarUrl = avatarUrl,
                onSignOut = onSignOut,
                onDeleteAccount = { accountDeletionViewModel.deleteAccount(onSignOut) },
                isDeletingAccount = accountDeletionState.isDeleting,
                accountDeletionError = accountDeletionState.error,
            )
        }
        item {
            AiReminderUsageCard(
                usage = aiUsageState.usage,
                isLoading = aiUsageState.isLoading,
                rewardedAdController = rewardedAdController,
                onAdDismissed = aiUsageViewModel::refreshAfterRewardedAd,
            )
        }

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
        item {
            SettingsCard(
                title = "Privacy Policy",
                accent = NudgeeColors.sky,
                onClick = { uriHandler.openUri("https://nudgee-sage.vercel.app/privacy") },
            ) {
                Text("Learn how Nudgee handles your information.", style = MaterialTheme.typography.bodyMedium, color = NudgeeColors.mutedInk)
            }
        }
    }
}

@Composable
private fun AiReminderUsageCard(
    usage: ReminderParseUsage?,
    isLoading: Boolean,
    rewardedAdController: RewardedAdController?,
    onAdDismissed: () -> Unit,
) {
    SettingsCard(title = "AI reminder allowance", accent = NudgeeColors.periwinkle) {
        if (usage == null) {
            Text(
                text = if (isLoading) "Checking today’s free parses…" else "AI parse usage is unavailable right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = NudgeeColors.mutedInk,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${usage.remainingFreeParses} of ${usage.dailyFreeParseLimit} free reminders left today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                )
                Text(
                    if (usage.bonusCredits > 0) {
                        "${usage.bonusCredits} reward credit${if (usage.bonusCredits == 1) "" else "s"} ready too · resets at local midnight."
                    } else {
                        "Your free allowance resets at local midnight."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NudgeeColors.mutedInk,
                )
            }
            if (usage.remainingFreeParses == 0 && rewardedAdController != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Need more AI reminders? Watch a short ad to earn 5 credits. Credits appear only after secure server verification, which may take a moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NudgeeColors.mutedInk,
                )
                Spacer(Modifier.height(10.dp))
                NudgeeButton(
                    label = when (rewardedAdController.status) {
                        RewardedAdStatus.Loading -> "Loading ad…"
                        RewardedAdStatus.Ready -> "Watch ad for 5 credits"
                        RewardedAdStatus.Unavailable -> "Try loading an ad"
                    },
                    onClick = {
                        if (rewardedAdController.status == RewardedAdStatus.Ready) {
                            rewardedAdController.show(onDismissed = onAdDismissed)
                        } else {
                            rewardedAdController.load()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = rewardedAdController.status != RewardedAdStatus.Loading,
                    style = NudgeeButtonStyle.Secondary,
                )
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
    val viewModel: NotificationSettingsViewModel = viewModel { NotificationSettingsViewModel() }
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val refreshVersion by viewModel.refreshVersion.collectAsState()

    LaunchedEffect(permissionController, refreshVersion) {
        viewModel.updatePermissionStatus(permissionController.status())
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
                    onClick = viewModel::refresh,
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
