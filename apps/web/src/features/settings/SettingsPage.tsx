import { useEffect, useState } from 'react'
import type { AuthenticatedUser } from '../auth/types'
import { getReminderParseUsage, type ReminderParseUsage } from '../todos/naturalLanguageService'
import { navigateTo, routes } from '../navigation/routes'
import { MobileBottomNavigation } from '../navigation/MobileBottomNavigation'
import { NotificationDeliverySettings } from '../fcm/NotificationDeliverySettings'
import { DeleteAccountDialog } from './DeleteAccountDialog'

type SettingsPageProps = {
  user: AuthenticatedUser
  onSignOut: () => Promise<void>
  onDeleteAccount: () => Promise<void>
}

export function SettingsPage({ user, onSignOut, onDeleteAccount }: SettingsPageProps) {
  const [usage, setUsage] = useState<ReminderParseUsage | null>(null)
  const [isLoadingUsage, setIsLoadingUsage] = useState(true)
  const [isSigningOut, setIsSigningOut] = useState(false)
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false)
  const [isDeletingAccount, setIsDeletingAccount] = useState(false)
  const [deleteAccountError, setDeleteAccountError] = useState<string | null>(null)
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  const availableParses = (usage?.remainingFreeParses ?? 0) + (usage?.bonusCredits ?? 0)

  useEffect(() => {
    let isMounted = true
    void getReminderParseUsage()
      .then((nextUsage) => { if (isMounted) setUsage(nextUsage) })
      .catch(() => { if (isMounted) setUsage(null) })
      .finally(() => { if (isMounted) setIsLoadingUsage(false) })
    return () => { isMounted = false }
  }, [])

  async function signOut() {
    setIsSigningOut(true)
    try { await onSignOut() } finally { setIsSigningOut(false) }
  }

  async function deleteAccount() {
    setIsDeletingAccount(true)
    setDeleteAccountError(null)
    try {
      await onDeleteAccount()
    } catch (error) {
      setDeleteAccountError(error instanceof Error ? error.message : 'Nudgee could not delete your account. Please try again.')
      setIsDeletingAccount(false)
    }
  }

  return (
    <main className="app-shell app-shell-with-mobile-nav">
      <section className="settings-page" aria-labelledby="settings-page-title">
        <header className="settings-page-header">
          <button className="home-link" type="button" onClick={() => navigateTo(routes.home)}>← Home</button>
          <span className="eyebrow">Nudgee</span>
          <h1 id="settings-page-title">Settings</h1>
          <p>Keep your nudges, account, and this browser in sync.</p>
        </header>

        <section className="settings-group" aria-labelledby="account-settings-title">
          <div className="settings-group-title"><span className="eyebrow">Account</span><h2 id="account-settings-title">Your Nudgee</h2></div>
          <div className="settings-profile-card">
            <div className="settings-avatar" aria-hidden="true">{user.photoURL ? <img src={user.photoURL} alt="" /> : user.displayName.slice(0, 1)}</div>
            <div><strong>{user.displayName}</strong><small>{user.email}</small></div>
          </div>
          <div className="settings-info-row"><span className="settings-row-icon" aria-hidden="true">◷</span><div><strong>Time zone</strong><small>{timezone}</small></div><span className="settings-row-note">Used for daily AI reminders</span></div>
        </section>

        <section className="settings-group" aria-labelledby="ai-settings-title">
          <div className="settings-group-title"><span className="eyebrow">AI reminders</span><h2 id="ai-settings-title">Your allowance</h2></div>
          <div className="settings-allowance-card">
            <div><strong>{isLoadingUsage ? 'Checking your allowance…' : `${usage?.remainingFreeParses ?? 0} of ${usage?.dailyFreeParseLimit ?? 10} free reminders left today`}</strong><small>{isLoadingUsage ? 'Connecting to Nudgee…' : usage?.bonusCredits ? `${usage.bonusCredits} reward credits ready · resets at local midnight` : 'Resets at local midnight'}</small></div>
            <span aria-hidden="true">✦</span>
          </div>
        </section>

        <section className="settings-group" aria-labelledby="delivery-settings-title"><div className="settings-group-title"><span className="eyebrow">Delivery</span><h2 id="delivery-settings-title">Notifications</h2></div><NotificationDeliverySettings userId={user.id} compact /></section>

        <section className="settings-group settings-support-group" aria-labelledby="support-settings-title">
          <div className="settings-group-title"><span className="eyebrow">Support</span><h2 id="support-settings-title">Keep Nudgee growing</h2></div>
          <a className="settings-navigation-row" href="https://buymeacoffee.com/hsjeon584z" target="_blank" rel="noreferrer"><span className="settings-row-icon" aria-hidden="true">♡</span><span><strong>Buy me a coffee</strong><small>Support the little nudges</small></span><b>↗</b></a>
          <a className="settings-navigation-row" href={routes.privacy}><span className="settings-row-icon" aria-hidden="true">⌁</span><span><strong>Privacy Policy</strong><small>How Nudgee handles your information</small></span><b>›</b></a>
          <button className="settings-sign-out" type="button" disabled={isSigningOut} onClick={() => void signOut()}>{isSigningOut ? 'Signing out…' : 'Sign out'}</button>
          <button className="settings-delete-account" type="button" onClick={() => { setDeleteAccountError(null); setIsDeleteDialogOpen(true) }}>Delete account</button>
        </section>
      </section>
      <MobileBottomNavigation />
      {isDeleteDialogOpen ? <DeleteAccountDialog isDeleting={isDeletingAccount} error={deleteAccountError} onConfirm={() => void deleteAccount()} onDismiss={() => setIsDeleteDialogOpen(false)} /> : null}
    </main>
  )
}
