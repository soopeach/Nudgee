import { NotificationSettingsPage } from './features/fcm/NotificationSettingsPage'
import { routes } from './features/navigation/routes'
import { useRoute } from './features/navigation/useRoute'
import { TodoPage } from './features/todos/TodoPage'
import { LoginScreen } from './features/auth/LoginScreen'
import { useAuth } from './features/auth/useAuth'

function App() {
  const route = useRoute()
  const { user, isLoading, error, signIn, signOut } = useAuth()
  if (route === routes.notificationSettings) return <NotificationSettingsPage />
  if (isLoading) return <main className="app-shell"><p className="loading-message">Loading Nudgee…</p></main>
  if (!user) return <LoginScreen error={error} onSignIn={signIn} />

  return <TodoPage user={user} onSignOut={signOut} />
}

export default App
