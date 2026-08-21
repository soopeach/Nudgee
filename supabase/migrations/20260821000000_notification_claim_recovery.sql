-- A scheduler invocation can end after claiming tasks but before it records a
-- final result (runtime timeout, deployment, or network interruption). Keep a
-- timestamp for that lease and safely return expired claims to pending.

alter table public.tasks
  add column if not exists notification_processing_started_at timestamptz;

-- Backfill rows claimed before this column existed. `updated_at` is written by
-- claim_due_tasks, so it is the best available lease start time for them.
update public.tasks
set notification_processing_started_at = updated_at
where notification_state = 'processing'
  and notification_processing_started_at is null;

create index if not exists tasks_processing_claim_idx
  on public.tasks (notification_processing_started_at)
  where completed = false and notification_state = 'processing';

create or replace function public.claim_due_tasks(p_limit integer default 50)
returns setof public.tasks
language sql
security definer
set search_path = public
as $$
  with candidates as (
    select id
    from public.tasks
    where completed = false
      and notify_at <= now()
      and notification_state in ('pending', 'failed')
    order by notify_at asc
    for update skip locked
    limit least(greatest(coalesce(p_limit, 50), 1), 100)
  )
  update public.tasks as task
  set notification_state = 'processing',
      notification_processing_started_at = now(),
      updated_at = now()
  from candidates
  where task.id = candidates.id
  returning task.*;
$$;

create or replace function public.reclaim_stale_notification_claims(
  p_stale_after interval default interval '15 minutes'
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  reclaimed_count integer;
begin
  if p_stale_after < interval '1 minute' then
    raise exception 'The stale claim interval must be at least one minute.';
  end if;

  update public.tasks
  set notification_state = 'pending',
      notification_processing_started_at = null,
      updated_at = now()
  where completed = false
    and notification_state = 'processing'
    and coalesce(notification_processing_started_at, updated_at) < now() - p_stale_after;

  get diagnostics reclaimed_count = row_count;
  return reclaimed_count;
end;
$$;

revoke all on function public.reclaim_stale_notification_claims(interval) from public, anon, authenticated;
grant execute on function public.reclaim_stale_notification_claims(interval) to service_role;
