package com.soopeach.nudgee.client.domain.reminder

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

data class ParsedReminderDraft(
    val title: String,
    val notifyAt: String?,
    val needsClarification: Boolean,
    val clarification: String?,
    val remainingFreeParses: Int? = null,
    val bonusCredits: Int? = null,
)

data class ReminderParseUsage(
    val usedFreeParses: Int,
    val remainingFreeParses: Int,
    val dailyFreeParseLimit: Int,
    val bonusCredits: Int = 0,
)

interface NaturalLanguageReminderParser {
    suspend fun parse(input: String): ParsedReminderDraft
    suspend fun usage(): ReminderParseUsage
}

class AuthenticationRequiredException : IllegalStateException("Sign in with Google before using natural-language reminders.")
class DailyParseLimitReachedException : IllegalStateException("You’ve used all 10 free reminder parses for today. Try again tomorrow.")
class ReminderParseRequestException(
    message: String,
    val requestId: String?,
) : IllegalStateException(message)

/**
 * Calls the same protected parse-reminder Edge Function used by the web app.
 * Gemini credentials never leave Supabase.
 */
class SupabaseEdgeFunctionReminderParser(
    private val supabase: SupabaseClient,
) : NaturalLanguageReminderParser {
    override suspend fun parse(input: String): ParsedReminderDraft {
        if (supabase.auth.currentSessionOrNull() == null) throw AuthenticationRequiredException()
        syncProfileTimezone()

        val response = supabase.functions.invoke(
            function = "parse-reminder",
            body = ParseReminderRequest(
                text = input.trim(),
                timezone = TimeZone.currentSystemDefault().id,
                locale = "en",
                now = Clock.System.now().toString(),
            ),
        )
        response.throwIfReminderRequestFailed()
        val parsed = response.body<ParseReminderResponse>()
        val title = parsed.title.trim()
        require(title.isNotBlank()) { "Nudgee could not find a task in that reminder." }

        return ParsedReminderDraft(
            title = title,
            notifyAt = parsed.notifyAt,
            needsClarification = parsed.needsClarification || parsed.notifyAt == null,
            clarification = parsed.clarification,
            remainingFreeParses = parsed.remainingFreeParses,
            bonusCredits = parsed.bonusCredits,
        )
    }

    override suspend fun usage(): ReminderParseUsage {
        if (supabase.auth.currentSessionOrNull() == null) throw AuthenticationRequiredException()
        syncProfileTimezone()

        val response = supabase.functions.invoke(
            function = "parse-reminder",
            body = ParseReminderUsageRequest(
                action = "usage",
                timezone = TimeZone.currentSystemDefault().id,
            ),
        )
        response.throwIfReminderRequestFailed()
        val usage = response.body<ReminderParseUsageResponse>()
        return ReminderParseUsage(
            usedFreeParses = usage.usedFreeParses,
            remainingFreeParses = usage.remainingFreeParses,
            dailyFreeParseLimit = usage.dailyFreeParseLimit,
            bonusCredits = usage.bonusCredits,
        )
    }

    private suspend fun syncProfileTimezone() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: throw AuthenticationRequiredException()
        supabase.from("profiles").upsert(ProfileTimezone(userId, TimeZone.currentSystemDefault().id))
    }
}

private suspend fun HttpResponse.throwIfReminderRequestFailed() {
    if (status.value in 200..299) return

    val failure = runCatching { body<ReminderParseErrorResponse>() }.getOrNull()
    if (status.value == 429 || failure?.code == "daily_parse_limit_reached") {
        throw DailyParseLimitReachedException()
    }
    throw ReminderParseRequestException(
        message = failure?.error ?: "Nudgee could not complete that reminder request. Please try again.",
        requestId = failure?.requestId,
    )
}

@Serializable
private data class ParseReminderRequest(
    val text: String,
    val timezone: String,
    val locale: String,
    val now: String,
)

@Serializable
private data class ParseReminderResponse(
    val title: String,
    val notifyAt: String? = null,
    val needsClarification: Boolean = false,
    val clarification: String? = null,
    val remainingFreeParses: Int? = null,
    val bonusCredits: Int? = null,
)

@Serializable
private data class ParseReminderUsageRequest(
    val action: String,
    val timezone: String,
)

@Serializable
private data class ReminderParseUsageResponse(
    val usedFreeParses: Int,
    val remainingFreeParses: Int,
    val dailyFreeParseLimit: Int,
    val bonusCredits: Int = 0,
)

@Serializable
private data class ReminderParseErrorResponse(
    val error: String? = null,
    val code: String? = null,
    val requestId: String? = null,
)

@Serializable
private data class ProfileTimezone(
    val user_id: String,
    val timezone: String,
)
