-- Nudgee RLS hardening. Run after the base tables exist.
-- These policies are the actual tenant boundary; never rely on client-side user_id filters alone.

alter table public.tasks enable row level security;
alter table public.device_tokens enable row level security;

drop policy if exists "Users can read own tasks" on public.tasks;
create policy "Users can read own tasks"
on public.tasks for select to authenticated
using (user_id = auth.uid());

drop policy if exists "Users can create own tasks" on public.tasks;
create policy "Users can create own tasks"
on public.tasks for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists "Users can update own tasks" on public.tasks;
create policy "Users can update own tasks"
on public.tasks for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists "Users can delete own tasks" on public.tasks;
create policy "Users can delete own tasks"
on public.tasks for delete to authenticated
using (user_id = auth.uid());

drop policy if exists "Users can read own device tokens" on public.device_tokens;
create policy "Users can read own device tokens"
on public.device_tokens for select to authenticated
using (user_id = auth.uid());

drop policy if exists "Users can register own device tokens" on public.device_tokens;
create policy "Users can register own device tokens"
on public.device_tokens for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists "Users can update own device tokens" on public.device_tokens;
create policy "Users can update own device tokens"
on public.device_tokens for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists "Users can delete own device tokens" on public.device_tokens;
create policy "Users can delete own device tokens"
on public.device_tokens for delete to authenticated
using (user_id = auth.uid());

-- Keep untrusted browser input bounded at the database boundary too.
alter table public.tasks drop constraint if exists tasks_title_length_check;
alter table public.tasks add constraint tasks_title_length_check check (char_length(trim(title)) between 1 and 500);

alter table public.device_tokens drop constraint if exists device_tokens_platform_check;
alter table public.device_tokens add constraint device_tokens_platform_check check (platform in ('web', 'ios', 'android', 'desktop'));
