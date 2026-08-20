/*
 * Firebase Cloud Messaging service worker.
 * Replace the values below with the public web configuration from Firebase Console.
 * This file is intentionally separate: service workers cannot read Vite's .env at runtime.
 */
importScripts('https://www.gstatic.com/firebasejs/12.9.0/firebase-app-compat.js')
importScripts('https://www.gstatic.com/firebasejs/12.9.0/firebase-messaging-compat.js')

firebase.initializeApp({
  apiKey: "AIzaSyDSsPn9nc0dJ5JIdpzc3f_ysB_E8d7WDlU",
  authDomain: "nudgee-99.firebaseapp.com",
  projectId: "nudgee-99",
  storageBucket: "nudgee-99.firebasestorage.app",
  messagingSenderId: "381441435963",
  appId: "1:381441435963:web:101cd6ec943d8ea661ef1e",
})

const messaging = firebase.messaging()

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(clients.claim()))

// This is a publishable Supabase key, not a server secret. Notification
// actions use a separate short-lived, server-signed capability token.
const supabaseUrl = 'https://efeenkmzvbaawsmxikli.supabase.co'
const supabasePublishableKey = 'sb_publishable_Mx-6tZbXGcSdvldAMj_GlQ_NB8PAUJK'

async function sendReminderAction(action, actionToken) {
  const response = await fetch(`${supabaseUrl}/functions/v1/reminder-action`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: supabasePublishableKey,
    },
    body: JSON.stringify({ action, actionToken }),
  })
  if (!response.ok) throw new Error(`Reminder action failed (${response.status})`)
}

messaging.onBackgroundMessage((payload) => {
  const notification = payload.notification ?? {}
  const data = payload.data ?? {}
  const options = {
    body: notification.body ?? data.title ?? 'It is time for your reminder.',
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
  self.registration.showNotification(notification.title ?? 'Nudgee reminder', options)
})

self.addEventListener('notificationclick', (event) => {
  const { action, notification } = event
  const actionToken = notification.data?.actionToken
  notification.close()
  if ((action === 'snooze' || action === 'complete') && actionToken) {
    event.waitUntil(sendReminderAction(action, actionToken))
    return
  }
  event.waitUntil((async () => {
    const payload = { type: 'nudgee-reminder-action-fallback', actionToken, title: notification.data?.title ?? 'Nudgee reminder' }
    const openClients = await clients.matchAll({ type: 'window', includeUncontrolled: true })
    if (openClients.length > 0) {
      // A user can have several local/dev tabs open. Every live client gets
      // the event, while the first one is brought forward for the dialog.
      openClients.forEach((client) => client.postMessage(payload))
      await openClients[0].focus()
      return
    }
    const newClient = await clients.openWindow('/')
    if (!newClient) return
    // Give the newly opened page a moment to install its message listener.
    await new Promise((resolve) => setTimeout(resolve, 250))
    newClient.postMessage(payload)
  })())
})
