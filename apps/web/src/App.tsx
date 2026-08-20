import { NotificationSettingsPage } from './features/fcm/NotificationSettingsPage'
import { routes } from './features/navigation/routes'
import { useRoute } from './features/navigation/useRoute'
import { TodoPage } from './features/todos/TodoPage'
import { SettingsPage } from './features/settings/SettingsPage'
import { CalendarPage } from './features/calendar/CalendarPage'
import { PrivacyPolicyPage } from './features/legal/PrivacyPolicyPage'
import { AccountDeletionPage } from './features/legal/AccountDeletionPage'
import { AdminDashboardPage } from './features/admin/AdminDashboardPage'
import { LoginScreen } from './features/auth/LoginScreen'
import { useAuth } from './features/auth/useAuth'
import { ReminderActionFallbackDialog } from './features/fcm/ReminderActionFallbackDialog'
import { useEffect, useState } from 'react'

type PendingReminderAction = { actionToken: string; title: string }

function App() {
  const route = useRoute()
  const { user, isLoading, error, signIn, signOut, deleteAccount } = useAuth()
  const [pendingReminderAction, setPendingReminderAction] = useState<PendingReminderAction | null>(null)
  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const handleMessage = (event: MessageEvent<unknown>) => {
      const data = event.data as { type?: string; actionToken?: string; title?: string } | null
      if (data?.type !== 'nudgee-reminder-action-fallback' || !data.actionToken) return
      setPendingReminderAction({ actionToken: data.actionToken, title: data.title ?? 'Nudgee reminder' })
    }
    navigator.serviceWorker.addEventListener('message', handleMessage)
    return () => navigator.serviceWorker.removeEventListener('message', handleMessage)
  }, [])
  useEffect(() => {
    if (!user?.isNewUser || route !== routes.home) return
    const onboardingKey = `nudgee-notification-onboarding:${user.id}`
    if (localStorage.getItem(onboardingKey)) return
    localStorage.setItem(onboardingKey, 'shown')
    window.history.pushState({}, '', routes.notificationSettings)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, [route, user])
  const page = route === routes.privacy ? <PrivacyPolicyPage />
    : route === routes.accountDeletion ? <AccountDeletionPage />
      : isLoading ? <main className="app-shell"><p className="loading-message">Loading Nudgee…</p></main>
        : !user ? <LoginScreen error={error} onSignIn={signIn} />
          : route === routes.admin ? <AdminDashboardPage />
            : route === routes.notificationSettings ? <NotificationSettingsPage userId={user.id} />
              : route === routes.settings ? <SettingsPage user={user} onSignOut={signOut} onDeleteAccount={deleteAccount} />
                : route === routes.calendar ? <CalendarPage user={user} />
                  : <TodoPage user={user} onSignOut={signOut} />

  return <>{page}{user && pendingReminderAction ? <ReminderActionFallbackDialog {...pendingReminderAction} onClose={() => setPendingReminderAction(null)} /> : null}</>
}

export default App
