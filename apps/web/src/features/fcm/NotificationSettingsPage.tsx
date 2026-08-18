import { navigateTo, routes } from '../navigation/routes'
import { NotificationDeliverySettings } from './NotificationDeliverySettings'

type NotificationSettingsPageProps = { userId: string }

export function NotificationSettingsPage({ userId }: NotificationSettingsPageProps) {
  return (
    <main className="app-shell">
      <section className="notification-settings-card" aria-labelledby="settings-title">
        <div className="brand-mark" aria-hidden="true">n</div>
        <button className="home-link" type="button" onClick={() => navigateTo(routes.home)}>Go to Home <span>→</span></button>
        <span className="eyebrow">Nudgee</span>
        <h1 id="settings-title">Notification settings</h1>
        <p className="settings-intro">Let Nudgee gently remind you when your tasks are due.</p>

        <NotificationDeliverySettings userId={userId} />
      </section>
    </main>
  )
}
