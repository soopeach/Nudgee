import type { SupabaseClient } from 'npm:@supabase/supabase-js@2'

type ErrorSource = 'parse_reminder' | 'notification_dispatch' | 'rewarded_ad'

function safeMessage(error: unknown) {
  return (error instanceof Error ? error.message : String(error)).replace(/[\r\n\t]+/g, ' ').slice(0, 500)
}

/**
 * Records a deliberately small operational event. Never pass reminder text,
 * device tokens, email addresses, raw provider responses, or authentication data.
 */
export async function recordOperationalError(
  client: SupabaseClient<any>,
  source: ErrorSource,
  eventType: string,
  error: unknown,
  metadata: Record<string, string | number | boolean | null> = {},
) {
  try {
    await client.from('operational_error_events').insert({
      source,
      event_type: eventType.slice(0, 100),
      message: safeMessage(error),
      metadata,
    })
    // Error events are useful briefly for operations, not permanent product data.
    await client.from('operational_error_events').delete().lt('created_at', new Date(Date.now() - 30 * 24 * 60 * 60 * 1_000).toISOString())
  } catch (recordError) {
    console.error('Operational error event could not be recorded', safeMessage(recordError))
  }
}
