import { useEffect, useRef } from 'react'
import type { Todo } from './types'

type DeleteTodoDialogProps = {
  todo: Todo
  onCancel: () => void
  onConfirm: () => void
  onStopFutureReminders?: () => void
}

export function DeleteTodoDialog({ todo, onCancel, onConfirm, onStopFutureReminders }: DeleteTodoDialogProps) {
  const cancelButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    cancelButtonRef.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancel()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onCancel])

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onCancel}>
      <section className="delete-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-dialog-title" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-icon" aria-hidden="true">×</div>
        <h2 id="delete-dialog-title">{onStopFutureReminders ? 'Manage repeating reminder' : 'Delete this task?'}</h2>
        <p>{onStopFutureReminders ? <>Skip <strong>{todo.title}</strong> and keep the next reminder scheduled, or stop its future reminders. Completed history stays in your account.</> : <><strong>{todo.title}</strong> will be removed from your list. This can’t be undone.</>}</p>
        <div className="dialog-actions">
          <button ref={cancelButtonRef} className="dialog-cancel" type="button" onClick={onCancel}>Cancel</button>
          <button className="dialog-delete" type="button" onClick={onConfirm}>{onStopFutureReminders ? 'Skip this occurrence' : 'Delete task'}</button>
        </div>
        {onStopFutureReminders && <button className="dialog-text-action" type="button" onClick={onStopFutureReminders}>Stop future reminders</button>}
      </section>
    </div>
  )
}
