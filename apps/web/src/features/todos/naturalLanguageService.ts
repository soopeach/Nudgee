import { supabase } from '../../lib/supabase'

export type ParsedReminder = {
  title: string
  notifyAt: string | null
  needsClarification: boolean
  clarification: string | null
}

function readParsedReminder(value: unknown): ParsedReminder {
  if (!value || typeof value !== 'object') throw new Error('Nudgee could not understand that reminder. Please try again.')
  const parsed = value as Record<string, unknown>
  if (typeof parsed.title !== 'string' || !parsed.title.trim()) throw new Error('Nudgee could not find a task in that reminder.')
  const notifyAt = typeof parsed.notifyAt === 'string' && !Number.isNaN(Date.parse(parsed.notifyAt)) ? parsed.notifyAt : null
  return {
    title: parsed.title.trim(),
    notifyAt,
    needsClarification: parsed.needsClarification === true,
    clarification: typeof parsed.clarification === 'string' ? parsed.clarification : null,
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
  if (error) throw new Error(error.message || 'Nudgee could not parse that reminder.')
  return readParsedReminder(data)
}
