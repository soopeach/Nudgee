import { useEffect, useState } from 'react'
import { navigateTo, routes } from '../navigation/routes'
import { AdminDashboardRequestError, getAdminDashboardMetrics, type AdminDashboardMetrics } from './adminDashboardService'

type LoadState = 'loading' | 'ready' | 'denied' | 'error'

function formatNumber(value: number) {
  return new Intl.NumberFormat().format(value)
}

function formatUpdatedAt(value: string) {
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatErrorTime(value: string) {
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}

function formatRelativeTime(value: string | null) {
  if (!value) return 'No successful run yet'
  const minutes = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 60_000))
  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  return hours < 24 ? `${hours}h ago` : `${Math.floor(hours / 24)}d ago`
}

function MetricCard({ label, value, note }: { label: string; value: string; note: string }) {
  return <article className="admin-metric-card"><span>{label}</span><strong>{value}</strong><small>{note}</small></article>
}

export function AdminDashboardPage() {
  const [metrics, setMetrics] = useState<AdminDashboardMetrics | null>(null)
  const [state, setState] = useState<LoadState>('loading')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function loadMetrics(isManualRefresh = false) {
    if (isManualRefresh) setIsRefreshing(true)
    else setState('loading')
    setError(null)
    try {
      const nextMetrics = await getAdminDashboardMetrics()
      setMetrics(nextMetrics)
      setState('ready')
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : 'Nudgee could not load dashboard metrics.'
      setState(caught instanceof AdminDashboardRequestError && caught.status === 403 ? 'denied' : 'error')
      setError(message)
    } finally {
      setIsRefreshing(false)
    }
  }

  useEffect(() => { void loadMetrics() }, [])

  return (
    <main className="app-shell">
      <section className="admin-dashboard" aria-labelledby="admin-dashboard-title">
        <header className="admin-dashboard-header">
          <div><button className="home-link" type="button" onClick={() => navigateTo(routes.home)}>← Home</button><span className="eyebrow">Private workspace</span><h1 id="admin-dashboard-title">Nudgee pulse</h1><p>A small, privacy-preserving snapshot of reminders, delivery, and AI allowance activity.</p></div>
          {state === 'ready' && <button className="admin-refresh-button" type="button" disabled={isRefreshing} onClick={() => void loadMetrics(true)}>{isRefreshing ? 'Refreshing…' : 'Refresh'}</button>}
        </header>

        {state === 'loading' && <p className="admin-state-card" role="status">Loading your private dashboard…</p>}
        {state === 'denied' && <section className="admin-state-card admin-state-card-error" role="alert"><h2>Admin access only</h2><p>This account is not allowed to view Nudgee operating metrics.</p></section>}
        {state === 'error' && <section className="admin-state-card admin-state-card-error" role="alert"><h2>Dashboard unavailable</h2><p>{error ?? 'Please try again in a moment.'}</p><button className="admin-refresh-button" type="button" onClick={() => void loadMetrics()}>Try again</button></section>}

        {state === 'ready' && metrics && <>
          <p className="admin-updated">Updated {formatUpdatedAt(metrics.generatedAt)} · all counts are aggregate only</p>
          <section className="admin-metric-grid" aria-label="Nudgee operating metrics">
            <MetricCard label="Users" value={formatNumber(metrics.users.total)} note="Accounts with a Nudgee profile" />
            <MetricCard label="Active devices" value={formatNumber(metrics.devices.active)} note={Object.entries(metrics.devices.byPlatform).map(([platform, count]) => `${platform} ${count}`).join(' · ') || 'No registered devices'} />
            <MetricCard label="Reminders today" value={formatNumber(metrics.reminders.createdToday)} note={`${formatNumber(metrics.reminders.open)} open · ${formatNumber(metrics.reminders.completed)} completed overall`} />
            <MetricCard label="Delivery success" value={metrics.deliveries.successRate === null ? '—' : `${metrics.deliveries.successRate}%`} note={`${formatNumber(metrics.deliveries.sentLast24Hours)} sent · ${formatNumber(metrics.deliveries.failedLast24Hours)} failed in 24h`} />
            <MetricCard label="AI parses today" value={formatNumber(metrics.ai.freeParsesToday)} note="Free allowance consumed on the current UTC date" />
            <MetricCard label="Reward credits" value={`+${formatNumber(metrics.ai.rewardCreditsGrantedLast24Hours)}`} note={`${formatNumber(metrics.ai.creditRefundsLast24Hours)} refunded in 24h`} />
          </section>
          <section className="admin-operations-grid" aria-label="Scheduler and activity health">
            <article className="admin-operations-card"><span className="eyebrow">Scheduler health</span><h2>{metrics.scheduler.overdueReminders === 0 ? 'On schedule' : 'Needs attention'}</h2><p><strong>{formatNumber(metrics.scheduler.overdueReminders)}</strong> overdue pending reminder{metrics.scheduler.overdueReminders === 1 ? '' : 's'}</p><small>Last successful run · {formatRelativeTime(metrics.scheduler.lastSuccessfulAt)}</small>{metrics.scheduler.lastRun && <small>Latest run · {metrics.scheduler.lastRun.status} · {metrics.scheduler.lastRun.claimedTasks} claimed</small>}</article>
            <article className="admin-operations-card"><span className="eyebrow">Engaged users</span><h2>Reminder activity</h2><div className="admin-activity-row"><span><strong>{formatNumber(metrics.activity.dailyActiveUsers)}</strong>DAU</span><span><strong>{formatNumber(metrics.activity.weeklyActiveUsers)}</strong>WAU</span><span><strong>{formatNumber(metrics.activity.monthlyActiveUsers)}</strong>MAU</span></div><small>Users who created or updated a reminder in each period</small></article>
          </section>
          <section className="admin-platform-section" aria-labelledby="admin-platform-title"><div><span className="eyebrow">Delivery by platform</span><h2 id="admin-platform-title">Last 30 days</h2></div>{metrics.platformDeliveries.length === 0 ? <p className="admin-errors-empty">No delivery attempts recorded yet.</p> : <div className="admin-platform-list">{metrics.platformDeliveries.map((platform) => <article key={platform.platform}><div><strong>{platform.platform}</strong><span>{platform.total} attempts</span></div><b>{platform.successRate === null ? '—' : `${platform.successRate}%`}</b><small>{platform.sent} sent · {platform.failed} failed</small></article>)}</div>}</section>
          <section className="admin-errors-section" aria-labelledby="admin-errors-title">
            <div><span className="eyebrow">Operations</span><h2 id="admin-errors-title">Recent errors</h2></div>
            {metrics.recentErrors.length === 0
              ? <p className="admin-errors-empty">No recorded errors in the last 30 days.</p>
              : <ul className="admin-error-list">{metrics.recentErrors.map((event) => <li key={event.id}><div><strong>{event.source.replace(/_/g, ' ')}</strong><span>{event.eventType.replace(/_/g, ' ')} · {formatErrorTime(event.createdAt)}</span></div><p>{event.message}</p></li>)}</ul>}
          </section>
          <section className="admin-note-card"><span aria-hidden="true">✦</span><div><strong>Gemini billing is not shown here.</strong><p>This page tracks Nudgee-side requests and credits. Check Google AI Studio or Google Cloud for provider token and billing totals.</p></div></section>
        </>}
      </section>
    </main>
  )
}
