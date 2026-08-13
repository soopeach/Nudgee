import { getRelativeDate, joinDateAndTime, splitDateTime } from './reminderDateTime'

type ReminderPickerProps = {
  value: string
  onChange: (value: string) => void
}

export function ReminderPicker({ value, onChange }: ReminderPickerProps) {
  const { date, time } = splitDateTime(value)
  const today = getRelativeDate(0)
  const tomorrow = getRelativeDate(1)

  function setDate(nextDate: string) {
    onChange(joinDateAndTime(nextDate, time))
  }

  function setTime(nextTime: string) {
    onChange(joinDateAndTime(date, nextTime))
  }

  return (
    <fieldset className="reminder-picker">
      <legend>WHEN SHOULD I NUDGE?</legend>
      <div className="quick-date-actions" aria-label="Quick date selection">
        <button className={date === today ? 'selected' : ''} type="button" onClick={() => setDate(today)}>Today</button>
        <button className={date === tomorrow ? 'selected' : ''} type="button" onClick={() => setDate(tomorrow)}>Tomorrow</button>
      </div>
      <div className="reminder-inputs">
        <label><span>Date</span><div className="picker-input-wrap"><span className="picker-icon" aria-hidden="true">▣</span><input type="date" min={today} value={date} onChange={(event) => setDate(event.target.value)} required /></div></label>
        <label><span>Time</span><div className="picker-input-wrap"><span className="picker-icon clock-icon" aria-hidden="true">◷</span><input type="time" value={time} onChange={(event) => setTime(event.target.value)} required /></div></label>
      </div>
    </fieldset>
  )
}
