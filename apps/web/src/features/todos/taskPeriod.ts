import type { TaskPeriod } from './TaskPeriodFilter'
import type { Todo } from './types'

export function filterTodosByPeriod(todos: Todo[], period: TaskPeriod) {
  if (period === 'all') return todos
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date(start)
  const windowDays = period === 'today' ? 0 : period === '7-days' ? 7 : 30
  start.setDate(start.getDate() - windowDays)
  end.setDate(end.getDate() + (period === 'today' ? 1 : windowDays))
  return todos.filter((todo) => {
    const notifyAt = new Date(todo.notifyAt)
    return notifyAt >= start && notifyAt < end
  })
}
