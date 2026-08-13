import { useEffect, useRef, useState } from 'react'
import { getServiceWorkerStatus, showLocalNotification, startFcmTest } from './fcmService'
import { navigateTo, routes } from '../navigation/routes'

type SetupState = 'idle' | 'requesting' | 'ready' | 'error'

export function NotificationSettingsPage() {
  const [state, setState] = useState<SetupState>('idle')
  const [message, setMessage] = useState('')
  const [permission, setPermission] = useState<NotificationPermission>(Notification.permission)
  const isEnabled = permission === 'granted'
  const isPermissionBlocked = permission === 'denied'
  const stopListeningRef = useRef<(() => void) | null>(null)

  useEffect(() => () => stopListeningRef.current?.(), [])

  async function enableNotifications() {
    setState('requesting')
    setMessage(permission === 'default' ? 'Your browser will now ask for notification permission…' : 'Setting up notifications…')

    try {
      stopListeningRef.current?.()
      const result = await startFcmTest((receivedMessage) => setMessage(`Notification received: ${receivedMessage}`))
      stopListeningRef.current = result.stopListening
      await getServiceWorkerStatus()
      setPermission(Notification.permission)
      setState('ready')
      setMessage('Notifications are on for this browser.')
    } catch (error) {
      setState('error')
      setPermission(Notification.permission)
      setMessage(error instanceof Error ? error.message : 'Notifications could not be enabled. Please try again.')
    }
  }

  const permissionLabel = {
    default: 'Not set',
    denied: 'Blocked',
    granted: 'On',
  }[permission]

  function sendBrowserTest() {
    try {
      showLocalNotification()
      setMessage('Test notification sent. If you saw it, your browser is ready for Nudgee reminders.')
    } catch (error) {
      setState('error')
      setMessage(error instanceof Error ? error.message : 'Test notification could not be shown.')
    }
  }

  return (
    <main className="app-shell">
      <section className="notification-settings-card" aria-labelledby="settings-title">
        <div className="brand-mark" aria-hidden="true">n</div>
        <button className="home-link" type="button" onClick={() => navigateTo(routes.home)}>Go to Home <span>→</span></button>
        <span className="eyebrow">Nudgee</span>
        <h1 id="settings-title">Notification settings</h1>
        <p className="settings-intro">Let Nudgee gently remind you when your tasks are due.</p>

        <section className="settings-row" aria-label="Browser notification status">
          <div className="settings-icon" aria-hidden="true">⌁</div>
          <div><strong>Browser notifications</strong><small>Receive reminders on this device.</small></div>
          <span className={isEnabled ? 'status-pill enabled' : 'status-pill'}>{permissionLabel}</span>
        </section>

        {!isEnabled && <button className="enable-fcm-button" type="button" disabled={state === 'requesting' || isPermissionBlocked} onClick={() => void enableNotifications()}>{state === 'requesting' ? 'Waiting for permission…' : 'Turn on notifications'}</button>}
        {isEnabled && <div className="settings-actions"><button className="enable-fcm-button" type="button" onClick={sendBrowserTest}>Send a test notification</button><button className="subtle-button" type="button" onClick={() => void enableNotifications()}>Refresh connection</button></div>}

        {message && <p className={state === 'error' ? 'test-message error' : 'test-message'} role="status">{message}</p>}
        {isPermissionBlocked && <aside className="permission-help" role="alert"><strong>Notifications are blocked</strong><p>Browsers only show the permission prompt once. Use the site-controls icon beside the address bar, change Notifications to <em>Allow</em>, then reload this page.</p></aside>}
        {permission === 'default' && <p className="permission-prompt-hint">Clicking the button should open your browser’s permission prompt. If it doesn’t, check whether another prompt is already open or blocked by the browser.</p>}

        <section className="device-status" aria-labelledby="device-status-title">
          <div className="device-status-heading"><div><span className="eyebrow">Device check</span><h2 id="device-status-title">Notification delivery</h2></div><span className="status-pill">Needs a test</span></div>
          <p>Browser permission is {permissionLabel.toLowerCase()}. Your operating system may still silence notifications because of system settings or Focus / Do Not Disturb.</p>
          <button type="button" onClick={sendBrowserTest} disabled={!isEnabled}>Check device notifications</button>
          <small>Seeing the test notification confirms that browser and current OS notification settings allow delivery.</small>
        </section>

        <aside className="fcm-note"><strong>Good to know</strong><p>When you click Turn on notifications, your browser’s permission prompt will open. Nudgee will only use it for task reminders.</p></aside>
      </section>
    </main>
  )
}
