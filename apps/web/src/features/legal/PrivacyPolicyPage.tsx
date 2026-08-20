import { navigateTo, routes } from '../navigation/routes'
import type { ReactNode } from 'react'

const lastUpdated = 'August 20, 2026'

export function PrivacyPolicyPage() {
  return (
    <main className="app-shell legal-app-shell">
      <article className="legal-page" aria-labelledby="privacy-policy-title">
        <header className="legal-page-header">
          <button className="home-link" type="button" onClick={() => navigateTo(routes.home)}>← Back to Nudgee</button>
          <span className="eyebrow">Nudgee</span>
          <h1 id="privacy-policy-title">Privacy Policy</h1>
          <p>Last updated: {lastUpdated}</p>
        </header>

        <p>Nudgee is developed by Hyunsoo Jeon. This Privacy Policy explains how Nudgee collects, uses, and shares information when you use the Nudgee mobile, desktop, and web applications (collectively, the “Service”).</p>
        <p>If you have questions about this policy, contact us at <a href="mailto:hsjeon584@gmail.com">hsjeon584@gmail.com</a>.</p>

        <PolicySection title="1. Information We Collect">
          <h3>Account information</h3>
          <p>When you sign in with Google, we receive your name, email address, and profile picture through Supabase Authentication. We use this information to identify your account and associate it with your Nudgee data.</p>
          <h3>Content you provide</h3>
          <p>We collect task and reminder text you enter, including titles and natural-language reminder requests, along with the reminder time and timezone associated with each task.</p>
          <h3>Device and notification information</h3>
          <p>We collect Firebase Cloud Messaging registration tokens, device platform, browser or user-agent information, and app version to deliver reminders to your registered devices. To send a reminder, Firebase Cloud Messaging receives the notification title, task title, task identifier, and device push token.</p>
          <h3>Usage and reward information</h3>
          <p>We record daily natural-language reminder usage and reward-credit balances to enforce the free allowance and optional rewarded-ad credits.</p>
          <h3>Advertising information</h3>
          <p>On Android, Nudgee offers an optional rewarded-ad feature. Google Mobile Ads may collect information such as advertising and device identifiers, IP address, ad interactions, and diagnostic information for advertising, analytics, and fraud prevention. Nudgee does not show banner or interstitial ads.</p>
        </PolicySection>

        <PolicySection title="2. How We Use Your Information">
          <ul>
            <li>Create and maintain your account.</li>
            <li>Store and synchronise tasks across your devices.</li>
            <li>Deliver reminders to registered devices at the times you request.</li>
            <li>Process natural-language reminder requests.</li>
            <li>Enforce usage limits and reward credits.</li>
            <li>Diagnose, prevent, and fix technical issues or abuse.</li>
          </ul>
          <p>We do not sell your personal information. We do not use task content for advertising or marketing.</p>
        </PolicySection>

        <PolicySection title="3. Third-Party Services">
          <div className="legal-table-wrap">
            <table>
              <thead><tr><th>Service</th><th>Purpose</th><th>Data received</th></tr></thead>
              <tbody>
                <tr><td><a href="https://supabase.com/privacy" target="_blank" rel="noreferrer">Supabase</a></td><td>Authentication, database, realtime sync, and server-side reminder scheduling</td><td>Account information, task content, reminder data, device tokens</td></tr>
                <tr><td><a href="https://policies.google.com/privacy" target="_blank" rel="noreferrer">Google Sign-In</a></td><td>Account authentication</td><td>Name, email address, profile picture</td></tr>
                <tr><td><a href="https://firebase.google.com/support/privacy" target="_blank" rel="noreferrer">Firebase Cloud Messaging</a></td><td>Push notification delivery</td><td>Push token, notification content, task identifier</td></tr>
                <tr><td><a href="https://ai.google.dev/gemini-api/terms" target="_blank" rel="noreferrer">Google Gemini API</a></td><td>Convert natural-language reminders into a task and reminder time</td><td>Text entered into the natural-language field, timezone, locale, and the current server time used for interpretation</td></tr>
                <tr><td><a href="https://policies.google.com/privacy" target="_blank" rel="noreferrer">Google AdMob</a></td><td>Optional rewarded ads for additional reminder credits on Android</td><td>Advertising and device identifiers, IP address, ad interactions, and diagnostics as described by Google</td></tr>
              </tbody>
            </table>
          </div>
          <p>These providers process information as necessary to provide their services. Their independent data practices are governed by their own policies.</p>
        </PolicySection>

        <PolicySection title="4. Data Retention">
          <p>We retain your account information, tasks, reminder data, and active device tokens while your account is active. We retain daily usage and reward-credit records while your account is active for service operation, fraud prevention, and audit purposes.</p>
          <p>When you delete your account from Settings, Nudgee deletes your account data, tasks, device tokens, notification delivery records, and usage or reward records from our systems as part of one database transaction. Our service providers may retain limited information under their own retention policies.</p>
        </PolicySection>

        <PolicySection title="5. Data Security">
          <p>We use HTTPS/TLS for data in transit and database-level access controls, including Row Level Security, to restrict Nudgee data to the relevant account. No transmission or storage method is completely secure, so we cannot guarantee absolute security.</p>
        </PolicySection>

        <PolicySection title="6. Your Rights and Choices">
          <ul>
            <li><strong>Access and correction:</strong> You can view and edit task data in Nudgee.</li>
            <li><strong>Account deletion:</strong> You can delete your account and associated Nudgee data directly from Settings.</li>
            <li><strong>Notifications:</strong> You can disable notifications in your device or browser settings.</li>
            <li><strong>Sign out:</strong> You can sign out from Settings at any time.</li>
          </ul>
          <p>Depending on where you live, you may have additional rights, including rights to access, delete, correct, or export personal information. Contact us at <a href="mailto:hsjeon584@gmail.com">hsjeon584@gmail.com</a> to make a request.</p>
          <p>For step-by-step deletion instructions, visit <a href={routes.accountDeletion}>Delete your account</a>.</p>
        </PolicySection>

        <PolicySection title="7. Children’s Privacy">
          <p>Nudgee is not directed to children under 13, or the minimum age required by local law. We do not knowingly collect personal information from children.</p>
        </PolicySection>

        <PolicySection title="8. International Data Transfers">
          <p>Your information may be processed or stored outside your country of residence where Supabase, Google, and other service providers operate infrastructure.</p>
        </PolicySection>

        <PolicySection title="9. Changes to This Policy">
          <p>We may update this policy from time to time. We will update the “Last updated” date and may provide additional notice in the Service when changes are material.</p>
        </PolicySection>

        <PolicySection title="10. Contact">
          <p>For privacy questions or requests, contact <a href="mailto:hsjeon584@gmail.com">hsjeon584@gmail.com</a>.</p>
        </PolicySection>
      </article>
    </main>
  )
}

function PolicySection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="legal-section"><h2>{title}</h2>{children}</section>
}
