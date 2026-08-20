import { createClient } from 'npm:@supabase/supabase-js@2'
import { recordOperationalError } from '../_shared/operationalErrors.ts'

const ADMOB_KEYS_URL = 'https://www.gstatic.com/admob/reward/verifier-keys.json'
const KEY_CACHE_TTL_MS = 24 * 60 * 60 * 1000
const REWARD_CUSTOM_DATA = 'nudgee_ai_credits_v1'
const REWARD_AMOUNT = 5
// AdMob uses this documented placeholder unit while its console verifies the
// callback URL. It is never an impression from Nudgee's real ad unit.
const ADMOB_URL_VERIFICATION_AD_UNIT_ID = '1234567890'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

type AdMobKey = { keyId: number; base64: string }
type AdMobKeyResponse = { keys?: AdMobKey[] }
type RewardGrant = { granted: boolean; balance: number }

let cachedKeys: Map<string, CryptoKey> | null = null
let cacheExpiresAt = 0

async function refreshVerificationKeys() {
  const response = await fetch(ADMOB_KEYS_URL)
  if (!response.ok) throw new Error(`Unable to fetch AdMob verification keys (${response.status}).`)
  const keyResponse = await response.json() as AdMobKeyResponse
  const importedKeys = await Promise.all((keyResponse.keys ?? []).map(async (entry) => [
    String(entry.keyId),
    await crypto.subtle.importKey(
      'spki',
      base64ToBytes(entry.base64),
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify'],
    ),
  ] as const))
  if (importedKeys.length === 0) throw new Error('AdMob did not return verification keys.')
  cachedKeys = new Map(importedKeys)
  cacheExpiresAt = Date.now() + KEY_CACHE_TTL_MS
}

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function getServerAuthKey() {
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (serviceRoleKey) return serviceRoleKey

  const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
  if (!secretKeys) return null

  try {
    return (JSON.parse(secretKeys) as Record<string, string>).default ?? null
  } catch {
    return null
  }
}

function base64ToBytes(value: string) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const binary = atob(padded)
  return Uint8Array.from(binary, (character) => character.charCodeAt(0))
}

/** Converts AdMob's DER-encoded P-256 ECDSA signature to Web Crypto's r||s form. */
function derEcdsaSignatureToP1363(signature: Uint8Array) {
  let offset = 0
  const readLength = () => {
    const first = signature[offset++]
    if (first === undefined) throw new Error('Invalid DER signature.')
    if ((first & 0x80) === 0) return first
    const count = first & 0x7f
    if (count === 0 || count > 2) throw new Error('Invalid DER signature length.')
    let length = 0
    for (let index = 0; index < count; index += 1) {
      const byte = signature[offset++]
      if (byte === undefined) throw new Error('Invalid DER signature length.')
      length = (length << 8) | byte
    }
    return length
  }
  const readInteger = () => {
    if (signature[offset++] !== 0x02) throw new Error('Invalid DER integer.')
    const length = readLength()
    const bytes = signature.slice(offset, offset + length)
    offset += length
    if (bytes.length !== length) throw new Error('Invalid DER integer length.')
    const unsigned = bytes.length > 1 && bytes[0] === 0 ? bytes.slice(1) : bytes
    if (unsigned.length === 0 || unsigned.length > 32) throw new Error('Invalid P-256 signature.')
    const padded = new Uint8Array(32)
    padded.set(unsigned, 32 - unsigned.length)
    return padded
  }

  if (signature[offset++] !== 0x30) throw new Error('Invalid DER sequence.')
  const sequenceLength = readLength()
  if (sequenceLength !== signature.length - offset) throw new Error('Invalid DER sequence length.')
  const r = readInteger()
  const s = readInteger()
  if (offset !== signature.length) throw new Error('Unexpected DER signature data.')
  return new Uint8Array([...r, ...s])
}

async function getVerificationKey(keyId: string) {
  if (!cachedKeys || Date.now() >= cacheExpiresAt) {
    await refreshVerificationKeys()
  }
  // A newly rotated key can arrive before this warm Edge isolate reaches its
  // 24-hour cache deadline. Refresh once so a legitimate callback is not lost.
  if (!cachedKeys?.has(keyId)) {
    await refreshVerificationKeys()
  }
  return cachedKeys?.get(keyId) ?? null
}

function requireSingleParameter(params: URLSearchParams, name: string) {
  const values = params.getAll(name)
  if (values.length !== 1 || !values[0]) throw new Error(`Missing or repeated ${name}.`)
  return values[0]
}

