import { getRelativeDate, joinDateAndTime, splitDateTime } from './reminderDateTime'
import type { Ref } from 'react'

type ReminderPickerProps = {
  value: string
  onChange: (value: string) => void
  timeInputRef?: Ref<HTMLInputElement>
  attention?: boolean
}

export function ReminderPicker({ value, onChange, timeInputRef, attention = false }: ReminderPickerProps) {
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
    <fieldset className={`reminder-picker${attention ? ' needs-attention' : ''}`}>
      <legend>WHEN SHOULD I NUDGE?</legend>
      <div className="quick-date-actions" aria-label="Quick date selection">
        <button className={date === today ? 'selected' : ''} type="button" onClick={() => setDate(today)}>Today</button>
        <button className={date === tomorrow ? 'selected' : ''} type="button" onClick={() => setDate(tomorrow)}>Tomorrow</button>
      </div>
      <div className="reminder-inputs">
        <label><span>Date</span><div className="picker-input-wrap"><input type="date" min={today} value={date} onChange={(event) => setDate(event.target.value)} required /></div></label>
        <label><span>Time</span><div className="picker-input-wrap"><input ref={timeInputRef} type="time" value={time} onChange={(event) => setTime(event.target.value)} required /></div></label>
      </div>
    </fieldset>
  )
}
