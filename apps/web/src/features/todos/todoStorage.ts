import type { Todo } from './types'

const storageKey = 'nudgee-local-todos'

export const starterTodos: Todo[] = [
  { id: 'starter-1', title: 'Review tomorrow’s meeting notes', notifyAt: '2026-08-13T09:00', completed: false, completedAt: null },
  { id: 'starter-2', title: 'Pick up milk', notifyAt: '2026-08-12T18:30', completed: true, completedAt: null },
]

export function loadTodos(): Todo[] {
  try {
    const serialized = localStorage.getItem(storageKey)
    return serialized ? JSON.parse(serialized) as Todo[] : starterTodos
  } catch {
    return starterTodos
  }
}

export function saveTodos(todos: Todo[]) {
  localStorage.setItem(storageKey, JSON.stringify(todos))
}
