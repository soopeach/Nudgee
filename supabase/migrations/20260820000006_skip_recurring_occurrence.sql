-- Skips one occurrence without ending its recurring chain. The row lock and
-- recurrence_next_created_at claim serialize this with the notification
-- dispatcher, so exactly one next occurrence remains scheduled.
create or replace function public.skip_recurring_occurrence(p_task_id uuid)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  occurrence public.tasks%rowtype;
  next_notify_at timestamptz;
  series_id uuid;
begin
  select *
    into occurrence
  from public.tasks
  where id = p_task_id
    and user_id = auth.uid()
    and recurrence_rule is not null
  for update;

  if not found then
    raise exception 'Recurring reminder not found.' using errcode = 'P0002';
  end if;

  if occurrence.recurrence_next_created_at is null then
    update public.tasks
    set recurrence_next_created_at = now()
    where id = occurrence.id
      and recurrence_next_created_at is null;

    next_notify_at := public.next_recurring_notify_at(
      occurrence.notify_at,
      occurrence.timezone,
      occurrence.recurrence_rule
    );
    series_id := coalesce(occurrence.recurrence_series_id, occurrence.id);

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
      occurrence.user_id,
      occurrence.title,
      next_notify_at,
      false,
      null,
      occurrence.timezone,
      'pending',
      occurrence.recurrence_rule,
      series_id,
      0,
      null
    );
  end if;

  delete from public.tasks
  where id = occurrence.id
    and user_id = auth.uid();
end;
$$;

revoke all on function public.skip_recurring_occurrence(uuid) from public;
grant execute on function public.skip_recurring_occurrence(uuid) to authenticated;
