import { supabase } from '../../lib/supabase'
import type { AuthenticatedUser } from './types'

export function mapSupabaseUser(user: { id: string; user_metadata?: Record<string, unknown>; email?: string } | null): AuthenticatedUser | null {
  if (!user) return null
  return { id: user.id, displayName: String(user.user_metadata?.full_name ?? user.user_metadata?.name ?? 'Nudgee user'), email: user.email ?? '', photoURL: typeof user.user_metadata?.avatar_url === 'string' ? user.user_metadata.avatar_url : null }
}

export async function signInWithGoogle() {
  if (!supabase) throw new Error('Supabase 설정이 올바르지 않습니다. .env의 VITE_SUPABASE_URL을 Project URL(https://프로젝트-ref.supabase.co)로 입력해 주세요.')
  const { error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: {
      redirectTo: `${window.location.origin}/`,
      queryParams: { access_type: 'offline', prompt: 'select_account' },
    },
  })
  if (error) throw error
}

export async function signOutCurrentUser() {
  if (!supabase) return
  const { error } = await supabase.auth.signOut()
  if (error) throw error
}
