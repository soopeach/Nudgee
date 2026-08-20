import { createClient } from 'npm:@supabase/supabase-js@2'

type ParseRequest = { action?: unknown; text?: unknown; timezone?: unknown; locale?: unknown; now?: unknown }
type GeminiResult = { title?: unknown; notifyAt?: unknown; needsClarification?: unknown; clarification?: unknown }
type ParseCreditResult = {
  allowed: boolean
  remaining_free_parses: number
  remaining_bonus_credits: number
  credit_source: 'free' | 'bonus' | 'none'
}
type ParseUsageResult = { used_free_parses: number; remaining_free_parses: number; bonus_credits: number }
type ServiceRoleRpcClient = {
  rpc: (functionName: string, arguments_: Record<string, unknown>) => PromiseLike<{ error: { message: string } | null }>
}

const corsHeaders = {
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Origin': '*',
  'Content-Type': 'application/json',
}
const GEMINI_INTERACTIONS_URL = 'https://generativelanguage.googleapis.com/v1/interactions'
const GEMINI_MODEL = 'gemini-3.1-flash-lite'
const DAILY_FREE_PARSE_LIMIT = 10

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders })
}

function errorJson(error: string, status: number, requestId: string, code?: string) {
  return json({ error, code, requestId }, status)
}

