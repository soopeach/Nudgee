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
)

interface NaturalLanguageReminderParser {
    suspend fun parse(input: String): ParsedReminderDraft
}

class AuthenticationRequiredException : IllegalStateException("Sign in with Google before using natural-language reminders.")

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
        val parsed = response.body<ParseReminderResponse>()
        val title = parsed.title.trim()
        require(title.isNotBlank()) { "Nudgee could not find a task in that reminder." }

        return ParsedReminderDraft(
            title = title,
            notifyAt = parsed.notifyAt,
            needsClarification = parsed.needsClarification || parsed.notifyAt == null,
            clarification = parsed.clarification,
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
)
