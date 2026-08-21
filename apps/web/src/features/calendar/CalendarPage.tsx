import { useEffect, useMemo, useState } from 'react'
import type { AuthenticatedUser } from '../auth/types'
import { navigateTo, routes } from '../navigation/routes'
import { MobileBottomNavigation } from '../navigation/MobileBottomNavigation'
import { DeleteTodoDialog } from '../todos/DeleteTodoDialog'
import { TodoDetailDialog } from '../todos/TodoDetailDialog'
import { TaskStatusMark } from '../todos/TaskStatusMark'
import { useTodos } from '../todos/useTodos'
import type { Todo } from '../todos/types'

const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

function toDateKey(value: Date) {
  const offset = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return offset.toISOString().slice(0, 10)
}

function dayKeyFromTodo(todo: Todo) {
  return toDateKey(new Date(todo.notifyAt))
}

function monthDays(month: Date) {
  const year = month.getFullYear()
  const index = month.getMonth()
  const firstWeekday = new Date(year, index, 1).getDay()
  const daysInMonth = new Date(year, index + 1, 0).getDate()
  return Array.from({ length: firstWeekday + daysInMonth }, (_, position) => position < firstWeekday ? null : new Date(year, index, position - firstWeekday + 1))
}

function formatDayHeading(value: Date) {
  return new Intl.DateTimeFormat('en', { weekday: 'long', month: 'long', day: 'numeric' }).format(value)
}

type CalendarPageProps = { user: AuthenticatedUser }

