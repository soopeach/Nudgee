import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { AuthenticatedUser } from '../auth/types'
import { navigateTo, routes } from '../navigation/routes'
import { MobileBottomNavigation } from '../navigation/MobileBottomNavigation'
import { DeleteTodoDialog } from './DeleteTodoDialog'
import { EmptyTodoState } from './EmptyTodoState'
import { NaturalLanguageReminderDialog, ParsingReminderDialog } from './NaturalLanguageReminderDialog'
import { TodoDetailDialog } from './TodoDetailDialog'
import { TaskStatusMark } from './TaskStatusMark'
import { ReminderPicker } from './ReminderPicker'
import { RecurrencePicker } from './RecurrencePicker'
import { recurrenceLabel, type RecurrenceRule } from './recurrence'
import { getNextHourInputValue, useTodos } from './useTodos'
import { getReminderParseUsage, parseNaturalLanguageReminder, type ParsedReminder, type ReminderParseUsage } from './naturalLanguageService'
import { formatLocalDateTime } from './reminderDateTime'
import type { Todo } from './types'
import { TaskPeriodFilter, type TaskPeriod } from './TaskPeriodFilter'
import { filterTodosByPeriod } from './taskPeriod'

type TodoPageProps = { user: AuthenticatedUser; onSignOut: () => Promise<void> }
type ManualFocusTarget = 'time' | 'recurrence' | null

