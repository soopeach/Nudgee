-- Delivery failures must not stop a recurring schedule, and they must not be
-- retried every minute forever. Keep a task-level bounded retry lease.

alter table public.tasks
  add column if not exists notification_attempt_count integer not null default 0
    check (notification_attempt_count >= 0),
  add column if not exists notification_retry_at timestamptz;

-- Preserve retry behaviour for failure rows created before retry timestamps
-- existed; the next scheduler pass will claim them once under the new policy.
update public.tasks
set notification_retry_at = now()
where notification_state = 'failed'
  and notification_retry_at is null
  and notification_attempt_count = 0;

create index if not exists tasks_notification_retry_idx
  on public.tasks (notification_retry_at)
  where completed = false and notification_state = 'failed' and notification_retry_at is not null;

-- A user-initiated reschedule is a fresh delivery attempt, rather than a
-- continuation of a previous transient failure budget.
create or replace function public.manage_task_notification_lifecycle()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if old.completed = false and new.completed = true then
    if old.notification_state in ('pending', 'failed', 'processing') then
      new.notification_state := 'cancelled';
    end if;
    new.notification_retry_at := null;
    new.notification_processing_started_at := null;
    return new;
  end if;

  if old.completed = true and new.completed = false then
    if old.notification_state = 'cancelled' then
      new.notification_state := case when new.notify_at > now() then 'pending' else 'cancelled' end;
      new.notification_retry_at := null;
      new.notification_processing_started_at := null;
    end if;
    return new;
  end if;

  if new.completed = false
    and new.notify_at is distinct from old.notify_at
    and old.notification_state in ('pending', 'failed', 'cancelled') then
    new.notification_state := case when new.notify_at > now() then 'pending' else 'cancelled' end;
    new.notification_attempt_count := case when new.notify_at > now() then 0 else old.notification_attempt_count end;
    new.notification_retry_at := null;
    new.notification_processing_started_at := null;
  end if;

  return new;
end;
$$;

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
      and (
        (notification_state = 'pending' and notify_at <= now())
        or (notification_state = 'failed' and notification_retry_at is not null and notification_retry_at <= now())
      )
    order by coalesce(notification_retry_at, notify_at) asc
    for update skip locked
    limit least(greatest(coalesce(p_limit, 50), 1), 100)
  )
  update public.tasks as task
  set notification_state = 'processing',
      notification_attempt_count = notification_attempt_count + 1,
      notification_retry_at = null,
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
  set notification_state = 'failed',
      notification_retry_at = case
        when notification_attempt_count >= 5 then null
        else now() + interval '5 minutes'
      end,
      notification_processing_started_at = null,
      updated_at = now()
  where completed = false
    and notification_state = 'processing'
    and coalesce(notification_processing_started_at, updated_at) < now() - p_stale_after;

  get diagnostics reclaimed_count = row_count;
  return reclaimed_count;
end;
$$;

-- Advance recurrence on the first terminal delivery attempt, whether it
-- succeeds or fails. A failed occurrence may still retry, but it no longer
-- blocks the following scheduled occurrence from existing.
create or replace function public.create_next_recurring_task()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  next_notify_at timestamptz;
  series_id uuid;
  claimed_task_id uuid;
begin
  if old.recurrence_rule is null
    or old.recurrence_next_created_at is not null
    or not (
      (old.notification_state is distinct from new.notification_state and new.notification_state in ('sent', 'failed'))
      or (old.completed = false and new.completed = true)
    ) then
    return new;
  end if;

  update public.tasks
  set recurrence_next_created_at = now()
  where id = new.id
    and recurrence_next_created_at is null
  returning id into claimed_task_id;

  if claimed_task_id is null then
    return new;
  end if;

  next_notify_at := public.next_recurring_notify_at(
    old.notify_at,
    old.timezone,
    old.recurrence_rule
  );
  series_id := coalesce(old.recurrence_series_id, old.id);

  insert into public.tasks (
    user_id,
    title,
    notify_at,
    completed,
    completed_at,
    timezone,
    notification_state,
    recurrence_rule,
    recurrence_series_id,
    notification_occurrence,
    recurrence_next_created_at,
    notification_processing_started_at,
    notification_attempt_count,
    notification_retry_at
  ) values (
    old.user_id,
    old.title,
    next_notify_at,
    false,
    null,
    old.timezone,
    'pending',
    old.recurrence_rule,
    series_id,
    0,
    null,
    null,
    0,
    null
  );

  return new;
end;
$$;