export function CalendarPage({ user }: CalendarPageProps) {
  const { todos, isLoading, error, toggleTodo, deleteTodo, skipRecurringOccurrence, stopRecurringReminder, updateTodo } = useTodos(user.id)
  const [visibleMonth, setVisibleMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const [selectedDateKey, setSelectedDateKey] = useState(() => toDateKey(new Date()))
  const [selectedTodo, setSelectedTodo] = useState<Todo | null>(null)
  const [todoPendingDeletion, setTodoPendingDeletion] = useState<Todo | null>(null)
  const [message, setMessage] = useState('')
  const todayKey = toDateKey(new Date())
  const calendarDays = useMemo(() => monthDays(visibleMonth), [visibleMonth])
  const selectedDate = new Date(`${selectedDateKey}T00:00:00`)
  const selectedTodos = todos.filter((todo) => dayKeyFromTodo(todo) === selectedDateKey)
  const tasksByDate = useMemo(() => todos.reduce<Record<string, Todo[]>>((grouped, todo) => {
    const key = dayKeyFromTodo(todo)
    grouped[key] = [...(grouped[key] ?? []), todo]
    return grouped
  }, {}), [todos])

  useEffect(() => {
    if (!message) return
    const timeoutId = window.setTimeout(() => setMessage(''), 2800)
    return () => window.clearTimeout(timeoutId)
  }, [message])

  function moveMonth(offset: number) {
    const nextMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + offset, 1)
    setVisibleMonth(nextMonth)
    setSelectedDateKey(toDateKey(nextMonth))
  }

  function requestDelete(todo: Todo) {
    setSelectedTodo(null)
    setTodoPendingDeletion(todo)
  }

  async function confirmDelete() {
    if (!todoPendingDeletion) return
    try {
      if (todoPendingDeletion.recurrenceRule) {
        await skipRecurringOccurrence(todoPendingDeletion.id)
        setMessage('This occurrence was skipped. The next one stays scheduled.')
      } else {
        await deleteTodo(todoPendingDeletion.id)
        setMessage('Task removed.')
      }
      setTodoPendingDeletion(null)
    } catch (caught) {
      setMessage(caught instanceof Error ? caught.message : 'Task could not be deleted.')
    }
  }

  async function confirmStopFutureReminders() {
    if (!todoPendingDeletion) return
    try {
      await stopRecurringReminder(todoPendingDeletion.id)
      setMessage('Future repeating reminders stopped.')
      setTodoPendingDeletion(null)
    } catch (caught) {
      setMessage(caught instanceof Error ? caught.message : 'Future reminders could not be stopped.')
    }
  }

  return (
    <main className="app-shell app-shell-with-mobile-nav">
      <section className="calendar-page" aria-labelledby="calendar-title">
        <header className="calendar-page-header"><div><span className="eyebrow">Nudgee</span><h1 id="calendar-title">Your rhythm</h1><p>See what you’ve finished and what still deserves a nudge.</p></div><div className="calendar-header-actions"><button type="button" onClick={() => navigateTo(routes.home)}>Home</button><button type="button" onClick={() => navigateTo(routes.settings)}>Settings</button></div></header>

        <section className="calendar-surface" aria-label="Task calendar">
          <div className="calendar-month-control"><button type="button" aria-label="Previous month" onClick={() => moveMonth(-1)}>‹</button><h2>{new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' }).format(visibleMonth)}</h2><button type="button" aria-label="Next month" onClick={() => moveMonth(1)}>›</button></div>
          <div className="calendar-weekdays">{weekdays.map((day) => <span key={day}>{day}</span>)}</div>
          <div className="calendar-grid">{calendarDays.map((day, index) => {
            if (!day) return <span className="calendar-empty-day" key={`empty-${index}`} aria-hidden="true" />
            const key = toDateKey(day)
            const dayTodos = tasksByDate[key] ?? []
            const completedCount = dayTodos.filter((todo) => todo.completed).length
            return <button key={key} className={`calendar-day${key === selectedDateKey ? ' selected' : ''}${key === todayKey ? ' today' : ''}`} type="button" aria-label={`${key}, ${dayTodos.length} tasks`} onClick={() => setSelectedDateKey(key)}><span>{day.getDate()}</span>{dayTodos.length > 0 && <small><i className="calendar-upcoming-dot" />{completedCount > 0 && <i className="calendar-completed-dot" />}</small>}</button>
          })}</div>
        </section>

        <section className="calendar-day-tasks" aria-labelledby="calendar-day-title"><div className="calendar-day-heading"><div><span className="eyebrow">Selected day</span><h2 id="calendar-day-title">{formatDayHeading(selectedDate)}</h2></div><span>{selectedTodos.length} {selectedTodos.length === 1 ? 'task' : 'tasks'}</span></div>{isLoading ? <p className="calendar-empty-copy">Loading your rhythm…</p> : selectedTodos.length === 0 ? <p className="calendar-empty-copy">Nothing scheduled here yet. A calm little pocket of time.</p> : <ul className="calendar-task-list">{selectedTodos.map((todo) => <li className={todo.completed ? 'calendar-task-row completed' : 'calendar-task-row'} key={todo.id}><button className="calendar-check-button" type="button" aria-label={`${todo.completed ? 'Reopen' : 'Complete'} ${todo.title}`} onClick={() => void toggleTodo(todo.id)}><TaskStatusMark completed={todo.completed} /></button><button className="calendar-task-card" type="button" onClick={() => setSelectedTodo(todo)}><span><strong>{todo.title}</strong><small>{new Intl.DateTimeFormat('en', { hour: 'numeric', minute: '2-digit' }).format(new Date(todo.notifyAt))}{todo.completed ? ' · Completed' : ' · Scheduled'}</small></span></button><button className="calendar-delete-button" type="button" aria-label={`Delete ${todo.title}`} onClick={() => requestDelete(todo)}>×</button></li>)}</ul>}</section>
        {error && <p className="form-message error" role="alert">{error}</p>}
        {message && <div className="toast-message" role="status">{message}<button type="button" aria-label="Dismiss message" onClick={() => setMessage('')}>×</button></div>}
      </section>
      <MobileBottomNavigation />
      {selectedTodo && <TodoDetailDialog todo={selectedTodo} onClose={() => setSelectedTodo(null)} onSave={async (title, notifyAt, recurrenceRule) => { await updateTodo(selectedTodo, title, notifyAt, recurrenceRule) }} onToggleCompleted={async () => { await toggleTodo(selectedTodo.id); setSelectedTodo(null) }} onDelete={() => requestDelete(selectedTodo)} />}
      {todoPendingDeletion && <DeleteTodoDialog todo={todoPendingDeletion} onCancel={() => setTodoPendingDeletion(null)} onConfirm={() => void confirmDelete()} onStopFutureReminders={todoPendingDeletion.recurrenceRule ? () => void confirmStopFutureReminders() : undefined} />}
    </main>
  )
}
