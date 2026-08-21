-- Add the weekend recurrence rule across the server-owned scheduler.

alter table public.tasks
  drop constraint if exists tasks_recurrence_rule_check;

alter table public.tasks
  add constraint tasks_recurrence_rule_check
  check (
    recurrence_rule is null
    or recurrence_rule in (
      'FREQ=DAILY',
      'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR',
      'FREQ=WEEKLY;BYDAY=SA,SU',
      'FREQ=WEEKLY'
    )
  );

-- Work in the task's IANA timezone so the selected local time stays stable
-- across daylight-saving transitions.
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
      when 'FREQ=WEEKLY;BYDAY=SA,SU' then
        local_time := local_time + interval '1 day';
        while extract(isodow from local_time) not in (6, 7) loop
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
