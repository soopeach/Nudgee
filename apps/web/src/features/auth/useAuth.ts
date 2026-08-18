import { useCallback, useEffect, useState } from 'react'
import { isSupabaseConfigured, supabase } from '../../lib/supabase'
import { deleteCurrentAccount, mapSupabaseUser, signInWithGoogle, signOutCurrentUser } from './authService'
import type { AuthenticatedUser } from './types'
import { registerCurrentDevice } from '../devices/deviceRegistration'
import { syncProfileTimezone } from './profileTimezone'

type AuthState = { user: AuthenticatedUser | null; isLoading: boolean; error: string | null }

export function useAuth() {
  const [state, setState] = useState<AuthState>({ user: null, isLoading: isSupabaseConfigured, error: null })
  useEffect(() => {
    if (!supabase) { setState({ user: null, isLoading: false, error: 'Supabase 설정이 올바르지 않습니다. .env의 VITE_SUPABASE_URL을 Project URL(https://프로젝트-ref.supabase.co)로 입력해 주세요.' }); return }
    void supabase.auth.getSession().then(({ data, error }) => {
      const mappedUser = mapSupabaseUser(data.session?.user ?? null)
      setState({ user: mappedUser, isLoading: false, error: error?.message ?? null })
      if (mappedUser) {
        void registerCurrentDevice(mappedUser.id).catch(() => undefined)
        void syncProfileTimezone(mappedUser.id).catch(() => undefined)
      }
    })
    const { data: listener } = supabase.auth.onAuthStateChange((_event, session) => {
      const mappedUser = mapSupabaseUser(session?.user ?? null)
      setState({ user: mappedUser, isLoading: false, error: null })
      if (mappedUser) {
        void registerCurrentDevice(mappedUser.id).catch(() => undefined)
        void syncProfileTimezone(mappedUser.id).catch(() => undefined)
      }
    })
    return () => listener.subscription.unsubscribe()
  }, [])
  const signIn = useCallback(async () => { try { setState((current) => ({ ...current, error: null })); await signInWithGoogle() } catch (error) { setState((current) => ({ ...current, error: error instanceof Error ? error.message : 'Sign-in could not be completed.' })) } }, [])
  const signOut = useCallback(async () => { try { await signOutCurrentUser(); setState({ user: null, isLoading: false, error: null }) } catch (error) { setState((current) => ({ ...current, error: error instanceof Error ? error.message : 'Sign-out could not be completed.' })) } }, [])
  const deleteAccount = useCallback(async () => {
    try { await deleteCurrentAccount(); setState({ user: null, isLoading: false, error: null }) }
    catch (error) { const message = error instanceof Error ? error.message : 'Nudgee could not delete your account. Please try again.'; setState((current) => ({ ...current, error: message })); throw new Error(message) }
  }, [])
  return { ...state, signIn, signOut, deleteAccount }
}
