import { NotificationSettingsPage } from './features/fcm/NotificationSettingsPage'
import { routes } from './features/navigation/routes'
import { useRoute } from './features/navigation/useRoute'
import { TodoPage } from './features/todos/TodoPage'
import { SettingsPage } from './features/settings/SettingsPage'
import { CalendarPage } from './features/calendar/CalendarPage'
import { PrivacyPolicyPage } from './features/legal/PrivacyPolicyPage'
import { AccountDeletionPage } from './features/legal/AccountDeletionPage'
import { LoginScreen } from './features/auth/LoginScreen'
import { useAuth } from './features/auth/useAuth'
import { useEffect } from 'react'

function App() {
  const route = useRoute()
  const { user, isLoading, error, signIn, signOut, deleteAccount } = useAuth()
  useEffect(() => {
    if (!user?.isNewUser || route !== routes.home) return
    const onboardingKey = `nudgee-notification-onboarding:${user.id}`
    if (localStorage.getItem(onboardingKey)) return
    localStorage.setItem(onboardingKey, 'shown')
    window.history.pushState({}, '', routes.notificationSettings)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, [route, user])
  if (route === routes.privacy) return <PrivacyPolicyPage />
  if (route === routes.accountDeletion) return <AccountDeletionPage />
  if (isLoading) return <main className="app-shell"><p className="loading-message">Loading Nudgee…</p></main>
  if (!user) return <LoginScreen error={error} onSignIn={signIn} />
  if (route === routes.notificationSettings) return <NotificationSettingsPage userId={user.id} />
  if (route === routes.settings) return <SettingsPage user={user} onSignOut={signOut} onDeleteAccount={deleteAccount} />
  if (route === routes.calendar) return <CalendarPage user={user} />

  return <TodoPage user={user} onSignOut={signOut} />
}

export default App
