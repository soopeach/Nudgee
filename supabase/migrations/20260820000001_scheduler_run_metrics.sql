-- A compact, server-owned heartbeat for the notification scheduler.
-- Retention is enforced by the dispatcher and is limited to 30 days.

create table if not exists public.notification_scheduler_runs (
  id uuid primary key default gen_random_uuid(),
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  status text not null default 'running' check (status in ('running', 'succeeded', 'failed')),
  claimed_tasks integer not null default 0 check (claimed_tasks >= 0),
  failed_tasks integer not null default 0 check (failed_tasks >= 0),
  error_message text check (error_message is null or char_length(error_message) <= 500)
);

alter table public.notification_scheduler_runs enable row level security;

create index if not exists notification_scheduler_runs_finished_at_idx
  on public.notification_scheduler_runs (finished_at desc);

create index if not exists notification_scheduler_runs_status_finished_at_idx
  on public.notification_scheduler_runs (status, finished_at desc);

revoke all on table public.notification_scheduler_runs from anon, authenticated;
grant select, insert, update, delete on table public.notification_scheduler_runs to service_role;
