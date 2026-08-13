import { createClient } from '@supabase/supabase-js'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
const supabaseKey = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || import.meta.env.VITE_SUPABASE_ANON_KEY

function isValidSupabaseUrl(value: unknown): value is string {
  if (typeof value !== 'string' || !value.trim()) return false
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && (url.hostname.endsWith('.supabase.co') || url.hostname === 'localhost')
  } catch {
    return false
  }
}

export const isSupabaseConfigured = isValidSupabaseUrl(supabaseUrl) && Boolean(supabaseKey)
export const supabase = isSupabaseConfigured ? createClient(supabaseUrl, supabaseKey) : null
