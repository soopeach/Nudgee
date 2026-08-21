-- Editing a repeating reminder changes its remaining schedule as one unit.
-- A dispatched occurrence may already have created its successor, so updating
-- one row in place would otherwise leave the next reminder on the old rule.

create or replace function public.replace_recurring_schedule(
  p_task_id uuid,
  p_title text,
  p_notify_at timestamptz,
  p_timezone text,
  p_recurrence_rule text
)
returns public.tasks
language plpgsql
security invoker
set search_path = public
as $$
declare
  selected_task public.tasks%rowtype;
  replacement public.tasks%rowtype;
  series_root_id uuid;
begin
  if nullif(trim(p_title), '') is null then
    raise exception 'A reminder title is required.' using errcode = '22023';
  end if;
  if p_notify_at <= clock_timestamp() then
    raise exception 'Choose a future reminder time.' using errcode = '22023';
  end if;

  select *
    into selected_task
  from public.tasks
  where id = p_task_id
    and user_id = auth.uid()
    and completed = false
  for update;

  if not found then
    raise exception 'Reminder not found or already completed.' using errcode = 'P0002';
  end if;

  series_root_id := coalesce(selected_task.recurrence_series_id, selected_task.id);

  -- Serialize with skip/stop/dispatch and replace every still-open occurrence
  -- in this chain. Completed task history is deliberately preserved.
  perform 1
  from public.tasks
  where user_id = auth.uid()
    and completed = false
    and (id = series_root_id or recurrence_series_id = series_root_id)
  for update;

  delete from public.tasks
  where user_id = auth.uid()
    and completed = false
    and (id = series_root_id or recurrence_series_id = series_root_id);

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
    notification_processing_started_at
  ) values (
    selected_task.user_id,
    trim(p_title),
    p_notify_at,
    false,
    null,
    p_timezone,
    'pending',
    p_recurrence_rule,
    case when p_recurrence_rule is null then null else series_root_id end,
    0,
    null,
    null
  )
  returning * into replacement;

  return replacement;
end;
$$;

revoke all on function public.replace_recurring_schedule(uuid, text, timestamptz, text, text) from public;
grant execute on function public.replace_recurring_schedule(uuid, text, timestamptz, text, text) to authenticated;