function normalizedAdUnitId(value: string) {
  return value.replace(/^ca-app-pub-\d+\//, '')
}

async function verifyAdMobSignature(url: URL) {
  const rawQuery = url.search.startsWith('?') ? url.search.slice(1) : ''
  const signatureStart = rawQuery.indexOf('&signature=')
  if (signatureStart <= 0) throw new Error('Missing signature.')
  const signatureAndKey = rawQuery.slice(signatureStart + 1)
  const keyMarker = '&key_id='
  const keyStart = signatureAndKey.indexOf(keyMarker)
  if (keyStart <= 'signature='.length) throw new Error('Missing key ID.')
  if (signatureAndKey.includes(keyMarker, keyStart + keyMarker.length)) throw new Error('Repeated key ID.')

  const contentToVerify = rawQuery.slice(0, signatureStart)
  const signatureValue = signatureAndKey.slice('signature='.length, keyStart)
  const keyId = signatureAndKey.slice(keyStart + keyMarker.length)
  if (!/^[0-9]+$/.test(keyId)) throw new Error('Invalid key ID.')

  const key = await getVerificationKey(keyId)
  if (!key) throw new Error(`Unknown AdMob key ID: ${keyId}.`)
  const valid = await crypto.subtle.verify(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    derEcdsaSignatureToP1363(base64ToBytes(signatureValue)),
    new TextEncoder().encode(contentToVerify),
  )
  if (!valid) throw new Error('Invalid AdMob signature.')
}

Deno.serve(async (request) => {
  if (request.method !== 'GET' && request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405)

  const url = new URL(request.url)
  try {
    await verifyAdMobSignature(url)

    const params = url.searchParams
    const userId = requireSingleParameter(params, 'user_id')
    const transactionId = requireSingleParameter(params, 'transaction_id')
    const rewardItem = requireSingleParameter(params, 'reward_item')
    const rewardAmount = requireSingleParameter(params, 'reward_amount')
    const customData = requireSingleParameter(params, 'custom_data')
    const adUnit = requireSingleParameter(params, 'ad_unit')

    if (!UUID_PATTERN.test(userId)) throw new Error('Invalid user ID.')
    // Production callbacks use a hex transaction ID, but AdMob's console URL
    // verification can use a non-hex test value. Its ECDSA signature is the
    // authenticity boundary; keep this validation to safe storage limits.
    if (!/^[^\x00-\x1f\x7f]{1,256}$/.test(transactionId)) throw new Error('Invalid transaction ID.')
    if (customData !== REWARD_CUSTOM_DATA) throw new Error('Unexpected reward context.')
    if (rewardItem !== (Deno.env.get('ADMOB_REWARD_ITEM')?.trim() || 'nudgee_ai_credits')) throw new Error('Unexpected reward item.')
    if (Number(rewardAmount) !== Number(Deno.env.get('ADMOB_REWARD_AMOUNT')?.trim() || REWARD_AMOUNT)) throw new Error('Unexpected reward amount.')

    const configuredAdUnit = Deno.env.get('ADMOB_REWARDED_AD_UNIT_ID')?.trim()
    const isUrlVerification = adUnit === ADMOB_URL_VERIFICATION_AD_UNIT_ID
    if (!isUrlVerification && configuredAdUnit && normalizedAdUnitId(adUnit) !== normalizedAdUnitId(configuredAdUnit)) {
      throw new Error('Unexpected AdMob ad unit.')
    }

    // The AdMob console sends this signed request only to confirm that the
    // callback URL is reachable. A verification must never alter user balance.
    if (isUrlVerification) return json({ status: 'verified' })

    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    const serverAuthKey = getServerAuthKey()
    if (!supabaseUrl || !serverAuthKey) throw new Error('Supabase function is not configured.')

    const client = createClient(supabaseUrl, serverAuthKey)
    const { data, error } = await client.rpc('grant_ai_parse_credits', {
      p_user_id: userId,
      p_amount: REWARD_AMOUNT,
      p_reason: 'rewarded_ad',
      p_provider: 'admob',
      p_provider_transaction_id: transactionId,
    }).single<RewardGrant>()
    if (error || !data) throw new Error(`Could not grant AI credits: ${error?.message ?? 'unknown error'}`)

    return json({ status: data.granted ? 'granted' : 'duplicate', balance: data.balance })
  } catch (error) {
    console.error('AdMob SSV callback rejected', error)
    const message = error instanceof Error ? error.message : 'Unknown error.'
    // Invalid signatures/configuration must not be retried. Infrastructure
    // failures use 500 so AdMob can make its documented delivery retries.
    const retryable = message.startsWith('Unable to fetch AdMob')
      || message.startsWith('AdMob did not return')
      || message.startsWith('Supabase function')
      || message.startsWith('Could not grant AI credits')
    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    const serverAuthKey = getServerAuthKey()
    if (supabaseUrl && serverAuthKey) {
      await recordOperationalError(
        createClient(supabaseUrl, serverAuthKey),
        'rewarded_ad',
        'callback_rejected',
        error,
        { retryable },
      )
    }
    return json({ error: retryable ? 'Reward verification is temporarily unavailable.' : 'Invalid rewarded-ad callback.' }, retryable ? 500 : 400)
  }
})
