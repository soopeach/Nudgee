import { supabase } from '../../lib/supabase'

export type AdminDashboardMetrics = {
  generatedAt: string
  users: { total: number }
  devices: { active: number; byPlatform: Record<string, number> }
  reminders: { total: number; createdToday: number; open: number; completed: number }
  deliveries: { last24Hours: number; sentLast24Hours: number; failedLast24Hours: number; successRate: number | null }
  ai: { freeParsesToday: number; rewardCreditsGrantedLast24Hours: number; creditRefundsLast24Hours: number }
  scheduler: {
    overdueReminders: number
    lastRun: { status: string; startedAt: string; finishedAt: string | null; claimedTasks: number; failedTasks: number } | null
    lastSuccessfulAt: string | null
  }
  platformDeliveries: Array<{ platform: string; sent: number; failed: number; total: number; successRate: number | null }>
  activity: { dailyActiveUsers: number; weeklyActiveUsers: number; monthlyActiveUsers: number }
  recentErrors: Array<{ id: string; source: string; eventType: string; message: string; createdAt: string }>
}

export class AdminDashboardRequestError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message)
    this.name = 'AdminDashboardRequestError'
  }
}

function requireSupabase() {
  if (!supabase) throw new Error('Supabase is not configured.')
  return supabase
}

export async function getAdminDashboardMetrics(): Promise<AdminDashboardMetrics> {
  const { data, error } = await requireSupabase().functions.invoke('admin-dashboard')
  if (error) {
    const response = error.context instanceof Response ? error.context : null
    const payload = response ? await response.json().catch(() => null) as { error?: unknown } | null : null
    throw new AdminDashboardRequestError(
      typeof payload?.error === 'string' ? payload.error : 'Nudgee could not load dashboard metrics.',
      response?.status,
    )
  }
  if (!data || typeof data !== 'object') throw new Error('Nudgee could not load dashboard metrics.')
  return data as AdminDashboardMetrics
}
