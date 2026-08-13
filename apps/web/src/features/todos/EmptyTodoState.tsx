export function EmptyTodoState() {
  return (
    <div className="empty-todo-state">
      <div className="empty-todo-illustration" aria-hidden="true"><span>✦</span></div>
      <h3>Your list is all clear</h3>
      <p>Add a task above, pick a reminder time, and Nudgee will take it from there.</p>
    </div>
  )
}
