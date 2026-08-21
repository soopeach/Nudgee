import { supabase } from '../../lib/supabase'

export type ParsedReminder = {
  title: string
  notifyAt: string | null
  recurrenceRule: string | null
  needsClarification: boolean
  clarification: string | null
  clarificationType: 'time' | 'recurrence' | null
}

const supportedRecurrenceRules = new Set([
  'FREQ=DAILY',
  'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR',
  'FREQ=WEEKLY;BYDAY=SA,SU',
  'FREQ=WEEKLY',
])

export type ReminderParseUsage = {
  usedFreeParses: number
  remainingFreeParses: number
  bonusCredits: number
  dailyFreeParseLimit: number
}

async function getFunctionErrorMessage(error: unknown, fallback: string) {
  const context = typeof error === 'object' && error !== null && 'context' in error
    ? (error as { context?: unknown }).context
    : null
  const response = context instanceof Response ? context : null
  const payload = response
    ? await response.json().catch(() => null) as { error?: unknown } | null
    : null

  if (typeof payload?.error === 'string' && payload.error.trim()) return payload.error
  if (error instanceof Error && error.message.trim()) return error.message
  return fallback
}

function readParsedReminder(value: unknown): ParsedReminder {
  if (!value || typeof value !== 'object') throw new Error('Nudgee could not understand that reminder. Please try again.')
  const parsed = value as Record<string, unknown>
  if (typeof parsed.title !== 'string' || !parsed.title.trim()) throw new Error('Nudgee could not find a task in that reminder.')
  const notifyAt = typeof parsed.notifyAt === 'string' && !Number.isNaN(Date.parse(parsed.notifyAt)) ? parsed.notifyAt : null
  const requestedRule = typeof parsed.recurrenceRule === 'string' ? parsed.recurrenceRule : null
  const recurrenceRule = requestedRule && supportedRecurrenceRules.has(requestedRule) ? requestedRule : null
  const hasUnsupportedRecurrence = requestedRule !== null && recurrenceRule === null
  const clarificationType = parsed.clarificationType === 'recurrence' || parsed.clarificationType === 'time'
    ? parsed.clarificationType
    : hasUnsupportedRecurrence ? 'recurrence' : !notifyAt ? 'time' : null
  return {
    title: parsed.title.trim(),
    notifyAt,
    recurrenceRule,
    needsClarification: parsed.needsClarification === true || hasUnsupportedRecurrence,
    clarification: hasUnsupportedRecurrence
      ? 'Nudgee currently supports every day, every weekday, every weekend, or every week. Choose one below.'
      : typeof parsed.clarification === 'string' ? parsed.clarification : null,
    clarificationType,
  }
}

export async function parseNaturalLanguageReminder(text: string): Promise<ParsedReminder> {
  if (!supabase) throw new Error('Supabase is not configured. Check your environment variables.')
  const { data, error } = await supabase.functions.invoke('parse-reminder', {
    body: {
      text,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      locale: navigator.language,
      now: new Date().toISOString(),
    },
  })
  if (error) throw new Error(await getFunctionErrorMessage(error, 'Nudgee could not parse that reminder.'))
  return readParsedReminder(data)
}

export async function getReminderParseUsage(): Promise<ReminderParseUsage> {
  if (!supabase) throw new Error('Supabase is not configured. Check your environment variables.')
  const { data, error } = await supabase.functions.invoke('parse-reminder', {
    body: { action: 'usage', timezone: Intl.DateTimeFormat().resolvedOptions().timeZone },
  })
  if (error) throw new Error(await getFunctionErrorMessage(error, 'Nudgee could not load AI reminder usage.'))
  const usage = data as Partial<ReminderParseUsage> | null
  if (!usage || typeof usage.remainingFreeParses !== 'number' || typeof usage.bonusCredits !== 'number') {
    throw new Error('Nudgee could not load AI reminder usage.')
  }
  return {
    usedFreeParses: usage.usedFreeParses ?? 0,
    remainingFreeParses: usage.remainingFreeParses,
    bonusCredits: usage.bonusCredits,
    dailyFreeParseLimit: usage.dailyFreeParseLimit ?? 10,
  }
}
