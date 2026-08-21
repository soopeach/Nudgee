import { useEffect, useState, type FormEvent } from 'react'
import { ReminderPicker } from './ReminderPicker'
import { RecurrencePicker } from './RecurrencePicker'
import { formatLocalDateTime } from './reminderDateTime'
import type { RecurrenceRule } from './recurrence'
import type { Todo } from './types'

type TodoDetailDialogProps = {
  todo: Todo
  onClose: () => void
  onSave: (title: string, notifyAt: string, recurrenceRule: RecurrenceRule) => Promise<void>
  onToggleCompleted: () => Promise<void>
  onDelete: () => void
}

export function TodoDetailDialog({ todo, onClose, onSave, onToggleCompleted, onDelete }: TodoDetailDialogProps) {
  const [title, setTitle] = useState(todo.title)
  const [notifyAt, setNotifyAt] = useState(formatLocalDateTime(new Date(todo.notifyAt)))
  const [recurrenceRule, setRecurrenceRule] = useState<RecurrenceRule>(todo.recurrenceRule as RecurrenceRule)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setTitle(todo.title)
    setNotifyAt(formatLocalDateTime(new Date(todo.notifyAt)))
    setRecurrenceRule(todo.recurrenceRule as RecurrenceRule)
    setError('')
  }, [todo])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!title.trim() || !notifyAt) return
    if (!todo.completed && new Date(notifyAt) <= new Date()) {
      setError('Please choose a future reminder time.')
      return
    }
    setIsSaving(true)
    setError('')
    try {
      await onSave(title.trim(), notifyAt, recurrenceRule)
      onClose()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Task could not be updated.')
    } finally { setIsSaving(false) }
  }

  async function toggleCompleted() {
    setIsSaving(true)
    setError('')
    try { await onToggleCompleted() } catch (caught) { setError(caught instanceof Error ? caught.message : 'Task could not be updated.') } finally { setIsSaving(false) }
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={isSaving ? undefined : onClose}>
      <section className="todo-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="todo-detail-title" onMouseDown={(event) => event.stopPropagation()}>
        <div className="todo-detail-heading"><div><p className="dialog-eyebrow">TASK DETAILS</p><h2 id="todo-detail-title">Make it yours</h2></div><button className="dialog-close" type="button" disabled={isSaving} aria-label="Close task details" onClick={onClose}>×</button></div>
        <form onSubmit={(event) => void submit(event)}>
          <label className="detail-title-field"><span>WHAT NEEDS DOING?</span><input value={title} disabled={isSaving} onChange={(event) => setTitle(event.target.value)} required /></label>
          <ReminderPicker value={notifyAt} onChange={setNotifyAt} />
          <RecurrencePicker value={recurrenceRule} onChange={setRecurrenceRule} />
          {todo.completed && <p className="detail-completed-note">This task is complete, so changing its reminder will not create a new notification.</p>}
          {error && <p className="form-message error" role="alert">{error}</p>}
          <div className="detail-actions"><button className="dialog-cancel" type="button" disabled={isSaving} onClick={() => void toggleCompleted()}>{todo.completed ? 'Reopen task' : 'Mark complete'}</button><button className="dialog-primary" type="submit" disabled={isSaving}>{isSaving ? 'Saving…' : 'Save changes'}</button></div>
        </form>
        <button className="detail-delete-button" type="button" disabled={isSaving} onClick={onDelete}>Delete task</button>
      </section>
    </div>
  )
}
