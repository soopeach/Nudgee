import { getMessaging, getToken, isSupported } from 'firebase/messaging'
import { isFirebaseConfigured } from '../fcm/firebaseConfig'
import { supabase } from '../../lib/supabase'

export type DevicePlatform = 'web' | 'ios' | 'android' | 'desktop'

const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY

function detectPlatform(): DevicePlatform {
  // Native clients will call registerDeviceToken with their explicit platform.
  // A browser is always registered as web, including a browser on a phone.
  return 'web'
}

async function getWebToken(): Promise<string | null> {
  if (!isFirebaseConfigured || !vapidKey || !('Notification' in window) || !('serviceWorker' in navigator)) return null
  if (Notification.permission !== 'granted' || !await isSupported()) return null
  const serviceWorkerRegistration = await navigator.serviceWorker.register('/firebase-messaging-sw.js')
  const token = await getToken(getMessaging(), { vapidKey, serviceWorkerRegistration })
  return token || null
}

export async function registerDeviceToken(userId: string, token: string, platform: DevicePlatform, metadata: { deviceName?: string; appVersion?: string } = {}) {
  if (!supabase) throw new Error('Supabase is not configured.')
  const payload = { user_id: userId, platform, token, is_active: true, last_seen_at: new Date().toISOString(), device_name: metadata.deviceName ?? null, app_version: metadata.appVersion ?? null }
  const { data: existing, error: lookupError } = await supabase.from('device_tokens').select('id').eq('user_id', userId).eq('platform', platform).eq('token', token).maybeSingle()
  if (lookupError) throw lookupError
  if (existing?.id) {
    const { error } = await supabase.from('device_tokens').update(payload).eq('id', existing.id).eq('user_id', userId)
    if (error) throw error
  } else {
    const { error } = await supabase.from('device_tokens').insert(payload)
    if (error) throw error
  }
}

export async function registerCurrentDevice(userId: string) {
  const platform = detectPlatform()
  if (platform !== 'web') return false
  const token = await getWebToken()
  if (!token) return false
  await registerDeviceToken(userId, token, platform, { deviceName: navigator.userAgent.slice(0, 120) })
  return true
}
