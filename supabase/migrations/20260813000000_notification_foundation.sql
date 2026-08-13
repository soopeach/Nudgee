-- Nudgee notification foundation
-- Run in Supabase SQL Editor or through the Supabase CLI.

create extension if not exists pgcrypto;

-- Keep task lifecycle and scheduling state explicit.
alter table public.tasks
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists timezone text not null default 'UTC',
  add column if not exists notification_state text not null default 'pending',
  add column if not exists recurrence_rule text;

alter table public.tasks
  drop constraint if exists tasks_notification_state_check;

alter table public.tasks
  add constraint tasks_notification_state_check
  check (notification_state in ('pending', 'processing', 'sent', 'failed', 'cancelled'));

create index if not exists tasks_user_notify_at_idx
  on public.tasks (user_id, notify_at);

create index if not exists tasks_due_notification_idx
  on public.tasks (notify_at)
  where completed = false and notification_state in ('pending', 'failed');

-- Device tokens are rotated and can become invalid without the user signing out.
alter table public.device_tokens
  add column if not exists is_active boolean not null default true,
  add column if not exists last_seen_at timestamptz not null default now(),
  add column if not exists device_name text,
  add column if not exists app_version text;

create unique index if not exists device_tokens_user_platform_token_idx
  on public.device_tokens (user_id, platform, token);

create index if not exists device_tokens_active_user_idx
  on public.device_tokens (user_id)
  where is_active = true;

-- One row per task/device/channel delivery attempt.
create table if not exists public.notification_deliveries (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references public.tasks(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  device_token_id uuid references public.device_tokens(id) on delete set null,
  channel text not null check (channel in ('web', 'android', 'ios', 'desktop')),
  status text not null default 'pending' check (status in ('pending', 'processing', 'sent', 'failed', 'cancelled')),
  sent_at timestamptz,
  failed_at timestamptz,
  error_message text,
  attempt_count integer not null default 0 check (attempt_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists notification_deliveries_task_device_channel_idx
  on public.notification_deliveries (task_id, device_token_id, channel);

create index if not exists notification_deliveries_pending_idx
  on public.notification_deliveries (status, created_at)
  where status in ('pending', 'failed');

-- App-level preferences. Auth identity remains in auth.users.
create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  timezone text not null default 'UTC',
  default_reminder_minutes integer not null default 0 check (default_reminder_minutes >= 0),
  notification_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists tasks_set_updated_at on public.tasks;
create trigger tasks_set_updated_at
before update on public.tasks
for each row execute function public.set_updated_at();

drop trigger if exists device_tokens_set_updated_at on public.device_tokens;
create trigger device_tokens_set_updated_at
before update on public.device_tokens
for each row execute function public.set_updated_at();

drop trigger if exists notification_deliveries_set_updated_at on public.notification_deliveries;
create trigger notification_deliveries_set_updated_at
before update on public.notification_deliveries
for each row execute function public.set_updated_at();

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

alter table public.notification_deliveries enable row level security;
alter table public.profiles enable row level security;

drop policy if exists "Users can read own notification deliveries" on public.notification_deliveries;
create policy "Users can read own notification deliveries"
on public.notification_deliveries for select
to authenticated
using (user_id = auth.uid());

drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile"
on public.profiles for select
to authenticated
using (user_id = auth.uid());

drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile"
on public.profiles for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile"
on public.profiles for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());
