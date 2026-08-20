-- Private, server-written operational errors for the Nudgee admin dashboard.
-- Do not store reminder text, tokens, email addresses, or provider responses here.

create table if not exists public.operational_error_events (
  id uuid primary key default gen_random_uuid(),
  source text not null check (source in ('parse_reminder', 'notification_dispatch', 'rewarded_ad')),
  event_type text not null,
  message text not null check (char_length(message) <= 500),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

alter table public.operational_error_events enable row level security;

create index if not exists operational_error_events_created_at_idx
  on public.operational_error_events (created_at desc);

create index if not exists operational_error_events_source_created_at_idx
  on public.operational_error_events (source, created_at desc);

revoke all on table public.operational_error_events from anon, authenticated;
grant select, insert, delete on table public.operational_error_events to service_role;
