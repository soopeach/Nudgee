import { createClient } from 'npm:@supabase/supabase-js@2'
import { recordOperationalError } from '../_shared/operationalErrors.ts'
import { applyServerRelativeTime, requireMeridiemClarification, rollRecurringReminderForward, validateParsedReminder } from '../_shared/reminderParsing.mjs'

type ParseRequest = { action?: unknown; text?: unknown; timezone?: unknown; locale?: unknown; now?: unknown }
type GeminiResult = {
  title?: unknown
  notifyAt?: unknown
  recurrenceRule?: unknown
  needsClarification?: unknown
  clarification?: unknown
  clarificationType?: unknown
}
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
      await recordOperationalError(client, 'parse_reminder', 'credit_claim_failed', creditError?.message ?? 'No credit claim result.', { requestId })
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
    const prompt = `You parse a user request into one Nudgee reminder. Current instant: ${input.now}. User timezone: ${input.timezone}. User locale: ${input.locale}.

Return a concise task title and an exact future RFC 3339 timestamp with its UTC offset only when the request unambiguously specifies a reminder time. Resolve relative dates from the current instant in the user's timezone. Never invent a time. For a repeating request whose stated time has already passed today, return the next valid repeating occurrence (for example, “every day at 10am” at 2pm means tomorrow at 10am).

An hour without an AM/PM or morning/afternoon qualifier is ambiguous. This includes English requests such as “at 10” or “10 o'clock”, and Korean requests such as “10시”. For these, do not guess. Set notifyAt to null, needsClarification to true, clarificationType to time, and ask whether the user means AM or PM. A 24-hour time such as 22:00 is unambiguous.

Nudgee supports only these recurrence values:
- null: one-time reminder (use when the user did not explicitly ask for repetition)
- FREQ=DAILY: every day
- FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR: every weekday / business day
- FREQ=WEEKLY;BYDAY=SA,SU: every weekend / Saturday and Sunday
- FREQ=WEEKLY: every week on the local weekday of notifyAt (including “every Monday”)

If the user requests an unsupported pattern (monthly, yearly, multiple weekdays, intervals such as every 2 days, multiple times per day, or an end date), do not invent a rule. Set recurrenceRule to null, needsClarification to true, clarificationType to recurrence, and explain that only daily, weekdays, and weekly are currently supported. If the repetition is clear but its time is missing, set needsClarification to true and clarificationType to time. If both are unclear, ask for the time first.\n\nUser request: ${input.text}`
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
              notifyAt: { type: ['string', 'null'] },
              recurrenceRule: {
                // Keep this nullable field permissive at the schema boundary
                // and enforce the allow-list in validateResult(). Gemini's
                // structured-output subset does not support nullable enums.
                type: ['string', 'null'],
              },
              needsClarification: { type: 'boolean' },
              clarification: { type: ['string', 'null'] },
              clarificationType: { type: ['string', 'null'] },
            },
            required: ['title', 'notifyAt', 'recurrenceRule', 'needsClarification', 'clarification', 'clarificationType'],
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
      await recordOperationalError(client, 'parse_reminder', 'gemini_request_failed', `Gemini request returned ${response.status}.`, { requestId, status: response.status })
      await refundParseCredit(client, user.id, input.timezone, credit, requestId, stage)
      return errorJson('Nudgee could not parse that reminder. Please try again.', 502, requestId, 'gemini_request_failed')
    }
    stage = 'result_validation'
    const result = JSON.parse(getInteractionText(await response.json())) as GeminiResult
    const { result: resultWithServerRelativeTime, usedServerRelativeTime } = applyServerRelativeTime(result, input.text, input.now)
    const { result: resultWithFutureRecurrence, rolledForward } = rollRecurringReminderForward(
      resultWithServerRelativeTime,
      input.timezone,
      new Date(input.now),
    )
    const resultWithRequiredMeridiem = requireMeridiemClarification(resultWithFutureRecurrence, input.text)
    const validatedResult = validateParsedReminder(resultWithRequiredMeridiem)
    console.info('Reminder parse completed', {
      requestId,
      userId: user.id,
      needsClarification: validatedResult.needsClarification,
      hasNotifyAt: validatedResult.notifyAt !== null,
      usedServerRelativeTime,
      rolledForward,
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
    await recordOperationalError(client, 'parse_reminder', stage, caught, { requestId })
    return errorJson(caught instanceof Error ? caught.message : 'Nudgee could not parse that reminder.', 400, requestId, 'reminder_parse_failed')
  }
})
