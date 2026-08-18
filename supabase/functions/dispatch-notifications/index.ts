import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2'
import { importPKCS8, SignJWT } from 'npm:jose@5'

type Task = { id: string; user_id: string; title: string; notify_at: string }
type DeviceToken = { id: string; platform: 'web' | 'ios' | 'android' | 'desktop'; token: string }
type CurrentTaskState = { completed: boolean; notification_state: string }

class UnregisteredFcmTokenError extends Error {}

const supabaseUrl = Deno.env.get('SUPABASE_URL')
const schedulerKey = Deno.env.get('NUDGE_SCHEDULER_KEY')
const fcmProjectId = Deno.env.get('FCM_PROJECT_ID')
const fcmClientEmail = Deno.env.get('FCM_CLIENT_EMAIL')
const fcmPrivateKey = Deno.env.get('FCM_PRIVATE_KEY')?.replace(/\\n/g, '\n')

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

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
}

async function getFcmAccessToken() {
  if (!fcmProjectId || !fcmClientEmail || !fcmPrivateKey) throw new Error('FCM service account secrets are not configured.')
  const privateKey = await importPKCS8(fcmPrivateKey, 'RS256')
  const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/firebase.messaging' })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(fcmClientEmail)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(privateKey)
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  })
  if (!response.ok) throw new Error(`Google OAuth token request failed (${response.status}).`)
  const body = await response.json() as { access_token?: string }
  if (!body.access_token) throw new Error('Google OAuth response did not include an access token.')
  return body.access_token
}

async function sendFcm(token: string, task: Task, platform: DeviceToken['platform'], accessToken: string) {
  if (!fcmProjectId) throw new Error('FCM_PROJECT_ID is not configured.')
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${fcmProjectId}/messages:send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: 'Nudgee reminder', body: task.title },
        data: { taskId: task.id, title: task.title },
        ...(platform === 'android' ? { android: { priority: 'high', notification: { channel_id: 'nudgee_reminders' } } } : {}),
      },
    }),
  })
  if (!response.ok) {
    const responseText = await response.text()
    if (response.status === 404 && (responseText.includes('UNREGISTERED') || responseText.includes('NotRegistered'))) {
      throw new UnregisteredFcmTokenError(`FCM token is no longer registered: ${responseText.slice(0, 500)}`)
    }
    throw new Error(`FCM rejected the message (${response.status}): ${responseText.slice(0, 500)}`)
  }
}

