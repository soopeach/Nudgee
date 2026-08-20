import { getMessaging, getToken, isSupported } from 'firebase/messaging'
import { isFirebaseConfigured } from '../fcm/firebaseConfig'
import { supabase } from '../../lib/supabase'

export type DevicePlatform = 'web' | 'ios' | 'android' | 'desktop'

const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY
const webInstallationIdKey = 'nudgee.web-installation-id'
const registrationCacheKey = 'nudgee.web-token-registration'
const registrationCacheTtlMs = 60 * 60 * 1000
const notificationActionCapabilityVersion = 'notification-actions-v1'

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

export async function registerDeviceToken(
  userId: string,
  token: string,
  platform: DevicePlatform,
  metadata: { deviceName?: string; appVersion?: string; installationId?: string } = {},
) {
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
    p_installation_id: metadata.installationId ?? (platform === 'web' ? getWebInstallationId() ?? null : null),
  })
  if (error) throw error
}

export async function registerCurrentDevice(userId: string) {
  const platform = detectPlatform()
  if (platform !== 'web') return false
  const token = await getWebToken()
  if (!token) return false
  const installationId = getWebInstallationId()
  if (hasRecentRegistration(userId, token, installationId)) return true

  await registerDeviceToken(userId, token, platform, {
    deviceName: navigator.userAgent.slice(0, 120),
    appVersion: notificationActionCapabilityVersion,
    installationId,
  })
  rememberRegistration(userId, token, installationId)
  return true
}

/** A browser profile shares localStorage across tabs but not across profiles. */
function getWebInstallationId(): string | undefined {
  try {
    const existing = window.localStorage.getItem(webInstallationIdKey)
    if (existing) return existing
    const installationId = createWebInstallationId()
    window.localStorage.setItem(webInstallationIdKey, installationId)
    return installationId
  } catch {
    return undefined
  }
}

function createWebInstallationId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()

  // Older browser fallback: preserve UUID shape because Supabase validates
  // installation_id as a PostgreSQL uuid.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16)
    const value = character === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

type RegistrationCache = {
  userId: string
  token: string
  installationId?: string
  capabilityVersion?: string
  registeredAt: number
}

function hasRecentRegistration(userId: string, token: string, installationId?: string): boolean {
  try {
    const cached = JSON.parse(window.localStorage.getItem(registrationCacheKey) ?? 'null') as RegistrationCache | null
    return cached?.userId === userId &&
      cached.token === token &&
      cached.installationId === installationId &&
      cached.capabilityVersion === notificationActionCapabilityVersion &&
      Date.now() - cached.registeredAt < registrationCacheTtlMs
  } catch {
    return false
  }
}

function rememberRegistration(userId: string, token: string, installationId?: string) {
  try {
    const cache: RegistrationCache = {
      userId,
      token,
      installationId,
      capabilityVersion: notificationActionCapabilityVersion,
      registeredAt: Date.now(),
    }
    window.localStorage.setItem(registrationCacheKey, JSON.stringify(cache))
  } catch {
    // Storage is optional; the RPC remains the server-side source of truth.
  }
}
