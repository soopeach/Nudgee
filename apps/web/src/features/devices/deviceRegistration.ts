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
  await navigator.serviceWorker.register('/firebase-messaging-sw.js')
  const serviceWorkerRegistration = await navigator.serviceWorker.ready
  if (!serviceWorkerRegistration.active) return null
  const token = await getToken(getMessaging(), { vapidKey, serviceWorkerRegistration })
  return token || null
}

export async function registerDeviceToken(userId: string, token: string, platform: DevicePlatform, metadata: { deviceName?: string; appVersion?: string } = {}) {
  if (!supabase) throw new Error('Supabase is not configured.')
  if (!userId) throw new Error('User is not authenticated.')
  // The RPC atomically transfers ownership when the same browser token is
  // encountered after an account switch. Direct client writes cannot safely
  // deactivate a previous user's row under per-user RLS.
  const { error } = await supabase.rpc('claim_device_token', {
    p_platform: platform,
    p_token: token,
    p_device_name: metadata.deviceName ?? null,
    p_app_version: metadata.appVersion ?? null,
  })
  if (error) throw error
}

export async function registerCurrentDevice(userId: string) {
  const platform = detectPlatform()
  if (platform !== 'web') return false
  const token = await getWebToken()
  if (!token) return false
  await registerDeviceToken(userId, token, platform, { deviceName: navigator.userAgent.slice(0, 120) })
  return true
}
