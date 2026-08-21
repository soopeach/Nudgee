export const SUPPORTED_RECURRENCE_RULES = new Set([
  'FREQ=DAILY',
  'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR',
  'FREQ=WEEKLY;BYDAY=SA,SU',
  'FREQ=WEEKLY',
])

export function explicitRelativeOffsetMs(text) {
  const patterns = [
    { regex: /(\d+)\s*분\s*(?:뒤|후)(?:에)?/, unitMs: 60_000 },
    { regex: /(\d+)\s*시간\s*(?:뒤|후)(?:에)?/, unitMs: 60 * 60_000 },
    { regex: /\bin\s+(\d+)\s*(?:minute|minutes)\b/i, unitMs: 60_000 },
    { regex: /\b(\d+)\s*(?:minute|minutes)\s*(?:later|from now)\b/i, unitMs: 60_000 },
    { regex: /\bin\s+(\d+)\s*(?:hour|hours)\b/i, unitMs: 60 * 60_000 },
    { regex: /\b(\d+)\s*(?:hour|hours)\s*(?:later|from now)\b/i, unitMs: 60 * 60_000 },
  ]

  for (const { regex, unitMs } of patterns) {
    const match = text.match(regex)
    const amount = Number(match?.[1])
    if (Number.isSafeInteger(amount) && amount > 0 && amount <= 365 * 24) return amount * unitMs
  }
  return null
}

/** Resolves only an explicit relative clock; recurrence clarification survives. */
export function applyServerRelativeTime(result, text, nowIso) {
  const offsetMs = explicitRelativeOffsetMs(text)
  if (offsetMs == null) return { result, usedServerRelativeTime: false }
  return {
    result: {
      ...result,
      notifyAt: new Date(Date.parse(nowIso) + offsetMs).toISOString(),
      needsClarification: result.clarificationType === 'recurrence'
        ? result.needsClarification
        : false,
    },
    usedServerRelativeTime: true,
  }
}

function zonedParts(instant, timezone) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(instant)
  const values = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, Number(part.value)]))
  return { year: values.year, month: values.month, day: values.day, hour: values.hour, minute: values.minute, second: values.second }
}

function timezoneOffsetMs(instant, timezone) {
  const local = zonedParts(instant, timezone)
  return Date.UTC(local.year, local.month - 1, local.day, local.hour, local.minute, local.second) - instant.getTime()
}

function zonedPartsToInstant(local, timezone) {
  const localAsUtc = Date.UTC(local.year, local.month - 1, local.day, local.hour, local.minute, local.second)
  let guess = localAsUtc
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const adjusted = localAsUtc - timezoneOffsetMs(new Date(guess), timezone)
    if (adjusted === guess) return new Date(adjusted)
    guess = adjusted
  }
  return new Date(guess)
}

function addLocalDays(local, days) {
  const value = new Date(Date.UTC(local.year, local.month - 1, local.day + days, local.hour, local.minute, local.second))
  return { year: value.getUTCFullYear(), month: value.getUTCMonth() + 1, day: value.getUTCDate(), hour: value.getUTCHours(), minute: value.getUTCMinutes(), second: value.getUTCSeconds() }
}

function localIsoDay(local) {
  const day = new Date(Date.UTC(local.year, local.month - 1, local.day)).getUTCDay()
  return day === 0 ? 7 : day
}

function advanceLocalRecurrence(local, rule) {
  if (rule === 'FREQ=DAILY') return addLocalDays(local, 1)
  if (rule === 'FREQ=WEEKLY') return addLocalDays(local, 7)

  let candidate = addLocalDays(local, 1)
  if (rule === 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR') {
    while (localIsoDay(candidate) > 5) candidate = addLocalDays(candidate, 1)
    return candidate
  }
  if (rule === 'FREQ=WEEKLY;BYDAY=SA,SU') {
    while (localIsoDay(candidate) < 6) candidate = addLocalDays(candidate, 1)
    return candidate
  }
  return null
}

