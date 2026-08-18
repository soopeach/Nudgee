type TaskStatusMarkProps = {
  completed: boolean
  className?: string
}

/** Matches the shared Compose client's four-petal completion control. */
export function TaskStatusMark({ completed, className }: TaskStatusMarkProps) {
  const petalColor = completed ? '#9fa1ff' : '#aee2ff'

  return (
    <svg className={className ? `task-status-mark ${className}` : 'task-status-mark'} viewBox="0 0 30 30" aria-hidden="true">
      <circle cx="15" cy="9.3" r="6.6" fill={petalColor} />
      <circle cx="20.7" cy="15" r="6.6" fill={petalColor} />
      <circle cx="15" cy="20.7" r="6.6" fill={petalColor} />
      <circle cx="9.3" cy="15" r="6.6" fill={petalColor} />
      <circle cx="15" cy="15" r="7.2" fill={completed ? '#9fa1ff' : '#ffffff'} stroke={completed ? 'none' : '#aee2ff'} strokeWidth="2" />
      {completed && <path d="M10.5 15.3 13.8 18.3 20 11.9" fill="none" stroke="#ffffff" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.25" />}
    </svg>
  )
}
