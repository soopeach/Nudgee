-- Nudgee AI reminder parsing: server-owned daily free allowance.
-- The Edge Function uses the RPC below with the service-role client. No client
-- policy is granted for this table, so a browser or native app cannot bypass it.

create table if not exists public.ai_parse_daily_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  usage_date date not null,
  free_parse_count integer not null default 0 check (free_parse_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, usage_date)
);

alter table public.ai_parse_daily_usage enable row level security;

create index if not exists ai_parse_daily_usage_date_idx
  on public.ai_parse_daily_usage (usage_date);

drop trigger if exists ai_parse_daily_usage_set_updated_at on public.ai_parse_daily_usage;
create trigger ai_parse_daily_usage_set_updated_at
before update on public.ai_parse_daily_usage
for each row execute function public.set_updated_at();

-- Atomically claims one of the ten free parses for the user's local calendar day.
-- A future rewarded-credit system should be consumed by this same RPC rather than
-- trusting a client-side counter.
create or replace function public.consume_reminder_parse_credit(
  p_user_id uuid,
  p_timezone text
)
returns table (
  allowed boolean,
  remaining_free_parses integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  local_usage_date date;
  current_count integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then
    raise exception 'A user is required to claim a parse credit.';
  end if;

  begin
    local_usage_date := timezone(p_timezone, now())::date;
  exception
    when invalid_parameter_value then
      raise exception 'Invalid timezone.';
  end;

  insert into public.ai_parse_daily_usage (user_id, usage_date, free_parse_count)
  values (p_user_id, local_usage_date, 1)
  on conflict (user_id, usage_date) do update
    set free_parse_count = public.ai_parse_daily_usage.free_parse_count + 1
    where public.ai_parse_daily_usage.free_parse_count < daily_limit
  returning free_parse_count into current_count;

  if found then
    return query select true, greatest(0, daily_limit - current_count);
    return;
  end if;

  return query select false, 0;
end;
$$;

revoke all on function public.consume_reminder_parse_credit(uuid, text) from public;
grant execute on function public.consume_reminder_parse_credit(uuid, text) to service_role;
