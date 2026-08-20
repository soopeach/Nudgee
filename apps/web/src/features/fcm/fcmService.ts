import { getMessaging, getToken, isSupported, onMessage } from 'firebase/messaging'
import { isFirebaseConfigured } from './firebaseConfig'

const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY

export type FcmTestResult = {
  token: string
  stopListening: () => void
}

export type ServiceWorkerStatus = {
  scope: string
  state: string
}

type ActionableNotificationOptions = NotificationOptions & {
  renotify?: boolean
  actions?: Array<{ action: string; title: string; icon?: string }>
}

export async function startFcmTest(onForegroundMessage: (message: string) => void): Promise<FcmTestResult> {
  if (!isFirebaseConfigured) {
    throw new Error('Firebase configuration is incomplete. Check your VITE_FIREBASE_* values and restart Vite.')
  }
  if (!vapidKey) {
    throw new Error('VITE_FIREBASE_VAPID_KEY is missing. Add the public Web Push certificate key and restart Vite.')
  }
  if (!('Notification' in window) || !('serviceWorker' in navigator)) {
    throw new Error('This browser does not support web notifications or service workers.')
  }
  if (!await isSupported()) {
    throw new Error('Firebase Cloud Messaging is not supported by this browser.')
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error('Notification permission was not granted. Allow notifications in your browser settings and try again.')
  }

  await navigator.serviceWorker.register('/firebase-messaging-sw.js')
  const serviceWorkerRegistration = await navigator.serviceWorker.ready
  if (!serviceWorkerRegistration.active) {
    throw new Error('Firebase messaging service worker is not active yet. Reload the page and try again.')
  }
  const messaging = getMessaging()
  const token = await getToken(messaging, { vapidKey, serviceWorkerRegistration })
  if (!token) {
    throw new Error('FCM did not return a registration token. Check the VAPID key and service worker configuration.')
  }

  const stopListening = onMessage(messaging, (payload) => {
    const title = payload.notification?.title ?? 'Nudgee reminder'
    const body = payload.notification?.body ?? payload.data?.title ?? 'You have a reminder.'
    // FCM does not automatically display a native notification while the page
    // is in the foreground, so the app owns that presentation path.
    if (Notification.permission === 'granted') {
      const data = payload.data ?? {}
      const options: ActionableNotificationOptions = {
        body,
        data,
        tag: data.taskId ? `nudgee:${data.taskId}:${data.occurrence ?? '0'}` : undefined,
        renotify: true,
      }
      if (data.actionToken) {
        options.actions = [
          { action: 'snooze', title: 'Snooze 10m' },
          { action: 'complete', title: 'Complete' },
        ]
      }
      void serviceWorkerRegistration.showNotification(title, options)
    }
    onForegroundMessage(`${title}: ${body}`)
  })

  return { token, stopListening }
}

export async function getServiceWorkerStatus(): Promise<ServiceWorkerStatus> {
  const registration = await navigator.serviceWorker.getRegistration('/firebase-messaging-sw.js')
  const worker = registration?.active ?? registration?.waiting ?? registration?.installing
  if (!registration || !worker) {
    throw new Error('No active Firebase Messaging service worker was found. Enable test notifications first.')
  }
  return { scope: registration.scope, state: worker.state }
}

export function showLocalNotification() {
  if (Notification.permission !== 'granted') {
    throw new Error('Browser notification permission is not granted.')
  }
  new Notification('Nudgee browser test', { body: 'Your browser can display a notification.' })
}
