import { useEffect, useState } from 'react'
import type { AuthenticatedUser } from '../auth/types'
import { MobileBottomNavigation } from '../navigation/MobileBottomNavigation'
import { navigateTo, routes } from '../navigation/routes'
import { DeleteTodoDialog } from './DeleteTodoDialog'
import { TodoDetailDialog } from './TodoDetailDialog'
import { useTodos } from './useTodos'
import type { Todo } from './types'

function formatReminderTime(value: string) {
  return new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}

function recurrenceSummary(todo: Todo) {
  const date = new Date(todo.notifyAt)
  const time = new Intl.DateTimeFormat('en', { hour: 'numeric', minute: '2-digit' }).format(date)
  if (todo.recurrenceRule === 'FREQ=DAILY') return `Every day at ${time}`
  if (todo.recurrenceRule === 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR') return `Every weekday at ${time}`
  if (todo.recurrenceRule === 'FREQ=WEEKLY;BYDAY=SA,SU') return `Every weekend at ${time}`
  return `Every ${new Intl.DateTimeFormat('en', { weekday: 'long' }).format(date)} at ${time}`
}

export function RecurringRemindersPage({ user }: { user: AuthenticatedUser }) {
  const { todos, deleteTodo, skipRecurringOccurrence, stopRecurringReminder, toggleTodo, updateTodo, isLoading, error } = useTodos(user.id)
  const [selectedTodo, setSelectedTodo] = useState<Todo | null>(null)
  const [todoPendingDeletion, setTodoPendingDeletion] = useState<Todo | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const recurringTodos = todos.filter((todo) => !todo.completed && todo.recurrenceRule).sort((a, b) => a.notifyAt.localeCompare(b.notifyAt))
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

  useEffect(() => {
    if (!message) return
    const timeoutId = window.setTimeout(() => setMessage(null), 2800)
    return () => window.clearTimeout(timeoutId)
  }, [message])

  async function skipOccurrence() {
    if (!todoPendingDeletion) return
    try {
      await skipRecurringOccurrence(todoPendingDeletion.id)
      setMessage('This occurrence was skipped. The next one stays scheduled.')
      setTodoPendingDeletion(null)
    } catch (caught) { setMessage(caught instanceof Error ? caught.message : 'This occurrence could not be skipped.') }
  }

  async function stopFutureReminders() {
    if (!todoPendingDeletion) return
    try {
      await stopRecurringReminder(todoPendingDeletion.id)
      setMessage('Future reminders stopped. Completed history stays here.')
      setTodoPendingDeletion(null)
    } catch (caught) { setMessage(caught instanceof Error ? caught.message : 'Future reminders could not be stopped.') }
  }

  return <main className="app-shell app-shell-with-mobile-nav"><section className="settings-page" aria-labelledby="repeating-reminders-title">
    <header className="settings-page-header"><button className="home-link" type="button" onClick={() => navigateTo(routes.settings)}>← Settings</button><span className="eyebrow">Planning</span><h1 id="repeating-reminders-title">Repeating reminders</h1><p>Keep the rhythm, skip just one nudge, or stop a reminder when it no longer fits.</p><small className="settings-intro">Times are shown in {timezone}.</small></header>
    {message && <div className="toast-message" role="status">{message}<button type="button" aria-label="Dismiss message" onClick={() => setMessage(null)}>×</button></div>}
    {isLoading ? <p className="loading-message">Loading repeating reminders…</p> : error ? <p className="form-message error" role="alert">{error}</p> : recurringTodos.length === 0 ? <section className="settings-group"><p className="settings-intro">No repeating reminders yet. Choose a repeat pattern when you add a nudge and it will appear here.</p></section> : <section className="settings-group" aria-label="Repeating reminders list">{recurringTodos.map((todo) => <article className="settings-info-row recurring-reminder-row" key={todo.id}><span className="settings-row-icon" aria-hidden="true">↻</span><div><strong>{todo.title}</strong><small>{recurrenceSummary(todo)} · Next {formatReminderTime(todo.notifyAt)}</small></div><button className="subtle-button" type="button" onClick={() => setSelectedTodo(todo)}>Manage</button></article>)}</section>}
  </section><MobileBottomNavigation />
  {selectedTodo && <TodoDetailDialog todo={selectedTodo} onClose={() => setSelectedTodo(null)} onSave={async (title, notifyAt, recurrenceRule) => { await updateTodo(selectedTodo, title, notifyAt, recurrenceRule); setSelectedTodo(null) }} onToggleCompleted={async () => { await toggleTodo(selectedTodo.id); setSelectedTodo(null) }} onDelete={() => { setTodoPendingDeletion(selectedTodo); setSelectedTodo(null) }} />}
  {todoPendingDeletion && <DeleteTodoDialog todo={todoPendingDeletion} onCancel={() => setTodoPendingDeletion(null)} onConfirm={() => void skipOccurrence()} onStopFutureReminders={() => void stopFutureReminders()} />}
  </main>
}
