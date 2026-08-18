import { createClient } from 'npm:@supabase/supabase-js@2'

type DeleteAccountRequest = { confirmation?: unknown }

const corsHeaders = {
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Origin': '*',
  'Content-Type': 'application/json',
}

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders })
}

function getServerAuthKey() {
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (serviceRoleKey) return serviceRoleKey
  const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
  if (!secretKeys) return null
  try { return (JSON.parse(secretKeys) as Record<string, string>).default ?? null } catch { return null }
}

/** Resolves the caller from its bearer token, then invokes atomic DB cleanup. */
Deno.serve(async (request) => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405)

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serverAuthKey = getServerAuthKey()
  if (!supabaseUrl || !serverAuthKey) return json({ error: 'Account deletion is not configured.' }, 500)

  const accessToken = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '').trim()
  if (!accessToken) return json({ error: 'Unauthorized.' }, 401)

  const supabase = createClient(supabaseUrl, serverAuthKey)
  const { data: { user }, error: userError } = await supabase.auth.getUser(accessToken)
  if (userError || !user) return json({ error: 'Unauthorized.' }, 401)

  let body: DeleteAccountRequest
  try { body = await request.json() as DeleteAccountRequest } catch { return json({ error: 'A confirmation is required.' }, 400) }
  if (body.confirmation !== 'DELETE') return json({ error: 'Type DELETE to confirm account deletion.' }, 400)

  try {
    const { error } = await supabase.rpc('delete_nudgee_account', { p_user_id: user.id })
    if (error) throw error
  } catch (error) {
    console.error('Account deletion failed', { userId: user.id, error: error instanceof Error ? error.message : String(error) })
    return json({ error: 'Nudgee could not delete your account. Please try again.' }, 500)
  }

  console.info('Account deleted', { userId: user.id })
  return json({ deleted: true })
})
