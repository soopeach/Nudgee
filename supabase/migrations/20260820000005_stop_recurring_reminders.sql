-- Stops an entire recurring chain while retaining completed history.
-- The current and future incomplete occurrences are removed together so a
-- reminder cannot quietly continue after the user chose to stop it.
create or replace function public.stop_recurring_reminder(p_task_id uuid)
returns integer
language plpgsql
security invoker
set search_path = public
as $$
declare
  series_root_id uuid;
  removed_count integer;
begin
  select coalesce(recurrence_series_id, id)
    into series_root_id
  from public.tasks
  where id = p_task_id
    and user_id = auth.uid()
    and recurrence_rule is not null
  for update;

  if series_root_id is null then
    raise exception 'Recurring reminder not found.' using errcode = 'P0002';
  end if;

  -- First prevent any concurrent delivery transition from advancing a task.
  update public.tasks
  set recurrence_rule = null
  where user_id = auth.uid()
    and completed = false
    and (id = series_root_id or recurrence_series_id = series_root_id);

  delete from public.tasks
  where user_id = auth.uid()
    and completed = false
    and (id = series_root_id or recurrence_series_id = series_root_id);

  get diagnostics removed_count = row_count;
  return removed_count;
end;
$$;

revoke all on function public.stop_recurring_reminder(uuid) from public;
grant execute on function public.stop_recurring_reminder(uuid) to authenticated;
