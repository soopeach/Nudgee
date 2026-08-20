type ReminderAction = 'snooze' | 'complete'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
const supabasePublishableKey = import.meta.env.VITE_SUPABASE_ANON_KEY

export async function performReminderAction(action: ReminderAction, actionToken: string) {
  if (!supabaseUrl || !supabasePublishableKey) throw new Error('Supabase is not configured.')
  const response = await fetch(`${supabaseUrl}/functions/v1/reminder-action`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', apikey: supabasePublishableKey },
    body: JSON.stringify({ action, actionToken }),
  })
  const body = await response.json().catch(() => null) as { error?: string } | null
  if (!response.ok) throw new Error(body?.error ?? 'Could not update this reminder.')
}
