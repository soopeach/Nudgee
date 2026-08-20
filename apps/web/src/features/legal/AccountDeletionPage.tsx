import { navigateTo, routes } from '../navigation/routes'

export function AccountDeletionPage() {
  return (
    <main className="app-shell legal-app-shell">
      <article className="legal-page" aria-labelledby="account-deletion-title">
        <header className="legal-page-header">
          <button className="home-link" type="button" onClick={() => navigateTo(routes.home)}>← Back to Nudgee</button>
          <span className="eyebrow">Nudgee account</span>
          <h1 id="account-deletion-title">Delete your account</h1>
          <p>Delete your Nudgee account and the data associated with it.</p>
        </header>

        <section className="legal-section legal-deletion-callout">
          <h2>Delete directly in Nudgee</h2>
          <ol>
            <li>Open Nudgee and sign in to the account you want to delete.</li>
            <li>Open <strong>Settings</strong>.</li>
            <li>Select <strong>Delete account</strong>.</li>
            <li>Enter <strong>DELETE</strong> and confirm.</li>
          </ol>
          <button className="add-button legal-open-nudgee" type="button" onClick={() => navigateTo(routes.home)}>Open Nudgee <span>→</span></button>
        </section>

        <section className="legal-section">
          <h2>What is deleted</h2>
          <p>When you confirm account deletion, Nudgee deletes your account profile, tasks and reminder data, device notification tokens, notification delivery records, daily AI reminder usage, and reward-credit records from Nudgee’s systems in one transaction.</p>
          <p>Deletion is permanent and cannot be undone. Some service providers may retain limited information under their own legal or operational retention policies.</p>
        </section>

        <section className="legal-section">
          <h2>Need help?</h2>
          <p>If you cannot access your account or need help with deletion, email <a href="mailto:hsjeon584@gmail.com?subject=Nudgee%20account%20deletion%20request">hsjeon584@gmail.com</a> from the email address associated with your Nudgee account.</p>
        </section>

        <p className="legal-footnote">For more information about how Nudgee handles data, read our <a href={routes.privacy}>Privacy Policy</a>.</p>
      </article>
    </main>
  )
}
