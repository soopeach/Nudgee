import { supabase } from '../../lib/supabase'

/** Stores the user's timezone preference once per authenticated browser session. */
export async function syncProfileTimezone(userId: string) {
  if (!supabase) return
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
  if (!timezone) return

  const { error } = await supabase
    .from('profiles')
    .upsert({ user_id: userId, timezone }, { onConflict: 'user_id' })
  if (error) throw error
}
