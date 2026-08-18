package com.soopeach.nudgee.client.domain.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

class AccountDeletionException(message: String) : IllegalStateException(message)

/** Calls the protected server endpoint; the client never chooses which account to delete. */
class SupabaseAccountDeletionService(
    private val supabase: SupabaseClient,
) {
    suspend fun deleteCurrentAccount() {
        if (supabase.auth.currentSessionOrNull() == null) throw AccountDeletionException("Sign in before deleting your account.")
        val response = supabase.functions.invoke(
            function = "delete-account",
            body = DeleteAccountRequest(confirmation = "DELETE"),
        )
        response.throwIfAccountDeletionFailed()
    }
}

private suspend fun HttpResponse.throwIfAccountDeletionFailed() {
    if (status.value in 200..299) return
    val message = runCatching { body<AccountDeletionErrorResponse>().error }.getOrNull()
        ?: "Nudgee could not delete your account. Please try again."
    throw AccountDeletionException(message)
}

@Serializable
private data class DeleteAccountRequest(val confirmation: String)

@Serializable
private data class AccountDeletionErrorResponse(val error: String? = null)