/** Rolls a passed recurring time forward to its next valid local occurrence. */
export function rollRecurringReminderForward(result, timezone, now = new Date()) {
  if (
    typeof result.notifyAt !== 'string' ||
    !SUPPORTED_RECURRENCE_RULES.has(result.recurrenceRule) ||
    result.needsClarification === true
  ) return { result, rolledForward: false }

  const parsed = new Date(result.notifyAt)
  if (Number.isNaN(parsed.getTime()) || parsed > now) return { result, rolledForward: false }

  let local = zonedParts(parsed, timezone)
  let candidate = parsed
  for (let step = 0; step < 3_660 && candidate <= now; step += 1) {
    local = advanceLocalRecurrence(local, result.recurrenceRule)
    if (!local) return { result, rolledForward: false }
    candidate = zonedPartsToInstant(local, timezone)
  }
  if (candidate <= now) throw new Error('Nudgee could not find the next repeating reminder time.')

  return { result: { ...result, notifyAt: candidate.toISOString() }, rolledForward: true }
}

function hasUnqualifiedKoreanHour(text) {
  return unqualifiedKoreanHour(text) !== null
}

function unqualifiedKoreanHour(text) {
  const matcher = /(?:^|\s)([1-9]|1[0-2])\s*시(?=\s|$|[에쯤])/g
  for (const match of text.matchAll(matcher)) {
    const prefix = text.slice(Math.max(0, match.index - 8), match.index)
    if (!/(오전|오후|아침|낮|저녁|밤)\s*$/u.test(prefix)) return Number(match[1])
  }
  return null
}

function unqualifiedEnglishHour(text) {
  const match = /\bat\s+([1-9]|1[0-2])\b(?!\s*(?:am|pm)\b)|\b([1-9]|1[0-2])\s*o['’]?clock\b(?!\s*(?:am|pm)\b)/iu.exec(text)
  return match ? Number(match[1] ?? match[2]) : null
}

/**
 * A bare 1–12 hour is not enough information to schedule a reliable reminder.
 * Gemini is prompted to ask, but this server-side guard prevents an accidental
 * AM/PM guess from reaching a client if the model overlooks that instruction.
 */
export function requireMeridiemClarification(result, text) {
  const hour = unqualifiedEnglishHour(text) ?? unqualifiedKoreanHour(text)
  if (hour === null) return result

  return {
    ...result,
    notifyAt: null,
    suggestedTime: `${String(hour).padStart(2, '0')}:00`,
    needsClarification: true,
    clarificationType: 'time',
    clarification: 'Is that in the morning or evening? Choose the exact date and time below.',
  }
}

/**
 * Validates Gemini's untrusted structured response before it reaches a client.
 * `now` is injectable so its boundary conditions can be tested deterministically.
 */
export function validateParsedReminder(result, now = new Date()) {
  const title = typeof result.title === 'string' ? result.title.trim().slice(0, 240) : ''
  const notifyAt = typeof result.notifyAt === 'string' && !Number.isNaN(Date.parse(result.notifyAt)) ? result.notifyAt : null
  const requestedRule = typeof result.recurrenceRule === 'string' ? result.recurrenceRule : null
  const hasUnsupportedRecurrence = requestedRule !== null && !SUPPORTED_RECURRENCE_RULES.has(requestedRule)
  const recurrenceRule = hasUnsupportedRecurrence ? null : requestedRule
  const needsClarification = result.needsClarification === true || !notifyAt || hasUnsupportedRecurrence
  const clarification = typeof result.clarification === 'string' ? result.clarification.trim().slice(0, 240) : null
  const requestedClarificationType = result.clarificationType === 'recurrence' || result.clarificationType === 'time'
    ? result.clarificationType
    : null
  const clarificationType = hasUnsupportedRecurrence
    ? 'recurrence'
    : needsClarification
      ? requestedClarificationType ?? (notifyAt ? 'recurrence' : 'time')
      : null
  const suggestedTime = typeof result.suggestedTime === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(result.suggestedTime)
    ? result.suggestedTime
    : null

  if (!title) throw new Error('What should Nudgee remind you about? Try “Take vitamins every day at 9am.”')
  if (!needsClarification && new Date(notifyAt).getTime() <= now.getTime()) {
    throw new Error('That reminder time has already passed today. Choose a future time, or make it repeat (for example, “every day at 10am”).')
  }

  return {
    title,
    notifyAt,
    recurrenceRule,
    needsClarification,
    clarification: hasUnsupportedRecurrence
      ? 'Nudgee currently supports every day, every weekday, every weekend, or every week. Choose one below.'
      : clarification,
    clarificationType,
    suggestedTime,
  }
}