function getServerAuthKey() {
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (serviceRoleKey) return serviceRoleKey
  const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
  if (secretKeys) {
    const parsed = JSON.parse(secretKeys) as Record<string, string>
    if (parsed.default) return parsed.default
  }
  return null
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

function validateTimezone(value: unknown) {
  const timezone = typeof value === 'string' ? value.trim() : ''
  try { new Intl.DateTimeFormat('en', { timeZone: timezone }) } catch { throw new Error('Your timezone is not valid.') }
  return timezone
}

function validateRequest(body: ParseRequest) {
  const text = typeof body.text === 'string' ? body.text.trim() : ''
  const timezone = validateTimezone(body.timezone)
  const locale = typeof body.locale === 'string' ? body.locale.trim() : 'en'
  // Relative reminders are a server-owned promise. A client-provided clock can
  // be stale or incorrect, so use the Edge runtime clock as the one reference.
  const now = new Date().toISOString()
  if (!text || text.length > 1_000) throw new Error('Enter a reminder with fewer than 1,000 characters.')
  return { text, timezone, locale, now }
}

function explicitRelativeOffsetMs(text: string): number | null {
  const patterns: Array<{ regex: RegExp; unitMs: number }> = [
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

function validateResult(result: GeminiResult) {
  const title = typeof result.title === 'string' ? result.title.trim().slice(0, 240) : ''
  const notifyAt = typeof result.notifyAt === 'string' && !Number.isNaN(Date.parse(result.notifyAt)) ? result.notifyAt : null
  const needsClarification = result.needsClarification === true || !notifyAt
  const clarification = typeof result.clarification === 'string' ? result.clarification.trim().slice(0, 240) : null
  if (!title) throw new Error('No task was found in the response.')
  if (!needsClarification && new Date(notifyAt as string) <= new Date()) throw new Error('The reminder time must be in the future.')
  return { title, notifyAt, needsClarification, clarification }
}

async function refundParseCredit(
  client: ServiceRoleRpcClient,
  userId: string,
  timezone: string,
  credit: ParseCreditResult,
  requestId: string,
  failureStage: string,
) {
  if (!credit.allowed || (credit.credit_source !== 'free' && credit.credit_source !== 'bonus')) return

  try {
    const { error } = await client.rpc('refund_reminder_parse_credit', {
      p_user_id: userId,
      p_timezone: timezone,
      p_credit_source: credit.credit_source,
    })
    if (error) {
      console.error('Reminder parse credit refund failed', {
        requestId,
        userId,
        creditSource: credit.credit_source,
        failureStage,
        error: error.message,
      })
      return
    }
    console.info('Reminder parse credit refunded', {
      requestId,
      userId,
      creditSource: credit.credit_source,
      failureStage,
    })
  } catch (caught) {
    console.error('Reminder parse credit refund failed', {
      requestId,
      userId,
      creditSource: credit.credit_source,
      failureStage,
      error: caught instanceof Error ? caught.message : String(caught),
    })
  }
}

Deno.serve(async (request) => {
  const requestId = crypto.randomUUID()
  if (request.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  if (request.method !== 'POST') return errorJson('Method not allowed.', 405, requestId)

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serverAuthKey = getServerAuthKey()
  const geminiKey = Deno.env.get('GEMINI_API_KEY')
  if (!supabaseUrl || !serverAuthKey) return errorJson('Supabase function is not configured.', 500, requestId)
  if (!geminiKey) return errorJson('Natural-language reminders are not configured yet.', 503, requestId)

  const auth = request.headers.get('Authorization')
  if (!auth) return errorJson('Unauthorized.', 401, requestId)
  const accessToken = auth.replace(/^Bearer\s+/i, '').trim()
  if (!accessToken) return errorJson('Unauthorized.', 401, requestId)
  const client = createClient(supabaseUrl, serverAuthKey)
  const { data: { user }, error: userError } = await client.auth.getUser(accessToken)
  if (userError || !user) return errorJson('Unauthorized.', 401, requestId)

  let stage = 'request_validation'
  let claimedCredit: ParseCreditResult | null = null
  let claimedTimezone: string | null = null
  try {
    const body = await request.json() as ParseRequest
    if (body.action === 'usage') {
      stage = 'usage_lookup'
      const { data: usage, error: usageError } = await client
        .rpc('get_reminder_parse_usage', {
          p_user_id: user.id,
          p_timezone: validateTimezone(body.timezone),
        })
        .single<ParseUsageResult>()
      if (usageError || !usage) {
        console.error('Reminder parse usage lookup failed', { requestId, userId: user.id, error: usageError?.message })
        return errorJson('Nudgee could not load your parse usage. Please try again.', 500, requestId)
      }
      return json({
        usedFreeParses: usage.used_free_parses,
        remainingFreeParses: usage.remaining_free_parses,
        bonusCredits: usage.bonus_credits,
        dailyFreeParseLimit: DAILY_FREE_PARSE_LIMIT,
      })
    }

    stage = 'request_validation'
    const input = validateRequest(body)
    console.info('Reminder parse requested', {
      requestId,
      userId: user.id,
      inputLength: input.text.length,
      timezone: input.timezone,
      locale: input.locale,
    })
    stage = 'credit_claim'
    const { data: credit, error: creditError } = await client
      .rpc('consume_reminder_parse_credit', {
        p_user_id: user.id,
        p_timezone: input.timezone,
      })
      .single<ParseCreditResult>()

    if (creditError || !credit) {
      console.error('Reminder parse credit claim failed', { requestId, userId: user.id, error: creditError?.message })
      return errorJson('Nudgee could not prepare your reminder. Please try again.', 500, requestId)
    }
    if (!credit.allowed) {
      return json({
        error: 'You have used all 10 free reminder parses for today. Try again tomorrow.',
        code: 'daily_parse_limit_reached',
        requestId,
        remainingFreeParses: 0,
        bonusCredits: 0,
      }, 429)
    }
    claimedCredit = credit
    claimedTimezone = input.timezone

    stage = 'gemini_request'
    const prompt = `You parse reminder requests into a single task. Current instant: ${input.now}. User timezone: ${input.timezone}. User locale: ${input.locale}.\n\nReturn a concise task title and an exact future RFC 3339 timestamp with its UTC offset only when the request unambiguously specifies a reminder time. Relative dates must be resolved from the current instant in the user's timezone. If no time, date, or sufficiently precise reminder moment is specified, set notifyAt to null and needsClarification to true. Never invent a time.\n\nUser request: ${input.text}`
    const response = await fetch(GEMINI_INTERACTIONS_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': geminiKey,
      },
      body: JSON.stringify({
        model: GEMINI_MODEL,
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
      console.error('Gemini reminder parse request failed', {
        requestId,
        userId: user.id,
        status: response.status,
        response: (await response.text()).slice(0, 500),
      })
      await refundParseCredit(client, user.id, input.timezone, credit, requestId, stage)
      return errorJson('Nudgee could not parse that reminder. Please try again.', 502, requestId, 'gemini_request_failed')
    }
    stage = 'result_validation'
    const result = JSON.parse(getInteractionText(await response.json())) as GeminiResult
    const relativeOffsetMs = explicitRelativeOffsetMs(input.text)
    const resultWithServerRelativeTime: GeminiResult = relativeOffsetMs == null
      ? result
      : {
          ...result,
          notifyAt: new Date(Date.parse(input.now) + relativeOffsetMs).toISOString(),
          needsClarification: false,
        }
    const validatedResult = validateResult(resultWithServerRelativeTime)
    console.info('Reminder parse completed', {
      requestId,
      userId: user.id,
      needsClarification: validatedResult.needsClarification,
      hasNotifyAt: validatedResult.notifyAt !== null,
      usedServerRelativeTime: relativeOffsetMs !== null,
    })
    return json({
      ...validatedResult,
      remainingFreeParses: credit.remaining_free_parses,
      bonusCredits: credit.remaining_bonus_credits,
    })
  } catch (caught) {
    if (claimedCredit && claimedTimezone && (stage === 'gemini_request' || stage === 'result_validation')) {
      await refundParseCredit(client, user.id, claimedTimezone, claimedCredit, requestId, stage)
    }
    console.error('Reminder parse failed', {
      requestId,
      userId: user.id,
      stage,
      error: caught instanceof Error ? caught.message : String(caught),
    })
    return errorJson(caught instanceof Error ? caught.message : 'Nudgee could not parse that reminder.', 400, requestId, 'reminder_parse_failed')
  }
})
