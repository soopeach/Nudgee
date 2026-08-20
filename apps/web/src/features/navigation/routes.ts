export const routes = {
  home: '/',
  privacy: '/privacy',
  accountDeletion: '/delete-account',
  calendar: '/calendar',
  settings: '/settings',
  notificationSettings: '/notification-settings',
  admin: '/admin',
} as const

export type AppRoute = typeof routes[keyof typeof routes]

export function getCurrentRoute(): AppRoute {
  if (window.location.pathname === routes.privacy) return routes.privacy
  if (window.location.pathname === routes.accountDeletion) return routes.accountDeletion
  if (window.location.pathname === routes.calendar) return routes.calendar
  if (window.location.pathname === routes.settings) return routes.settings
  if (window.location.pathname === routes.notificationSettings) return routes.notificationSettings
  if (window.location.pathname === routes.admin) return routes.admin
  return routes.home
}

export function navigateTo(route: AppRoute) {
  window.history.pushState({}, '', route)
  window.dispatchEvent(new PopStateEvent('popstate'))
}
