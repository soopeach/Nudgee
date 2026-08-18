import { useCallback, useEffect, useState } from 'react'
import { supabase } from '../../lib/supabase'
import { formatLocalDateTime } from './reminderDateTime'
import { createTodo, fetchTodos, removeTodo, setTodoCompleted, updateTodo as persistTodoUpdate } from './todoService'
import type { Todo } from './types'
import type { RealtimePostgresChangesPayload } from '@supabase/supabase-js'

type TaskRealtimeRow = { id: string; title: string; notify_at: string; completed: boolean; completed_at: string | null }

function mapRealtimeTodo(payload: RealtimePostgresChangesPayload<TaskRealtimeRow>): Todo | null {
  const row = payload.eventType === 'DELETE' ? payload.old : payload.new
  if (!row || typeof row.id !== 'string') return null
  if (payload.eventType === 'DELETE') return { id: row.id, title: '', notifyAt: '', completed: false, completedAt: null }
  if (typeof row.title !== 'string' || typeof row.notify_at !== 'string' || typeof row.completed !== 'boolean') return null
  return { id: row.id, title: row.title, notifyAt: row.notify_at, completed: row.completed, completedAt: row.completed_at ?? null }
}

function upsertTodo(current: Todo[], incoming: Todo) {
  const existingIndex = current.findIndex((todo) => todo.id === incoming.id)
  if (existingIndex === -1) return [incoming, ...current]
  return current.map((todo) => todo.id === incoming.id ? incoming : todo)
}

export function getNextHourInputValue() {
  const date = new Date(Date.now() + 60 * 60 * 1000)
  date.setMinutes(0, 0, 0)
  return formatLocalDateTime(date)
}

export function useTodos(userId: string) {
  const [todos, setTodos] = useState<Todo[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setIsLoading(true)
    try {
      setTodos(await fetchTodos(userId))
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Tasks could not be loaded.')
    } finally {
      setIsLoading(false)
    }
  }, [userId])

  useEffect(() => {
    let cancelled = false
    void fetchTodos(userId).then((items) => {
      if (!cancelled) { setTodos(items); setError(null) }
    }).catch((caught: unknown) => {
      if (!cancelled) setError(caught instanceof Error ? caught.message : 'Tasks could not be loaded.')
    }).finally(() => {
      if (!cancelled) setIsLoading(false)
    })
    return () => { cancelled = true }
  }, [userId])

  useEffect(() => {
    const client = supabase
    if (!client) return
    const channel = client.channel(`tasks:${userId}`).on('postgres_changes', { event: '*', schema: 'public', table: 'tasks', filter: `user_id=eq.${userId}` }, (payload) => {
      const todo = mapRealtimeTodo(payload as RealtimePostgresChangesPayload<TaskRealtimeRow>)
      if (!todo) return
      if (payload.eventType === 'DELETE') {
        setTodos((current) => current.filter((item) => item.id !== todo.id))
      } else {
        setTodos((current) => upsertTodo(current, todo))
      }
    }).subscribe((status) => {
      if (status === 'SUBSCRIBED') setError(null)
      if (status === 'CHANNEL_ERROR') setError('Realtime sync could not connect. In Supabase, enable Realtime for the public.tasks table and check its RLS policies.')
      if (status === 'TIMED_OUT') setError('Realtime sync timed out. Check your Supabase project connection and try again.')
    })
    return () => { void client.removeChannel(channel) }
  }, [userId])

  const addTodo = useCallback(async (title: string, notifyAt: string) => {
    const created = await createTodo(userId, title, notifyAt, Intl.DateTimeFormat().resolvedOptions().timeZone)
    // The INSERT Realtime event can arrive before or after this response.
    // Merge by the server-generated UUID so the task is rendered once.
    setTodos((current) => upsertTodo(current, created))
    return created
  }, [userId])

  const toggleTodo = useCallback(async (id: string) => {
    const current = todos.find((todo) => todo.id === id)
    if (!current) return
    try {
      const updated = await setTodoCompleted(userId, id, !current.completed)
      setTodos((items) => items.map((todo) => todo.id === id ? updated : todo))
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Task could not be updated.')
    }
  }, [todos, userId])

  const deleteTodo = useCallback(async (id: string) => {
    await removeTodo(userId, id)
    setTodos((current) => current.filter((todo) => todo.id !== id))
  }, [userId])

  const updateTodo = useCallback(async (todo: Todo, title: string, notifyAt: string) => {
    const updated = await persistTodoUpdate(userId, todo, title, notifyAt)
    setTodos((items) => items.map((item) => item.id === todo.id ? updated : item))
    return updated
  }, [userId])

  return { todos, addTodo, toggleTodo, deleteTodo, updateTodo, isLoading, error, refresh }
}
