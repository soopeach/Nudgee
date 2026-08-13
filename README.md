# Nudgee

Nudgee is a cross-device, notification-first todo app. This repository is a monorepo containing the React web client, shared Supabase configuration, and the future Kotlin Multiplatform client.

## Repository layout

```text
apps/web/       React + Vite web client (current project)
apps/client/    Kotlin Multiplatform + Compose client (planned)
supabase/       Database migrations, RLS policies, and future Edge Functions
docs/           Product and architecture documentation (planned)
```

## Web development

```bash
npm install --prefix apps/web
npm run dev
```

The Vercel project should use `apps/web` as its Root Directory. Web environment variables belong in `apps/web/.env` locally and in Vercel project settings.

Nudgee schedules notifications on the server. Clients write tasks and register push targets; they never own a local notification timer.

## Local setup

1. Create `apps/web/.env` locally and add the Supabase and Firebase Web FCM values.
2. Configure Google OAuth and the `tasks`/`device_tokens` tables in Supabase.
3. Run `npm run dev` from the repository root.

## Deploy

Deploy `apps/web` to Vercel with `apps/web` as the Root Directory. The Supabase Edge Function and scheduled trigger will provide the server-owned notification scheduler; Firebase is used only as the web FCM delivery channel.
