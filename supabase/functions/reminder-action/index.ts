import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2'

type ReminderAction = 'complete' | 'snooze'
type ActionPayload = { taskId: string; userId: string; occurrence: number; exp: number }

const supabaseUrl = Deno.env.get('SUPABASE_URL')
const actionSecret = Deno.env.get('NUDGEE_REMINDER_ACTION_SECRET')
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'apikey, authorization, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  })
}

function getAdminKey() {
  const legacyKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (legacyKey) return legacyKey
  const rawKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
  if (rawKeys) {
    const keys = JSON.parse(rawKeys) as Record<string, string>
    if (keys.default) return keys.default
  }
  throw new Error('Supabase secret key is not configured.')
}

function decodeBase64Url(value: string) {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('Malformed action token.')
  const padded = value.replaceAll('-', '+').replaceAll('_', '/') + '='.repeat((4 - value.length % 4) % 4)
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
}

function constantTimeEquals(left: Uint8Array, right: Uint8Array) {
  if (left.length !== right.length) return false
  let difference = 0
  for (let index = 0; index < left.length; index += 1) difference |= left[index] ^ right[index]
  return difference === 0
}

async function verifyActionToken(token: string): Promise<ActionPayload> {
  if (!actionSecret) throw new Error('NUDGEE_REMINDER_ACTION_SECRET is not configured.')
  const [payloadPart, signaturePart, ...rest] = token.split('.')
  if (!payloadPart || !signaturePart || rest.length > 0) throw new Error('Malformed action token.')
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(actionSecret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const expected = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(payloadPart)))
  if (!constantTimeEquals(expected, decodeBase64Url(signaturePart))) throw new Error('Invalid action token.')
  const payload = JSON.parse(new TextDecoder().decode(decodeBase64Url(payloadPart))) as ActionPayload
  if (
    typeof payload.taskId !== 'string' ||
    typeof payload.userId !== 'string' ||
    !Number.isInteger(payload.occurrence) || payload.occurrence < 0 ||
    !Number.isInteger(payload.exp) || payload.exp <= Math.floor(Date.now() / 1_000)
  ) throw new Error('Expired or invalid action token.')
  return payload
}

async function applyAction(client: SupabaseClient<any>, action: ReminderAction, payload: ActionPayload) {
  const { data: task, error: taskError } = await client
    .from('tasks')
    .select('id, completed, notification_occurrence')
    .eq('id', payload.taskId)
    .eq('user_id', payload.userId)
    .maybeSingle()
  if (taskError) throw taskError
  if (!task || task.completed) return { status: 'already_handled' }
  if (task.notification_occurrence !== payload.occurrence) return { status: 'stale_action' }

  if (action === 'complete') {
    const { error } = await client.from('tasks').update({
      completed: true,
      completed_at: new Date().toISOString(),
      notification_state: 'cancelled',
      updated_at: new Date().toISOString(),
    }).eq('id', payload.taskId).eq('user_id', payload.userId).eq('notification_occurrence', payload.occurrence)
    if (error) throw error
    return { status: 'completed' }
  }

  const notifyAt = new Date(Date.now() + 10 * 60 * 1_000).toISOString()
  const { error } = await client.from('tasks').update({
    notify_at: notifyAt,
    notification_state: 'pending',
    notification_occurrence: payload.occurrence + 1,
    updated_at: new Date().toISOString(),
  }).eq('id', payload.taskId).eq('user_id', payload.userId).eq('notification_occurrence', payload.occurrence)
  if (error) throw error
  return { status: 'snoozed', notifyAt }
}

Deno.serve(async (request) => {
  if (request.method === 'OPTIONS') return new Response(null, { headers: corsHeaders })
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405)
  try {
    if (!supabaseUrl) throw new Error('SUPABASE_URL is not configured.')
    const body = await request.json() as { action?: ReminderAction; actionToken?: string }
    if ((body.action !== 'complete' && body.action !== 'snooze') || !body.actionToken) {
      return json({ error: 'Invalid reminder action.' }, 400)
    }
    const payload = await verifyActionToken(body.actionToken)
    const result = await applyAction(createClient(supabaseUrl, getAdminKey()), body.action, payload)
    return json({ ok: true, ...result })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Reminder action failed.'
    const clientError = /token|action/i.test(message)
    console.error('Reminder action failed', { error: message })
    return json({ error: clientError ? message : 'Reminder action failed.' }, clientError ? 401 : 500)
  }
})
