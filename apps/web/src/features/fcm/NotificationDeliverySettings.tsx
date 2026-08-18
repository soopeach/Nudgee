import { useEffect, useRef, useState } from 'react'
import { registerDeviceToken } from '../devices/deviceRegistration'
import { getServiceWorkerStatus, showLocalNotification, startFcmTest } from './fcmService'

type SetupState = 'idle' | 'requesting' | 'ready' | 'error'

type NotificationDeliverySettingsProps = {
  userId: string
  compact?: boolean
}

function getPermission(): NotificationPermission | 'unavailable' {
  return 'Notification' in window ? Notification.permission : 'unavailable'
}

export function NotificationDeliverySettings({ userId, compact = false }: NotificationDeliverySettingsProps) {
  const [state, setState] = useState<SetupState>('idle')
  const [message, setMessage] = useState('')
  const [permission, setPermission] = useState<NotificationPermission | 'unavailable'>(getPermission)
  const stopListeningRef = useRef<(() => void) | null>(null)
  const isEnabled = permission === 'granted'
  const isPermissionBlocked = permission === 'denied'
  const permissionLabel = { default: 'Not set', denied: 'Blocked', granted: 'On', unavailable: 'Unavailable' }[permission]

  useEffect(() => () => stopListeningRef.current?.(), [])

  async function enableNotifications() {
    if (permission === 'unavailable') return
    setState('requesting')
    setMessage(permission === 'default' ? 'Your browser will now ask for notification permission…' : 'Setting up notifications…')
    try {
      stopListeningRef.current?.()
      const result = await startFcmTest((receivedMessage) => setMessage(`Notification received: ${receivedMessage}`))
      await registerDeviceToken(userId, result.token, 'web', { deviceName: navigator.userAgent.slice(0, 120) })
      stopListeningRef.current = result.stopListening
      await getServiceWorkerStatus()
      setPermission(getPermission())
      setState('ready')
      setMessage('Notifications are on for this browser.')
    } catch (error) {
      setState('error')
      setPermission(getPermission())
      setMessage(error instanceof Error ? error.message : 'Notifications could not be enabled. Please try again.')
    }
  }

  function sendBrowserTest() {
    try {
      showLocalNotification()
      setMessage('Test notification sent. If you saw it, this browser is ready for Nudgee reminders.')
    } catch (error) {
      setState('error')
      setMessage(error instanceof Error ? error.message : 'Test notification could not be shown.')
    }
  }

  return (
    <section className={compact ? 'notification-delivery-settings compact' : 'notification-delivery-settings'} aria-label="Browser notification settings">
      <div className="settings-row" aria-label="Browser notification status"><div className="settings-icon" aria-hidden="true">⌁</div><div><strong>Browser notifications</strong><small>Receive reminders on this device.</small></div><span className={isEnabled ? 'status-pill enabled' : 'status-pill'}>{permissionLabel}</span></div>
      {!isEnabled && <button className="enable-fcm-button" type="button" disabled={state === 'requesting' || isPermissionBlocked || permission === 'unavailable'} onClick={() => void enableNotifications()}>{state === 'requesting' ? 'Waiting for permission…' : 'Turn on notifications'}</button>}
      {isEnabled && <div className="settings-actions"><button className="enable-fcm-button" type="button" onClick={sendBrowserTest}>Send a test notification</button><button className="subtle-button" type="button" onClick={() => void enableNotifications()}>Refresh connection</button></div>}
      {message && <p className={state === 'error' ? 'test-message error' : 'test-message'} role="status">{message}</p>}
      {isPermissionBlocked && <aside className="permission-help" role="alert"><strong>Notifications are blocked</strong><p>Use the site-controls icon beside the address bar, change Notifications to <em>Allow</em>, then reload this page.</p></aside>}
      {permission === 'default' && <p className="permission-prompt-hint">Clicking the button opens your browser’s permission prompt.</p>}
      {!compact && <section className="device-status" aria-labelledby="device-status-title"><div className="device-status-heading"><div><span className="eyebrow">Device check</span><h2 id="device-status-title">Notification delivery</h2></div><span className="status-pill">Needs a test</span></div><p>Browser permission is {permissionLabel.toLowerCase()}. Your operating system may still silence notifications because of Focus / Do Not Disturb.</p><button type="button" onClick={sendBrowserTest} disabled={!isEnabled}>Check device notifications</button><small>Seeing the test notification confirms that browser and current OS notification settings allow delivery.</small></section>}
      {!compact && <aside className="fcm-note"><strong>Good to know</strong><p>Nudgee only uses notification permission for task reminders.</p></aside>}
    </section>
  )
}
