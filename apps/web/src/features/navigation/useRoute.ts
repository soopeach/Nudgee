import { useEffect, useState } from 'react'
import { getCurrentRoute, type AppRoute } from './routes'

export function useRoute() {
  const [route, setRoute] = useState<AppRoute>(getCurrentRoute)

  useEffect(() => {
    const updateRoute = () => setRoute(getCurrentRoute())
    window.addEventListener('popstate', updateRoute)
    return () => window.removeEventListener('popstate', updateRoute)
  }, [])

  return route
}
