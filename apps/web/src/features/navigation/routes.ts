export const routes = {
  home: '/',
  notificationSettings: '/notification-settings',
} as const

export type AppRoute = typeof routes[keyof typeof routes]

export function getCurrentRoute(): AppRoute {
  return window.location.pathname === routes.notificationSettings ? routes.notificationSettings : routes.home
}

export function navigateTo(route: AppRoute) {
  window.history.pushState({}, '', route)
  window.dispatchEvent(new PopStateEvent('popstate'))
}
