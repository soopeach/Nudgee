package com.soopeach.nudgee.client.domain.reminder

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

data class ParsedReminderDraft(
    val title: String,
    val notifyAt: String?,
    val needsClarification: Boolean,
    val clarification: String?,
    val remainingFreeParses: Int? = null,
)

data class ReminderParseUsage(
    val usedFreeParses: Int,
    val remainingFreeParses: Int,
    val dailyFreeParseLimit: Int,
)

interface NaturalLanguageReminderParser {
    suspend fun parse(input: String): ParsedReminderDraft
    suspend fun usage(): ReminderParseUsage
}

class AuthenticationRequiredException : IllegalStateException("Sign in with Google before using natural-language reminders.")
class DailyParseLimitReachedException : IllegalStateException("You’ve used all 10 free reminder parses for today. Try again tomorrow.")

/**
 * Calls the same protected parse-reminder Edge Function used by the web app.
 * Gemini credentials never leave Supabase.
 */
class SupabaseEdgeFunctionReminderParser(
    private val supabase: SupabaseClient,
) : NaturalLanguageReminderParser {
    override suspend fun parse(input: String): ParsedReminderDraft {
        if (supabase.auth.currentSessionOrNull() == null) throw AuthenticationRequiredException()

        val response = supabase.functions.invoke(
            function = "parse-reminder",
            body = ParseReminderRequest(
                text = input.trim(),
                timezone = TimeZone.currentSystemDefault().id,
                locale = "en",
                now = Clock.System.now().toString(),
            ),
        )
        if (response.status.value == 429) throw DailyParseLimitReachedException()
        val parsed = response.body<ParseReminderResponse>()
        val title = parsed.title.trim()
        require(title.isNotBlank()) { "Nudgee could not find a task in that reminder." }

        return ParsedReminderDraft(
            title = title,
            notifyAt = parsed.notifyAt,
            needsClarification = parsed.needsClarification || parsed.notifyAt == null,
            clarification = parsed.clarification,
            remainingFreeParses = parsed.remainingFreeParses,
        )
    }

    override suspend fun usage(): ReminderParseUsage {
        if (supabase.auth.currentSessionOrNull() == null) throw AuthenticationRequiredException()

        val response = supabase.functions.invoke(
            function = "parse-reminder",
            body = ParseReminderUsageRequest(
                action = "usage",
                timezone = TimeZone.currentSystemDefault().id,
            ),
        )
        val usage = response.body<ReminderParseUsageResponse>()
        return ReminderParseUsage(
            usedFreeParses = usage.usedFreeParses,
            remainingFreeParses = usage.remainingFreeParses,
            dailyFreeParseLimit = usage.dailyFreeParseLimit,
        )
    }
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
)
