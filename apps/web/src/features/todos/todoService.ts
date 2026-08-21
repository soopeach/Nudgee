import { supabase } from '../../lib/supabase'
import type { Todo } from './types'

type TaskRow = {
  id: string
  title: string
  notify_at: string
  completed: boolean
  completed_at: string | null
  recurrence_rule: string | null
}

function requireSupabase() {
  if (!supabase) throw new Error('Supabase is not configured. Check your environment variables.')
  return supabase
}

function toTodo(row: TaskRow): Todo {
  return { id: row.id, title: row.title, notifyAt: row.notify_at, completed: row.completed, completedAt: row.completed_at, recurrenceRule: row.recurrence_rule }
}

export async function fetchTodos(userId: string): Promise<Todo[]> {
  const client = requireSupabase()
  const { data, error } = await client.from('tasks').select('id, title, notify_at, completed, completed_at, recurrence_rule').eq('user_id', userId).order('notify_at', { ascending: true })
  if (error) throw error
  return (data as TaskRow[]).map(toTodo)
}

export async function createTodo(userId: string, title: string, notifyAt: string, recurrenceRule: string | null = null, timezone = Intl.DateTimeFormat().resolvedOptions().timeZone): Promise<Todo> {
  const client = requireSupabase()
  const { data, error } = await client.from('tasks').insert({ user_id: userId, title, notify_at: new Date(notifyAt).toISOString(), timezone, recurrence_rule: recurrenceRule, completed: false, completed_at: null, notification_state: 'pending' }).select('id, title, notify_at, completed, completed_at, recurrence_rule').single()
  if (error) throw error
  return toTodo(data as TaskRow)
}

export async function setTodoCompleted(userId: string, id: string, completed: boolean): Promise<Todo> {
  const client = requireSupabase()
  const { data, error } = await client.from('tasks').update({ completed, completed_at: completed ? new Date().toISOString() : null }).eq('id', id).eq('user_id', userId).select('id, title, notify_at, completed, completed_at, recurrence_rule').single()
  if (error) throw error
  return toTodo(data as TaskRow)
}

export async function updateTodo(userId: string, todo: Todo, title: string, notifyAt: string, recurrenceRule: string | null): Promise<Todo> {
  const client = requireSupabase()
  const normalizedNotifyAt = new Date(notifyAt).toISOString()
  const replacesRemainingRecurringSchedule = !todo.completed && (todo.recurrenceRule !== null || recurrenceRule !== null)
  if (replacesRemainingRecurringSchedule) {
    const { data, error } = await client.rpc('replace_recurring_schedule', {
      p_task_id: todo.id,
      p_title: title,
      p_notify_at: normalizedNotifyAt,
      p_timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      p_recurrence_rule: recurrenceRule,
    }).single()
    if (error) throw error
    return toTodo(data as TaskRow)
  }
  const update = {
    title,
    notify_at: normalizedNotifyAt,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    recurrence_rule: recurrenceRule,
  }
  const { data, error } = await client.from('tasks').update(update).eq('id', todo.id).eq('user_id', userId).select('id, title, notify_at, completed, completed_at, recurrence_rule').single()
  if (error) throw error
  return toTodo(data as TaskRow)
}

export async function removeTodo(userId: string, id: string) {
  const client = requireSupabase()
  const { error } = await client.from('tasks').delete().eq('id', id).eq('user_id', userId)
  if (error) throw error
}

export async function stopRecurringReminder(id: string) {
  const client = requireSupabase()
  const { error } = await client.rpc('stop_recurring_reminder', { p_task_id: id })
  if (error) throw error
}

export async function skipRecurringOccurrence(id: string) {
  const client = requireSupabase()
  const { error } = await client.rpc('skip_recurring_occurrence', { p_task_id: id })
  if (error) throw error
}
