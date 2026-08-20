  type LoginScreenProps = {
  error: string | null
  onSignIn: () => Promise<void>
}

export function LoginScreen({ error, onSignIn }: LoginScreenProps) {
  return (
    <main className="app-shell">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand-mark" aria-hidden="true">n</div>
        <span className="eyebrow">Nudgee</span>
        <h1 id="login-title">A gentle nudge,<br />right on time.</h1>
        <p>Keep your plans close and get reminded when it matters.</p>
        <button className="google-button" type="button" onClick={() => void onSignIn()}>
          <GoogleIcon />
          Continue with Google
        </button>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <small>By continuing, you agree to keep things delightfully organised. Read our <a href="/privacy">Privacy Policy</a>.</small>
      </section>
    </main>
  )
}

function GoogleIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.2-2.05H12v3.88h5.38a4.6 4.6 0 0 1-1.99 3.02v2.52h3.24c1.9-1.75 2.97-4.33 2.97-7.37Z" /><path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.63-2.4l-3.24-2.52c-.9.6-2.05.95-3.39.95-2.6 0-4.8-1.76-5.59-4.12H3.07v2.6A10 10 0 0 0 12 22Z" /><path fill="#FBBC05" d="M6.41 13.91A6.01 6.01 0 0 1 6.1 12c0-.66.11-1.3.31-1.91v-2.6H3.07A10 10 0 0 0 2 12c0 1.61.39 3.13 1.07 4.51l3.34-2.6Z" /><path fill="#EA4335" d="M12 5.97c1.47 0 2.8.51 3.84 1.51l2.88-2.88C16.96 2.96 14.7 2 12 2a10 10 0 0 0-8.93 5.49l3.34 2.6C7.2 7.73 9.4 5.97 12 5.97Z" /></svg>
}
