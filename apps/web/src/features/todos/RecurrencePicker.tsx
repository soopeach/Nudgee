import { recurrenceOptions, type RecurrenceRule } from './recurrence'
import type { Ref } from 'react'

type RecurrencePickerProps = {
  value: RecurrenceRule
  onChange: (value: RecurrenceRule) => void
  selectRef?: Ref<HTMLSelectElement>
  attention?: boolean
}

export function RecurrencePicker({ value, onChange, selectRef, attention = false }: RecurrencePickerProps) {
  const helperText = value
    ? 'After this reminder is delivered, Nudgee automatically creates and schedules the next task — even if you do not complete this one.'
    : 'This is a one-time nudge.'

  return <label className={`recurrence-field${attention ? ' needs-attention' : ''}`}><span>DOES IT REPEAT?</span><select ref={selectRef} value={value ?? ''} onChange={(event) => onChange((event.target.value || null) as RecurrenceRule)}>{recurrenceOptions.map((option) => <option key={option.label} value={option.rule ?? ''}>{option.label}</option>)}</select><small>{helperText}</small></label>
}
