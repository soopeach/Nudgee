export const recurrenceOptions = [
  { rule: null, label: 'Does not repeat' },
  { rule: 'FREQ=DAILY', label: 'Every day' },
  { rule: 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR', label: 'Every weekday' },
  { rule: 'FREQ=WEEKLY;BYDAY=SA,SU', label: 'Every weekend' },
  { rule: 'FREQ=WEEKLY', label: 'Every week' },
] as const

export type RecurrenceRule = typeof recurrenceOptions[number]['rule']

export function recurrenceLabel(rule: string | null) {
  return recurrenceOptions.find((option) => option.rule === rule)?.label ?? null
}
