import { navigateTo, routes, type AppRoute } from './routes'
import { useRoute } from './useRoute'
import type { ReactNode } from 'react'

type NavigationItem = {
  route: AppRoute
  label: string
  icon: ReactNode
}

const navigationItems: NavigationItem[] = [
  {
    route: routes.home,
    label: 'Home',
    icon: <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V10Z" /><path d="M9 21v-6h6v6" /></svg>,
  },
  {
    route: routes.calendar,
    label: 'Calendar',
    icon: <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M7 3v4M17 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01" /></svg>,
  },
  {
    route: routes.settings,
    label: 'Settings',
    icon: <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h9M17 7h3M4 17h3M11 17h9M13 4v6M7 14v6" /><circle cx="15" cy="7" r="2" /><circle cx="9" cy="17" r="2" /></svg>,
  },
]

export function MobileBottomNavigation() {
  const route = useRoute()

  return (
    <nav className="mobile-bottom-navigation" aria-label="Main navigation">
      {navigationItems.map((item) => <button key={item.route} className={route === item.route ? 'selected' : ''} type="button" aria-current={route === item.route ? 'page' : undefined} onClick={() => navigateTo(item.route)}><span>{item.icon}</span><small>{item.label}</small></button>)}
    </nav>
  )
}
