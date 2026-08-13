export function toDateInputValue(date: Date) {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return offsetDate.toISOString().slice(0, 10)
}

export function toTimeInputValue(date: Date) {
  return date.toTimeString().slice(0, 5)
}

export function formatLocalDateTime(date: Date) {
  return `${toDateInputValue(date)}T${toTimeInputValue(date)}`
}

export function splitDateTime(value: string) {
  const [date = '', time = ''] = value.split('T')
  return { date, time }
}

export function joinDateAndTime(date: string, time: string) {
  return date && time ? `${date}T${time}` : ''
}

export function getRelativeDate(offsetDays: number) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return toDateInputValue(date)
}
