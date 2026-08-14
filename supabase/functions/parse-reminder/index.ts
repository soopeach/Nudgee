import { createClient } from 'npm:@supabase/supabase-js@2'

type ParseRequest = { text?: unknown; timezone?: unknown; locale?: unknown; now?: unknown }
type GeminiResult = { title?: unknown; notifyAt?: unknown; needsClarification?: unknown; clarification?: unknown }

const corsHeaders = {
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Origin': '*',
  'Content-Type': 'application/json',
}

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders })
}

function getInteractionText(body: unknown) {
  const steps = (body as { steps?: Array<{ type?: string; content?: Array<{ type?: string; text?: string }> }> })?.steps ?? []
  return steps
    .filter((step) => step.type === 'model_output')
    .flatMap((step) => step.content ?? [])
    .filter((part) => part.type === 'text')
    .map((part) => part.text ?? '')
    .join('')
}

function validateRequest(body: ParseRequest) {
  const text = typeof body.text === 'string' ? body.text.trim() : ''
  const timezone = typeof body.timezone === 'string' ? body.timezone.trim() : ''
  const locale = typeof body.locale === 'string' ? body.locale.trim() : 'en'
  const now = typeof body.now === 'string' && !Number.isNaN(Date.parse(body.now)) ? body.now : new Date().toISOString()
  if (!text || text.length > 1_000) throw new Error('Enter a reminder with fewer than 1,000 characters.')
  try { new Intl.DateTimeFormat('en', { timeZone: timezone }) } catch { throw new Error('Your timezone is not valid.') }
  return { text, timezone, locale, now }
}

function validateResult(result: GeminiResult) {
  const title = typeof result.title === 'string' ? result.title.trim().slice(0, 240) : ''
  const notifyAt = typeof result.notifyAt === 'string' && !Number.isNaN(Date.parse(result.notifyAt)) ? result.notifyAt : null
  const needsClarification = result.needsClarification === true || !notifyAt
  const clarification = typeof result.clarification === 'string' ? result.clarification.trim().slice(0, 240) : null
  if (!title) throw new Error('No task was found in the response.')
  if (!needsClarification && new Date(notifyAt as string) <= new Date()) throw new Error('The reminder time must be in the future.')
  return { title, notifyAt, needsClarification, clarification }
}

Deno.serve(async (request) => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405)

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const anonKey = Deno.env.get('SUPABASE_ANON_KEY')
  const geminiKey = Deno.env.get('GEMINI_API_KEY')
  if (!supabaseUrl || !anonKey) return json({ error: 'Supabase function is not configured.' }, 500)
  if (!geminiKey) return json({ error: 'Natural-language reminders are not configured yet.' }, 503)

  const auth = request.headers.get('Authorization')
  if (!auth) return json({ error: 'Unauthorized.' }, 401)
  const client = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: auth } } })
  const { data: { user }, error: userError } = await client.auth.getUser()
  if (userError || !user) return json({ error: 'Unauthorized.' }, 401)

  try {
    const input = validateRequest(await request.json() as ParseRequest)
    const prompt = `You parse reminder requests into a single task. Current instant: ${input.now}. User timezone: ${input.timezone}. User locale: ${input.locale}.\n\nReturn a concise task title and an exact future RFC 3339 timestamp with its UTC offset only when the request unambiguously specifies a reminder time. Relative dates must be resolved from the current instant in the user's timezone. If no time, date, or sufficiently precise reminder moment is specified, set notifyAt to null and needsClarification to true. Never invent a time.\n\nUser request: ${input.text}`
    const response = await fetch('https://generativelanguage.googleapis.com/v1/interactions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': geminiKey,
      },
      body: JSON.stringify({
        model: 'gemini-3.6-flash',
        input: prompt,
        store: false,
        response_format: [{
          type: 'text',
          mime_type: 'application/json',
          schema: {
            type: 'object',
            properties: {
              title: { type: 'string' },
              notifyAt: { type: 'string', nullable: true },
              needsClarification: { type: 'boolean' },
              clarification: { type: 'string', nullable: true },
            },
            required: ['title', 'notifyAt', 'needsClarification', 'clarification'],
          },
        }],
      }),
    })
    if (!response.ok) {
      console.error('Gemini request failed', response.status, (await response.text()).slice(0, 500))
      return json({ error: 'Nudgee could not parse that reminder. Please try again.' }, 502)
    }
    const result = JSON.parse(getInteractionText(await response.json())) as GeminiResult
    return json(validateResult(result))
  } catch (caught) {
    console.error('Reminder parse failed', caught)
    return json({ error: caught instanceof Error ? caught.message : 'Nudgee could not parse that reminder.' }, 400)
  }
})
