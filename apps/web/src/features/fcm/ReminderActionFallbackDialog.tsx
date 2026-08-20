import { useState } from 'react'
import { performReminderAction } from './reminderActionService'

type ReminderActionFallbackDialogProps = {
  title: string
  actionToken: string
  onClose: () => void
}

export function ReminderActionFallbackDialog({ title, actionToken, onClose }: ReminderActionFallbackDialogProps) {
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleAction(action: 'snooze' | 'complete') {
    setIsSaving(true)
    setError(null)
    try {
      await performReminderAction(action, actionToken)
      onClose()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not update this reminder.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="dialog-card reminder-action-dialog" role="dialog" aria-modal="true" aria-labelledby="reminder-action-title">
        <span className="reminder-action-spark" aria-hidden="true">✦</span>
        <p className="eyebrow">REMINDER TIME</p>
        <h2 id="reminder-action-title">{title}</h2>
        <p>What would you like to do?</p>
        {error ? <p className="form-message error" role="alert">{error}</p> : null}
        <div className="reminder-action-buttons">
          <button className="dialog-cancel" type="button" disabled={isSaving} onClick={() => void handleAction('snooze')}>Snooze 10m</button>
          <button className="add-button" type="button" disabled={isSaving} onClick={() => void handleAction('complete')}>{isSaving ? 'Saving…' : 'Complete'}</button>
        </div>
        <button className="dialog-cancel reminder-action-dismiss" type="button" disabled={isSaving} onClick={onClose}>Not now</button>
      </section>
    </div>
  )
}
