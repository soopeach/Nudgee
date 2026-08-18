import { useEffect, useState } from 'react'

type DeleteAccountDialogProps = { isDeleting: boolean; error: string | null; onConfirm: () => void; onDismiss: () => void }

export function DeleteAccountDialog({ isDeleting, error, onConfirm, onDismiss }: DeleteAccountDialogProps) {
  const [confirmation, setConfirmation] = useState('')
  const canDelete = confirmation === 'DELETE' && !isDeleting
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) { if (event.key === 'Escape' && !isDeleting) onDismiss() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [isDeleting, onDismiss])

  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !isDeleting) onDismiss() }}>
    <section className="delete-dialog delete-account-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-account-title">
      <span className="dialog-icon" aria-hidden="true">!</span>
      <p className="dialog-eyebrow">Permanent action</p>
      <h2 id="delete-account-title">Delete your account?</h2>
      <p>This permanently removes your tasks, reminders, device registrations, and AI reminder history. <strong>This cannot be undone.</strong></p>
      <label className="delete-account-confirmation"><span>Type <b>DELETE</b> to continue</span><input autoFocus value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={isDeleting} aria-label="Type DELETE to confirm" /></label>
      {error ? <p className="delete-account-error" role="alert">{error}</p> : null}
      <div className="dialog-actions"><button className="dialog-cancel" type="button" disabled={isDeleting} onClick={onDismiss}>Cancel</button><button className="dialog-delete" type="button" disabled={!canDelete} onClick={onConfirm}>{isDeleting ? 'Deleting…' : 'Delete account'}</button></div>
    </section>
  </div>
}
