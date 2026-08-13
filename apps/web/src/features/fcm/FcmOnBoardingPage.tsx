import {useEffect, useRef, useState} from 'react'
import {getServiceWorkerStatus, showLocalNotification, startFcmTest} from './fcmService'

type SetupState = 'idle' | 'requesting' | 'ready' | 'error'

export function FcmOnBoardingPage() {
    const [state, setState] = useState<SetupState>('idle')
    const [token, setToken] = useState('')
    const [message, setMessage] = useState('')
    const [workerStatus, setWorkerStatus] = useState('')
    const stopListeningRef = useRef<(() => void) | null>(null)

    useEffect(() => {
        return () => stopListeningRef.current?.()
    }, [])

    async function handleEnable() {
        setState('requesting')
        setMessage('Requesting browser permission and an FCM registration token…')

        try {
            stopListeningRef.current?.()
            const result = await startFcmTest((receivedMessage) => {
                setMessage(`Received: ${receivedMessage}`)
            })
            stopListeningRef.current = result.stopListening
            setToken(result.token)
            setState('ready')
            setMessage('Ready. Copy this token into Firebase Console, then send a test notification.')
            const status = await getServiceWorkerStatus()
            setWorkerStatus(`Service worker: ${status.state} · ${new URL(status.scope).pathname}`)
        } catch (error) {
            setState('error')
            setMessage(error instanceof Error ? error.message : 'FCM setup failed. Please try again.')
        }
    }

    async function copyToken() {
        await navigator.clipboard.writeText(token)
        setMessage('Token copied. Paste it into Firebase Console → Messaging → Send test message.')
    }

    function handleBrowserTest() {
        try {
            showLocalNotification()
            setMessage('Browser notification sent locally. If you saw it, permission and OS notification settings are working.')
        } catch (error) {
            setState('error')
            setMessage(error instanceof Error ? error.message : 'Browser notification test failed.')
        }
    }

    return (
        <main className="app-shell">
            <section className="fcm-test-card" aria-labelledby="fcm-test-title">
                <div className="brand-mark" aria-hidden="true">n</div>
                <span className="eyebrow">Nudgee developer tool</span>
                <h1 id="fcm-test-title">FCM test lab</h1>
                <p className="fcm-intro">Verify that this browser can receive a Firebase Cloud Messaging notification
                    before wiring up scheduled task delivery.</p>

                <ol className="test-steps">
                    <li className={state === 'idle' ? 'active' : 'complete'}><span>1</span>
                        <div><strong>Enable browser notifications</strong><small>Request permission and create an FCM
                            registration token.</small></div>
                    </li>
                    <li className={state === 'ready' ? 'active' : ''}><span>2</span>
                        <div><strong>Copy the token</strong><small>Use it to target only this browser.</small></div>
                    </li>
                    <li><span>3</span>
                        <div><strong>Send from Firebase Console</strong><small>Open Messaging, then choose Send test
                            message.</small></div>
                    </li>
                </ol>

                <button className="enable-fcm-button" type="button" disabled={state === 'requesting'}
                        onClick={() => void handleEnable()}>
                    {state === 'requesting' ? 'Setting up…' : state === 'ready' ? 'Refresh FCM token' : 'Enable test notifications'}
                </button>
                {state === 'ready' && <div className="diagnostic-actions">
                    <button type="button" onClick={handleBrowserTest}>Test browser notification</button>
                    <p className="test-message">For this test page, a message will show as a browser notification
                        whether this tab is active or in the background.</p>{workerStatus &&
                    <p className="worker-status">{workerStatus}</p>}</div>}

                {message && <p className={state === 'error' ? 'test-message error' : 'test-message'}
                               role="status">{message}</p>}

                {token && <section className="token-panel" aria-labelledby="token-title">
                    <div><h2 id="token-title">FCM registration token</h2><p>Keep this token private. It identifies this
                        browser’s push subscription.</p></div>
                    <code>{token}</code>
                    <button type="button" onClick={() => void copyToken()}>Copy token</button>
                </section>}

                <aside className="fcm-note"><strong>Test tip</strong><p>After sending, move this browser tab to the
                    background to see the native notification. Messages received while the page is active are shown
                    above.</p></aside>
            </section>
        </main>
    )
}
