-- A recurring reminder advances when its current occurrence has actually been
-- delivered, not only when the user marks it complete. This keeps tomorrow's
-- reminder alive even when today's task is left open.

alter table public.tasks
  add column if not exists recurrence_next_created_at timestamptz;

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
  -- A regular occurrence is advanced after its delivery succeeds. Completion
  -- still advances an occurrence that was completed before it became due.
  if old.recurrence_rule is null
    or old.recurrence_next_created_at is not null
    or not (
      (old.notification_state is distinct from 'sent' and new.notification_state = 'sent')
      or (old.completed = false and new.completed = true)
    ) then
    return new;
  end if;

  -- Atomically claim the right to create the next occurrence. This protects
  -- against a delivery state update racing with a notification Complete action.
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
    recurrence_next_created_at
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
    null
  );

  return new;
end;
$$;
