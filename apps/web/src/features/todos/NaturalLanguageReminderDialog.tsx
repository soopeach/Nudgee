import { useEffect, useRef } from 'react'
import type { ParsedReminder } from './naturalLanguageService'

type ParsingReminderDialogProps = {
  prompt: string
}

type NaturalLanguageReminderDialogProps = {
  parsedReminder: ParsedReminder
  prompt: string
  isSaving: boolean
  onClose: () => void
  onSchedule: () => void
  onEditDetails: () => void
}

function formatReminderTime(value: string) {
  return new Intl.DateTimeFormat('en', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

export function ParsingReminderDialog({ prompt }: ParsingReminderDialogProps) {
  return (
    <div className="dialog-backdrop parsing-backdrop" role="presentation">
      <section className="natural-language-dialog parsing-dialog" role="dialog" aria-modal="true" aria-labelledby="parsing-dialog-title">
        <span className="parsing-orbit" aria-hidden="true"><span /></span>
        <p className="dialog-eyebrow">NUDGEE IS LISTENING</p>
        <h2 id="parsing-dialog-title">Understanding your reminder</h2>
        <p>Finding the task and the best time for it.</p>
        <blockquote>“{prompt}”</blockquote>
        <div className="dialog-status" role="status" aria-live="polite"><span className="loading-spinner" aria-hidden="true" />This only takes a moment</div>
      </section>
    </div>
  )
}

export function NaturalLanguageReminderDialog({
  parsedReminder,
  prompt,
  isSaving,
  onClose,
  onSchedule,
  onEditDetails,
}: NaturalLanguageReminderDialogProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const needsTime = parsedReminder.needsClarification || !parsedReminder.notifyAt

  useEffect(() => { closeButtonRef.current?.focus() }, [])

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={isSaving ? undefined : onClose}>
      <section className="natural-language-dialog" role="dialog" aria-modal="true" aria-labelledby="natural-language-dialog-title" onMouseDown={(event) => event.stopPropagation()}>
        <div className="natural-dialog-icon" aria-hidden="true">✦</div>
        <p className="dialog-eyebrow">{needsTime ? 'ONE MORE DETAIL' : 'READY TO SCHEDULE'}</p>
        <h2 id="natural-language-dialog-title">{needsTime ? 'When should Nudgee remind you?' : 'Ready to schedule?'}</h2>
        <p>{needsTime ? (parsedReminder.clarification || 'Choose a date and time before creating this reminder.') : 'Please check the details before Nudgee saves it.'}</p>
        <div className="parsed-reminder-card">
          <strong>{parsedReminder.title}</strong>
          {parsedReminder.notifyAt ? <time dateTime={parsedReminder.notifyAt}>Nudge · {formatReminderTime(parsedReminder.notifyAt)}</time> : <span>Reminder time still needed</span>}
        </div>
        <small className="parsed-prompt">From: “{prompt}”</small>
        <div className="dialog-actions natural-dialog-actions">
          <button ref={closeButtonRef} className="dialog-cancel" type="button" disabled={isSaving} onClick={onClose}>{needsTime ? 'Keep editing' : 'Not now'}</button>
          <button className="dialog-primary" type="button" disabled={isSaving} onClick={needsTime ? onEditDetails : onSchedule}>{isSaving ? 'Scheduling…' : needsTime ? 'Set reminder time' : 'Schedule'}</button>
        </div>
        {!needsTime && <button className="dialog-text-action" type="button" disabled={isSaving} onClick={onEditDetails}>Edit details</button>}
      </section>
    </div>
  )
}
