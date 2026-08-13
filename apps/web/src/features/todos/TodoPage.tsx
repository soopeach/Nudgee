import { useEffect, useState, type FormEvent } from 'react'
import type { AuthenticatedUser } from '../auth/types'
import { navigateTo, routes } from '../navigation/routes'
import { DeleteTodoDialog } from './DeleteTodoDialog'
import { EmptyTodoState } from './EmptyTodoState'
import { ReminderPicker } from './ReminderPicker'
import { getNextHourInputValue, useTodos } from './useTodos'
import type { Todo } from './types'
import { TaskPeriodFilter, type TaskPeriod } from './TaskPeriodFilter'
import { filterTodosByPeriod } from './taskPeriod'

type TodoPageProps = { user: AuthenticatedUser; onSignOut: () => Promise<void> }

function formatNotifyAt(value: string) {
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', weekday: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}

type TodoListProps = {
  todos: Todo[]
  onToggle: (id: string) => void
  onDelete: (todo: Todo) => void
}

function TodoList({ todos, onToggle, onDelete }: TodoListProps) {
  return <ul className="todo-list">{todos.map((todo) => <li key={todo.id} className={todo.completed ? 'todo completed' : 'todo'}><button className="check-button" aria-label={`${todo.completed ? 'Reopen' : 'Complete'} ${todo.title}`} onClick={() => onToggle(todo.id)} /><div className="todo-copy"><strong>{todo.title}</strong><time dateTime={todo.notifyAt}>Nudge · {formatNotifyAt(todo.notifyAt)}</time></div><button className="delete-button" aria-label={`Delete ${todo.title}`} onClick={() => onDelete(todo)}>×</button></li>)}</ul>
}

export function TodoPage({ user, onSignOut }: TodoPageProps) {
  const { todos, addTodo, toggleTodo, deleteTodo, isLoading, error: todoError } = useTodos(user.id)
  const [title, setTitle] = useState('')
  const [notifyAt, setNotifyAt] = useState(getNextHourInputValue)
  const [message, setMessage] = useState('')
  const [todoPendingDeletion, setTodoPendingDeletion] = useState<Todo | null>(null)
  const [period, setPeriod] = useState<TaskPeriod>('7-days')
  const visibleTodos = filterTodosByPeriod(todos, period)
  const upcomingTodos = visibleTodos.filter((todo) => !todo.completed)
  const pastDueTodos = upcomingTodos.filter((todo) => new Date(todo.notifyAt) < new Date())
  const activeTodos = upcomingTodos.filter((todo) => new Date(todo.notifyAt) >= new Date())
  const completedTodos = visibleTodos.filter((todo) => todo.completed)
  const completedCount = completedTodos.length
  const totalCount = visibleTodos.length
  const progressPercentage = totalCount === 0 ? 0 : Math.round((completedCount / totalCount) * 100)

  useEffect(() => {
    if (!message) return
    const timeoutId = window.setTimeout(() => setMessage(''), 2500)
    return () => window.clearTimeout(timeoutId)
  }, [message])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!title.trim() || !notifyAt) return
    if (new Date(notifyAt) <= new Date()) {
      setMessage('Please choose a future reminder time.')
      return
    }
    try {
      await addTodo(title.trim(), notifyAt)
      setTitle('')
      setNotifyAt(getNextHourInputValue())
      setMessage('Task added!')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Task could not be added.')
    }
  }

  async function confirmDelete() {
    if (!todoPendingDeletion) return
    try {
      await deleteTodo(todoPendingDeletion.id)
      setMessage('Task deleted.')
      setTodoPendingDeletion(null)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Task could not be deleted.')
    }
  }

  return <main className="app-shell"><section className="todo-app" aria-labelledby="app-title">
    <header className="todo-header"><div><span className="eyebrow">Nudgee</span><h1 id="app-title">Your little nudges</h1><p>Pick a time, then let Nudgee remember it for you.</p></div><div className="header-actions"><button className="settings-link" onClick={() => navigateTo(routes.notificationSettings)}>Notifications</button><button className="avatar-button" title={user.displayName ?? user.email ?? 'Signed in user'}>{user.photoURL ? <img src={user.photoURL} alt="" /> : user.displayName?.slice(0, 1) ?? 'U'}</button><button className="sign-out-button" onClick={() => void onSignOut()}>Sign out</button></div></header>
    <div className="progress-card" aria-label={`${completedCount} of ${totalCount} tasks completed, ${progressPercentage}% complete`}><div className="progress-card-top"><strong>{completedCount} of {totalCount}</strong><span>{progressPercentage}%</span></div><div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progressPercentage} aria-label="Task completion progress"><span style={{ width: `${progressPercentage}%` }} /></div><small>tasks completed</small></div>
    <form className="add-todo-form" onSubmit={(event) => void handleSubmit(event)}><label className="title-field"><span>WHAT NEEDS DOING?</span><input value={title} onChange={(event) => { setTitle(event.target.value); setMessage('') }} placeholder="e.g. Send the meeting notes" autoFocus required /></label><ReminderPicker value={notifyAt} onChange={setNotifyAt} /><button className="add-button" type="submit" disabled={isLoading}>Add task <span>→</span></button></form>
    {todoError && <p className="form-message error" role="alert">{todoError}</p>}
    {message && <div className="toast-message" role="status">{message}<button type="button" aria-label="Dismiss message" onClick={() => setMessage('')}>×</button></div>}
    <TaskPeriodFilter value={period} onChange={setPeriod} />
    <section className="todo-section" aria-labelledby="list-title"><div className="section-heading"><h2 id="list-title">Coming up</h2><span>{activeTodos.length} tasks</span></div>{isLoading ? <p className="loading-message">Loading tasks…</p> : activeTodos.length === 0 ? <EmptyTodoState /> : <TodoList todos={activeTodos} onToggle={(id) => void toggleTodo(id)} onDelete={setTodoPendingDeletion} />}</section>
    {pastDueTodos.length > 0 && <section className="todo-section past-due-section" aria-labelledby="past-due-title"><div className="section-heading"><h2 id="past-due-title">Past due</h2><span>{pastDueTodos.length} tasks</span></div><p className="section-caption">These reminders have passed and still need your attention.</p><TodoList todos={pastDueTodos} onToggle={(id) => void toggleTodo(id)} onDelete={setTodoPendingDeletion} /></section>}
    {completedTodos.length > 0 && <section className="todo-section completed-section" aria-labelledby="completed-title"><div className="section-heading"><h2 id="completed-title">Completed</h2><span>{completedTodos.length} tasks</span></div><TodoList todos={completedTodos} onToggle={(id) => void toggleTodo(id)} onDelete={setTodoPendingDeletion} /></section>}
  </section>{todoPendingDeletion && <DeleteTodoDialog todo={todoPendingDeletion} onCancel={() => setTodoPendingDeletion(null)} onConfirm={confirmDelete} />}</main>
}
