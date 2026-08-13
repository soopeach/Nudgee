export type TaskPeriod = 'today' | '7-days' | '30-days' | 'all'

type TaskPeriodFilterProps = { value: TaskPeriod; onChange: (period: TaskPeriod) => void }
const options: Array<{ value: TaskPeriod; label: string }> = [
  { value: 'today', label: 'Today' }, { value: '7-days', label: '7-day window' },
  { value: '30-days', label: '30-day window' }, { value: 'all', label: 'All time' },
]

export function TaskPeriodFilter({ value, onChange }: TaskPeriodFilterProps) {
  return <div className="period-filter" aria-label="Task display period">{options.map((option) => <button key={option.value} className={value === option.value ? 'selected' : ''} type="button" onClick={() => onChange(option.value)}>{option.label}</button>)}</div>
}
