-- Recurring reminders are stored as a chain of ordinary task occurrences.
-- Completing one occurrence creates exactly one future occurrence on the
-- server, which preserves history without pre-generating an infinite series.

alter table public.tasks
  add column if not exists recurrence_series_id uuid,
  add column if not exists recurrence_rule text;

alter table public.tasks
  drop constraint if exists tasks_recurrence_rule_check;

alter table public.tasks
  add constraint tasks_recurrence_rule_check
  check (
    recurrence_rule is null
    or recurrence_rule in (
      'FREQ=DAILY',
      'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR',
      'FREQ=WEEKLY'
    )
  );

create index if not exists tasks_recurrence_series_idx
  on public.tasks (recurrence_series_id, notify_at);

-- Advances in the task's own IANA timezone so a 9:00 AM reminder remains at
-- 9:00 AM when daylight-saving transitions occur.
create or replace function public.next_recurring_notify_at(
  p_notify_at timestamptz,
  p_timezone text,
  p_recurrence_rule text
)
returns timestamptz
language plpgsql
volatile
set search_path = public
as $$
declare
  local_time timestamp;
  candidate timestamptz;
begin
  if p_recurrence_rule is null then
    return null;
  end if;

  local_time := p_notify_at at time zone p_timezone;
  loop
    case p_recurrence_rule
      when 'FREQ=DAILY' then
        local_time := local_time + interval '1 day';
      when 'FREQ=WEEKLY' then
        local_time := local_time + interval '7 days';
      when 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR' then
        local_time := local_time + interval '1 day';
        while extract(isodow from local_time) in (6, 7) loop
          local_time := local_time + interval '1 day';
        end loop;
      else
        raise exception 'Unsupported recurrence rule: %', p_recurrence_rule;
    end case;

    candidate := local_time at time zone p_timezone;
    exit when candidate > clock_timestamp();
  end loop;

  return candidate;
end;
$$;

create or replace function public.create_next_recurring_task()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  next_notify_at timestamptz;
  series_id uuid;
begin
  if old.completed = false
    and new.completed = true
    and old.recurrence_rule is not null then
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
      notification_occurrence
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
      0
    );
  end if;
  return new;
end;
$$;

drop trigger if exists tasks_create_next_recurring_task on public.tasks;
create trigger tasks_create_next_recurring_task
after update on public.tasks
for each row execute function public.create_next_recurring_task();