function formatNotifyAt(value: string) {
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', weekday: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}

type TodoListProps = {
  todos: Todo[]
  onToggle: (id: string) => void
  onDelete: (todo: Todo) => void
  onSelect: (todo: Todo) => void
}

function TodoList({ todos, onToggle, onDelete, onSelect }: TodoListProps) {
  return <ul className="todo-list">{todos.map((todo) => <li key={todo.id} className={todo.completed ? 'todo completed' : 'todo'}><button className="check-button" aria-label={`${todo.completed ? 'Reopen' : 'Complete'} ${todo.title}`} onClick={() => onToggle(todo.id)}><TaskStatusMark completed={todo.completed} /></button><button className="todo-select-button" type="button" onClick={() => onSelect(todo)}><span className="todo-copy"><strong>{todo.title}</strong><time dateTime={todo.notifyAt}>Nudge · {formatNotifyAt(todo.notifyAt)}{recurrenceLabel(todo.recurrenceRule) ? ` · ${recurrenceLabel(todo.recurrenceRule)}` : ''}</time></span></button><button className="delete-button" aria-label={`Delete ${todo.title}`} onClick={() => onDelete(todo)}>×</button></li>)}</ul>
}

export function TodoPage({ user, onSignOut }: TodoPageProps) {
  const { todos, addTodo, toggleTodo, deleteTodo, skipRecurringOccurrence, stopRecurringReminder, updateTodo, isLoading, error: todoError } = useTodos(user.id)
  const [title, setTitle] = useState('')
  const [notifyAt, setNotifyAt] = useState(getNextHourInputValue)
  const [recurrenceRule, setRecurrenceRule] = useState<RecurrenceRule>(null)
  const [naturalLanguage, setNaturalLanguage] = useState('')
  const [isParsing, setIsParsing] = useState(false)
  const [isSchedulingParsedReminder, setIsSchedulingParsedReminder] = useState(false)
  const [parsedReminder, setParsedReminder] = useState<ParsedReminder | null>(null)
  const [parsedPrompt, setParsedPrompt] = useState('')
  const [parseUsage, setParseUsage] = useState<ReminderParseUsage | null>(null)
  const [isUsageLoading, setIsUsageLoading] = useState(true)
  const [showManualEntry, setShowManualEntry] = useState(false)
  const [manualFocusTarget, setManualFocusTarget] = useState<ManualFocusTarget>(null)
  const [message, setMessage] = useState('')
  const [todoPendingDeletion, setTodoPendingDeletion] = useState<Todo | null>(null)
  const [selectedTodo, setSelectedTodo] = useState<Todo | null>(null)
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false)
  const [period, setPeriod] = useState<TaskPeriod>('7-days')
  const visibleTodos = filterTodosByPeriod(todos, period)
  const upcomingTodos = visibleTodos.filter((todo) => !todo.completed)
  const pastDueTodos = upcomingTodos.filter((todo) => new Date(todo.notifyAt) < new Date())
  const activeTodos = upcomingTodos.filter((todo) => new Date(todo.notifyAt) >= new Date())
  const completedTodos = visibleTodos.filter((todo) => todo.completed)
  const completedCount = completedTodos.length
  const totalCount = visibleTodos.length
  const progressPercentage = totalCount === 0 ? 0 : Math.round((completedCount / totalCount) * 100)
  const availableParses = (parseUsage?.remainingFreeParses ?? 0) + (parseUsage?.bonusCredits ?? 0)
  const hasAiAllowance = parseUsage !== null && availableParses > 0
  const timeInputRef = useRef<HTMLInputElement>(null)
  const recurrenceSelectRef = useRef<HTMLSelectElement>(null)

  useEffect(() => {
    if (!message) return
    const timeoutId = window.setTimeout(() => setMessage(''), 2500)
    return () => window.clearTimeout(timeoutId)
  }, [message])

  async function refreshParseUsage() {
    setIsUsageLoading(true)
    try { setParseUsage(await getReminderParseUsage()) } catch { setParseUsage(null) } finally { setIsUsageLoading(false) }
  }

  useEffect(() => { void refreshParseUsage() }, [])

  useEffect(() => {
    if (!showManualEntry || !manualFocusTarget) return
    const frame = window.requestAnimationFrame(() => {
      ;(manualFocusTarget === 'time' ? timeInputRef.current : recurrenceSelectRef.current)?.focus()
    })
    return () => window.cancelAnimationFrame(frame)
  }, [manualFocusTarget, showManualEntry])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!title.trim() || !notifyAt) return
    if (new Date(notifyAt) <= new Date()) {
      setMessage('Please choose a future reminder time.')
      return
    }
    try {
      await addTodo(title.trim(), notifyAt, recurrenceRule)
      setTitle('')
      setNotifyAt(getNextHourInputValue())
      setRecurrenceRule(null)
      setMessage('Task added!')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Task could not be added.')
    }
  }

  async function handleNaturalLanguageSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!naturalLanguage.trim() || isParsing) return
    setIsParsing(true)
    setMessage('')
    try {
      const prompt = naturalLanguage.trim()
      const parsed = await parseNaturalLanguageReminder(prompt)
      setParsedPrompt(prompt)
      setParsedReminder(parsed)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Task could not be added.')
    } finally {
      setIsParsing(false)
      void refreshParseUsage()
    }
  }

  function closeParsedReminderDialog() {
    setParsedReminder(null)
    setParsedPrompt('')
  }

  function editParsedReminderDetails() {
    if (!parsedReminder) return
    setTitle(parsedReminder.title)
    if (parsedReminder.notifyAt) setNotifyAt(formatLocalDateTime(new Date(parsedReminder.notifyAt)))
    setRecurrenceRule(parsedReminder.recurrenceRule as RecurrenceRule)
    setManualFocusTarget(parsedReminder.clarificationType === 'recurrence' ? 'recurrence' : parsedReminder.clarificationType === 'time' ? 'time' : null)
    setShowManualEntry(true)
    closeParsedReminderDialog()
  }

  async function scheduleParsedReminder() {
    if (!parsedReminder?.notifyAt) return
    setIsSchedulingParsedReminder(true)
    try {
      await addTodo(parsedReminder.title, parsedReminder.notifyAt, parsedReminder.recurrenceRule)
      setNaturalLanguage('')
      setTitle('')
      setNotifyAt(getNextHourInputValue())
      closeParsedReminderDialog()
      setMessage('Task scheduled!')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Task could not be scheduled.')
    } finally {
      setIsSchedulingParsedReminder(false)
    }
  }

  async function confirmDelete() {
    if (!todoPendingDeletion) return
    try {
      if (todoPendingDeletion.recurrenceRule) {
        await skipRecurringOccurrence(todoPendingDeletion.id)
        setMessage('This reminder was skipped. The next one is still scheduled.')
      } else {
        await deleteTodo(todoPendingDeletion.id)
        setMessage('Task deleted.')
      }
      setTodoPendingDeletion(null)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Task could not be deleted.')
    }
  }

  async function confirmStopFutureReminders() {
    if (!todoPendingDeletion) return
    try {
      await stopRecurringReminder(todoPendingDeletion.id)
      setMessage('Future reminders stopped.')
      setTodoPendingDeletion(null)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Future reminders could not be stopped.')
    }
  }

  async function handleToggle(id: string) {
    const todo = todos.find((item) => item.id === id)
    const didUpdate = await toggleTodo(id)
    if (!didUpdate) {
      setMessage('Task could not be updated.')
      return false
    }
    if (todo && !todo.completed && todo.recurrenceRule) {
      setMessage('Completed. Nudgee keeps the next reminder scheduled.')
    }
    return true
  }

  function requestDelete(todo: Todo) {
    setSelectedTodo(null)
    setTodoPendingDeletion(todo)
  }

  return <main className="app-shell app-shell-with-mobile-nav"><section className="todo-app" aria-labelledby="app-title">
    <header className="todo-header"><div><span className="eyebrow">Nudgee</span><h1 id="app-title">Your little nudges</h1><p>Pick a time, then let Nudgee remember it for you.</p></div><div className="header-actions"><div className="profile-menu"><button className="avatar-button" type="button" aria-label="Open account menu" aria-expanded={isProfileMenuOpen} onClick={() => setIsProfileMenuOpen((isOpen) => !isOpen)}>{user.photoURL ? <img src={user.photoURL} alt="" /> : user.displayName?.slice(0, 1) ?? 'U'}</button>{isProfileMenuOpen && <div className="profile-menu-popover"><div className="profile-menu-identity"><strong>{user.displayName}</strong><small>{user.email}</small></div><button type="button" onClick={() => navigateTo(routes.settings)}>Account settings</button><button className="profile-menu-sign-out" type="button" onClick={() => void onSignOut()}>Sign out</button></div>}</div><button className="settings-link" onClick={() => navigateTo(routes.calendar)}>Calendar</button><button className="settings-link" onClick={() => navigateTo(routes.settings)}>Settings</button></div></header>
    <div className="progress-card" aria-label={`${completedCount} of ${totalCount} tasks completed, ${progressPercentage}% complete`}><div className="progress-card-top"><strong>{completedCount} of {totalCount}</strong><span>{progressPercentage}%</span></div><div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progressPercentage} aria-label="Task completion progress"><span style={{ width: `${progressPercentage}%` }} /></div><small>tasks completed</small></div>
    <section className={`natural-reminder-card${isParsing ? ' is-parsing' : ''}`} aria-labelledby="natural-reminder-title" aria-busy={isParsing}><div className="natural-reminder-heading"><span aria-hidden="true">✦</span><div><h2 id="natural-reminder-title">Tell Nudgee naturally</h2><p>Try “Remind me to call Mum tomorrow at 9am”.</p></div></div>{parseUsage && <div className="ai-usage-card"><div><strong>{parseUsage.remainingFreeParses} of {parseUsage.dailyFreeParseLimit} free left today</strong><small>{parseUsage.bonusCredits > 0 ? `${parseUsage.bonusCredits} reward credits ready · resets at local midnight` : 'Resets at local midnight'}</small></div><b>{availableParses} left</b></div>}<form className="natural-reminder-form" onSubmit={(event) => void handleNaturalLanguageSubmit(event)}><label className="sr-only" htmlFor="natural-reminder">Describe your reminder</label><input id="natural-reminder" value={naturalLanguage} onChange={(event) => { setNaturalLanguage(event.target.value); setMessage('') }} placeholder={isUsageLoading ? 'Checking AI reminder allowance…' : hasAiAllowance ? 'What do you need a nudge for?' : 'AI reminders reset at local midnight'} autoFocus required disabled={isParsing || isUsageLoading || !hasAiAllowance} /><button className="add-button" type="submit" disabled={isLoading || isParsing || isUsageLoading || !hasAiAllowance}>{isParsing ? 'Understanding…' : isUsageLoading ? 'Checking allowance…' : hasAiAllowance ? <>Add with Nudgee <span>→</span></> : 'No AI reminders left today'}</button></form>{isParsing && <div className="natural-reminder-loading" role="status" aria-live="polite"><span className="loading-spinner" aria-hidden="true" />Understanding your reminder…</div>}</section>
    <details className="manual-reminder" open={showManualEntry} onToggle={(event) => setShowManualEntry(event.currentTarget.open)}><summary>Set the task, date and time yourself</summary><form className="add-todo-form" onSubmit={(event) => void handleSubmit(event)}><label className="title-field"><span>WHAT NEEDS DOING?</span><input value={title} onChange={(event) => { setTitle(event.target.value); setMessage('') }} placeholder="e.g. Send the meeting notes" required /></label><ReminderPicker value={notifyAt} onChange={(value) => { setNotifyAt(value); setManualFocusTarget(null) }} timeInputRef={timeInputRef} attention={manualFocusTarget === 'time'} /><RecurrencePicker value={recurrenceRule} onChange={(value) => { setRecurrenceRule(value); setManualFocusTarget(null) }} selectRef={recurrenceSelectRef} attention={manualFocusTarget === 'recurrence'} /><button className="add-button" type="submit" disabled={isLoading}>Add task <span>→</span></button></form></details>
    {todoError && <p className="form-message error" role="alert">{todoError}</p>}
    {message && <div className="toast-message" role="status">{message}<button type="button" aria-label="Dismiss message" onClick={() => setMessage('')}>×</button></div>}
    <TaskPeriodFilter value={period} onChange={setPeriod} />
    <section className="todo-section" aria-labelledby="list-title"><div className="section-heading"><h2 id="list-title">Coming up</h2><span>{activeTodos.length} tasks</span></div>{isLoading ? <p className="loading-message">Loading tasks…</p> : activeTodos.length === 0 ? <EmptyTodoState /> : <TodoList todos={activeTodos} onToggle={(id) => void handleToggle(id)} onDelete={requestDelete} onSelect={setSelectedTodo} />}</section>
    {pastDueTodos.length > 0 && <section className="todo-section past-due-section" aria-labelledby="past-due-title"><div className="section-heading"><h2 id="past-due-title">Past due</h2><span>{pastDueTodos.length} tasks</span></div><p className="section-caption">These reminders have passed and still need your attention.</p><TodoList todos={pastDueTodos} onToggle={(id) => void handleToggle(id)} onDelete={requestDelete} onSelect={setSelectedTodo} /></section>}
    {completedTodos.length > 0 && <section className="todo-section completed-section" aria-labelledby="completed-title"><div className="section-heading"><h2 id="completed-title">Completed</h2><span>{completedTodos.length} tasks</span></div><TodoList todos={completedTodos} onToggle={(id) => void handleToggle(id)} onDelete={requestDelete} onSelect={setSelectedTodo} /></section>}
  </section><MobileBottomNavigation />{isParsing && <ParsingReminderDialog prompt={naturalLanguage.trim()} />}{parsedReminder && <NaturalLanguageReminderDialog parsedReminder={parsedReminder} prompt={parsedPrompt} isSaving={isSchedulingParsedReminder} onClose={closeParsedReminderDialog} onEditDetails={editParsedReminderDetails} onSchedule={() => void scheduleParsedReminder()} />}{selectedTodo && <TodoDetailDialog todo={selectedTodo} onClose={() => setSelectedTodo(null)} onSave={async (nextTitle, nextNotifyAt, nextRecurrenceRule) => { await updateTodo(selectedTodo, nextTitle, nextNotifyAt, nextRecurrenceRule); setMessage('Nudge updated.') }} onToggleCompleted={async () => { const didUpdate = await handleToggle(selectedTodo.id); if (didUpdate) setSelectedTodo(null) }} onDelete={() => requestDelete(selectedTodo)} />}{todoPendingDeletion && <DeleteTodoDialog todo={todoPendingDeletion} onCancel={() => setTodoPendingDeletion(null)} onConfirm={confirmDelete} onStopFutureReminders={todoPendingDeletion.recurrenceRule ? () => void confirmStopFutureReminders() : undefined} />}</main>
}