async function dispatchTask(client: SupabaseClient<any>, task: Task, accessToken: string | null) {
  // A completion can happen after claim_due_tasks() returns but before FCM is
  // contacted. Respect the latest server-owned lifecycle state in that gap.
  const { data: currentTask, error: currentTaskError } = await client
    .from('tasks')
    .select('completed, notification_state')
    .eq('id', task.id)
    .maybeSingle()
  if (currentTaskError) throw currentTaskError
  const latestTask = currentTask as CurrentTaskState | null
  if (!latestTask || latestTask.completed || latestTask.notification_state !== 'processing') {
    return { taskId: task.id, failed: false }
  }

  const { data, error } = await client.from('device_tokens').select('id, platform, token').eq('user_id', task.user_id).eq('is_active', true)
  if (error) throw error
  const devices = (data ?? []) as DeviceToken[]
  // A task can still be delivered to an actively running desktop through its
  // authenticated Realtime session. There is no persistent desktop push token
  // until APNs/WNS support is added, so an empty device list is not a failure.
  // The delivery-count check below will then move the claimed task to `sent`.
  let failed = false
  const activeDeviceIds = new Set(devices.map(device => device.id))
  for (const device of devices) {
    const { data: existingDelivery, error: existingError } = await client.from('notification_deliveries').select('id, status, attempt_count').eq('task_id', task.id).eq('device_token_id', device.id).eq('channel', device.platform).maybeSingle()
    if (existingError) { failed = true; continue }
    // A retry of a partially failed task must not resend to devices that
    // already acknowledged delivery successfully.
    if (existingDelivery?.status === 'sent') continue
    const { data: delivery, error: deliveryError } = await client.from('notification_deliveries').upsert({ task_id: task.id, user_id: task.user_id, device_token_id: device.id, channel: device.platform, status: 'processing', attempt_count: 1, error_message: null }, { onConflict: 'task_id,device_token_id,channel' }).select('id').single()
    if (deliveryError) { failed = true; continue }
    try {
      if (device.platform !== 'web' && device.platform !== 'android') throw new Error(`${device.platform} delivery is not implemented yet.`)
      if (!accessToken) throw new Error('FCM access token is unavailable.')
      await sendFcm(device.token, task, device.platform, accessToken)
      await client.from('notification_deliveries').update({ status: 'sent', sent_at: new Date().toISOString(), updated_at: new Date().toISOString() }).eq('id', delivery.id)
    } catch (caught) {
      const unregisteredToken = caught instanceof UnregisteredFcmTokenError
      if (unregisteredToken) {
        const { error: deactivateError } = await client
          .from('device_tokens')
          .update({ is_active: false, updated_at: new Date().toISOString() })
          .eq('id', device.id)
        if (deactivateError) failed = true
        else activeDeviceIds.delete(device.id)
      } else {
        failed = true
      }
      await client.from('notification_deliveries').update({ status: 'failed', failed_at: new Date().toISOString(), error_message: caught instanceof Error ? caught.message : 'Unknown delivery error', updated_at: new Date().toISOString() }).eq('id', delivery.id)
    }
  }
  const remainingActiveDeviceIds = [...activeDeviceIds]
  const { count: remainingFailures } = remainingActiveDeviceIds.length === 0
    ? { count: 0 }
    : await client
      .from('notification_deliveries')
      .select('id', { count: 'exact', head: true })
      .eq('task_id', task.id)
      .in('device_token_id', remainingActiveDeviceIds)
      .in('status', ['pending', 'processing', 'failed'])
  await client.from('tasks').update({ notification_state: failed || (remainingFailures ?? 0) > 0 ? 'failed' : 'sent', updated_at: new Date().toISOString() }).eq('id', task.id).eq('notification_state', 'processing')
  return { taskId: task.id, failed }
}

async function markTaskFailed(client: SupabaseClient<any>, taskId: string, error: unknown) {
  await client.from('tasks').update({ notification_state: 'failed', updated_at: new Date().toISOString() }).eq('id', taskId).eq('notification_state', 'processing')
  console.error('Task dispatch failed', { taskId, error: error instanceof Error ? error.message : error })
}

Deno.serve(async (request) => {
  if (request.method !== 'POST') return json({ error: 'Method not allowed' }, 405)
  if (!schedulerKey || request.headers.get('x-nudgee-cron-secret') !== schedulerKey) return json({ error: 'Unauthorized' }, 401)
  if (!supabaseUrl) return json({ error: 'SUPABASE_URL is not configured' }, 500)
  try {
    const client = createClient(supabaseUrl, getAdminKey())
    const { data, error } = await client.rpc('claim_due_tasks', { p_limit: 50 })
    if (error) throw error
    const tasks = (data ?? []) as Task[]
    let accessToken: string | null = null
    try {
      accessToken = tasks.length > 0 ? await getFcmAccessToken() : null
    } catch (caught) {
      for (const task of tasks) await markTaskFailed(client, task.id, caught)
      throw caught
    }
    const results = []
    for (const task of tasks) {
      try {
        results.push(await dispatchTask(client, task, accessToken))
      } catch (caught) {
        await markTaskFailed(client, task.id, caught)
        results.push({ taskId: task.id, failed: true })
      }
    }
    return json({ ok: true, claimed: tasks.length, results })
  } catch (caught) {
    console.error(caught)
    return json({ ok: false, error: caught instanceof Error ? caught.message : 'Dispatch failed.' }, 500)
  }
})
