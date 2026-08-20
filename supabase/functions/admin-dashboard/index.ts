import { createClient } from 'npm:@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Origin': '*',
  'Content-Type': 'application/json',
}

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders })
}

function serverAuthKey() {
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (serviceRoleKey) return serviceRoleKey

  const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
  if (!secretKeys) return null
  try { return (JSON.parse(secretKeys) as Record<string, string>).default ?? null } catch { return null }
}

function adminEmails() {
  return new Set(
    (Deno.env.get('NUDGEE_ADMIN_EMAILS') ?? '')
      .split(',')
      .map((email) => email.trim().toLowerCase())
      .filter(Boolean),
  )
}

function todayUtc() {
  return new Date().toISOString().slice(0, 10)
}

function countOrThrow(result: { count: number | null; error: { message: string } | null }, metric: string) {
  if (result.error) throw new Error(`Could not load ${metric}.`)
  return result.count ?? 0
}

Deno.serve(async (request) => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  // supabase-js `functions.invoke()` uses POST by default. This endpoint does
  // not read or mutate request data; POST is accepted only for SDK compatibility.
  if (request.method !== 'GET' && request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405)

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serviceRoleKey = serverAuthKey()
  if (!supabaseUrl || !serviceRoleKey) return json({ error: 'Dashboard is not configured.' }, 500)

  const authorization = request.headers.get('Authorization')
  const accessToken = authorization?.replace(/^Bearer\s+/i, '').trim()
  if (!accessToken) return json({ error: 'Unauthorized.' }, 401)

  const client = createClient(supabaseUrl, serviceRoleKey)
  const { data: { user }, error: userError } = await client.auth.getUser(accessToken)
  if (userError || !user?.email) return json({ error: 'Unauthorized.' }, 401)
  if (!adminEmails().has(user.email.toLowerCase())) return json({ error: 'Admin access is required.' }, 403)

  const now = new Date()
  const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1_000).toISOString()
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1_000).toISOString()
  const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1_000).toISOString()
  const dayStart = `${todayUtc()}T00:00:00.000Z`

  try {
    const [
      userCount,
      activeDeviceCount,
      activeDevices,
      reminderCount,
      remindersCreatedToday,
      openReminderCount,
      completedReminderCount,
      deliveriesLast24Hours,
      sentDeliveriesLast24Hours,
      failedDeliveriesLast24Hours,
      aiUsageRows,
      creditEvents,
      recentErrors,
      overdueReminderCount,
      latestSchedulerRun,
      latestSuccessfulSchedulerRun,
      platformDeliveries,
      dailyActiveUsers,
      weeklyActiveUsers,
      monthlyActiveUsers,
    ] = await Promise.all([
      client.from('profiles').select('user_id', { count: 'exact', head: true }),
      client.from('device_tokens').select('id', { count: 'exact', head: true }).eq('is_active', true),
      client.from('device_tokens').select('platform').eq('is_active', true),
      client.from('tasks').select('id', { count: 'exact', head: true }),
      client.from('tasks').select('id', { count: 'exact', head: true }).gte('created_at', dayStart),
      client.from('tasks').select('id', { count: 'exact', head: true }).eq('completed', false),
      client.from('tasks').select('id', { count: 'exact', head: true }).eq('completed', true),
      client.from('notification_deliveries').select('id', { count: 'exact', head: true }).gte('created_at', dayAgo),
      client.from('notification_deliveries').select('id', { count: 'exact', head: true }).gte('created_at', dayAgo).eq('status', 'sent'),
      client.from('notification_deliveries').select('id', { count: 'exact', head: true }).gte('created_at', dayAgo).eq('status', 'failed'),
      client.from('ai_parse_daily_usage').select('free_parse_count').eq('usage_date', todayUtc()),
      client.from('ai_parse_credit_events').select('reason, delta').gte('created_at', dayAgo),
      client.from('operational_error_events').select('id, source, event_type, message, created_at').order('created_at', { ascending: false }).limit(20),
      client.from('tasks').select('id', { count: 'exact', head: true }).eq('completed', false).lte('notify_at', now.toISOString()).in('notification_state', ['pending', 'processing', 'failed']),
      client.from('notification_scheduler_runs').select('status, started_at, finished_at, claimed_tasks, failed_tasks').order('started_at', { ascending: false }).limit(1).maybeSingle(),
      client.from('notification_scheduler_runs').select('finished_at').eq('status', 'succeeded').order('finished_at', { ascending: false }).limit(1).maybeSingle(),
      client.from('notification_deliveries').select('channel, status').gte('created_at', monthAgo),
      client.from('tasks').select('user_id').gte('updated_at', dayAgo),
      client.from('tasks').select('user_id').gte('updated_at', weekAgo),
      client.from('tasks').select('user_id').gte('updated_at', monthAgo),
    ])

    if (activeDevices.error || aiUsageRows.error || creditEvents.error || recentErrors.error || latestSchedulerRun.error || latestSuccessfulSchedulerRun.error || platformDeliveries.error || dailyActiveUsers.error || weeklyActiveUsers.error || monthlyActiveUsers.error) throw new Error('Could not load dashboard details.')
    const byPlatform = (activeDevices.data ?? []).reduce<Record<string, number>>((counts, device) => {
      const platform = typeof device.platform === 'string' ? device.platform : 'unknown'
      counts[platform] = (counts[platform] ?? 0) + 1
      return counts
    }, {})
    const freeParsesToday = (aiUsageRows.data ?? []).reduce((total, row) => total + (Number(row.free_parse_count) || 0), 0)
    const rewardCreditsGrantedLast24Hours = (creditEvents.data ?? [])
      .filter((event) => event.reason === 'rewarded_ad')
      .reduce((total, event) => total + Math.max(Number(event.delta) || 0, 0), 0)
    const creditRefundsLast24Hours = (creditEvents.data ?? [])
      .filter((event) => event.reason === 'parse_refund')
      .reduce((total, event) => total + Math.max(Number(event.delta) || 0, 0), 0)
    const deliveryTotal = countOrThrow(deliveriesLast24Hours, 'delivery totals')
    const sentTotal = countOrThrow(sentDeliveriesLast24Hours, 'sent delivery totals')
    const platformSummary = (platformDeliveries.data ?? []).reduce<Record<string, { sent: number; failed: number; total: number }>>((summary, delivery) => {
      const platform = typeof delivery.channel === 'string' ? delivery.channel : 'unknown'
      const entry = summary[platform] ?? { sent: 0, failed: 0, total: 0 }
      entry.total += 1
      if (delivery.status === 'sent') entry.sent += 1
      if (delivery.status === 'failed') entry.failed += 1
      summary[platform] = entry
      return summary
    }, {})
    const activeUserCount = (result: { data: Array<{ user_id: string }> | null }) => new Set((result.data ?? []).map((row) => row.user_id)).size

    return json({
      generatedAt: now.toISOString(),
      users: { total: countOrThrow(userCount, 'user totals') },
      devices: { active: countOrThrow(activeDeviceCount, 'device totals'), byPlatform },
      reminders: {
        total: countOrThrow(reminderCount, 'reminder totals'),
        createdToday: countOrThrow(remindersCreatedToday, 'today reminder totals'),
        open: countOrThrow(openReminderCount, 'open reminder totals'),
        completed: countOrThrow(completedReminderCount, 'completed reminder totals'),
      },
      deliveries: {
        last24Hours: deliveryTotal,
        sentLast24Hours: sentTotal,
        failedLast24Hours: countOrThrow(failedDeliveriesLast24Hours, 'failed delivery totals'),
        successRate: deliveryTotal === 0 ? null : Math.round((sentTotal / deliveryTotal) * 100),
      },
      ai: { freeParsesToday, rewardCreditsGrantedLast24Hours, creditRefundsLast24Hours },
      scheduler: {
        overdueReminders: countOrThrow(overdueReminderCount, 'overdue reminder totals'),
        lastRun: latestSchedulerRun.data ? {
          status: latestSchedulerRun.data.status,
          startedAt: latestSchedulerRun.data.started_at,
          finishedAt: latestSchedulerRun.data.finished_at,
          claimedTasks: latestSchedulerRun.data.claimed_tasks,
          failedTasks: latestSchedulerRun.data.failed_tasks,
        } : null,
        lastSuccessfulAt: latestSuccessfulSchedulerRun.data?.finished_at ?? null,
      },
      platformDeliveries: Object.entries(platformSummary).map(([platform, summary]) => ({
        platform,
        sent: summary.sent,
        failed: summary.failed,
        total: summary.total,
        successRate: summary.total === 0 ? null : Math.round((summary.sent / summary.total) * 100),
      })).sort((left, right) => right.total - left.total),
      activity: {
        dailyActiveUsers: activeUserCount(dailyActiveUsers),
        weeklyActiveUsers: activeUserCount(weeklyActiveUsers),
        monthlyActiveUsers: activeUserCount(monthlyActiveUsers),
      },
      recentErrors: (recentErrors.data ?? []).map((event) => ({
        id: event.id,
        source: event.source,
        eventType: event.event_type,
        message: event.message,
        createdAt: event.created_at,
      })),
    })
  } catch (error) {
    console.error('Admin dashboard metrics failed', { error: error instanceof Error ? error.message : String(error) })
    return json({ error: 'Nudgee could not load dashboard metrics.' }, 500)
  }
})
