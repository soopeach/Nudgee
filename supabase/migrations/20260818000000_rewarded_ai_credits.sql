-- Server-owned balance and audit trail for rewarded AI reminder credits.
-- Credits are granted only by a future verified AdMob SSV callback.

create table if not exists public.ai_parse_credit_balances (
  user_id uuid primary key references auth.users(id) on delete cascade,
  balance integer not null default 0 check (balance >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.ai_parse_credit_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  delta integer not null check (delta <> 0),
  reason text not null check (reason in ('rewarded_ad', 'parse_consumption', 'parse_refund', 'admin_adjustment')),
  provider text,
  provider_transaction_id text,
  created_at timestamptz not null default now()
);

alter table public.ai_parse_credit_balances enable row level security;
alter table public.ai_parse_credit_events enable row level security;

create index if not exists ai_parse_credit_events_user_created_idx
  on public.ai_parse_credit_events (user_id, created_at desc);

create unique index if not exists ai_parse_credit_events_provider_transaction_idx
  on public.ai_parse_credit_events (provider, provider_transaction_id)
  where provider is not null and provider_transaction_id is not null;

drop trigger if exists ai_parse_credit_balances_set_updated_at on public.ai_parse_credit_balances;
create trigger ai_parse_credit_balances_set_updated_at
before update on public.ai_parse_credit_balances
for each row execute function public.set_updated_at();

-- Idempotent credit grant for a trusted server callback. The unique provider
-- transaction key makes duplicate AdMob callbacks harmless.
create or replace function public.grant_ai_parse_credits(
  p_user_id uuid,
  p_amount integer,
  p_reason text,
  p_provider text,
  p_provider_transaction_id text
)
returns table (granted boolean, balance integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  event_id uuid;
  updated_balance integer;
begin
  if p_user_id is null or p_amount <= 0 then
    raise exception 'A user and positive credit amount are required.';
  end if;
  if p_reason not in ('rewarded_ad', 'admin_adjustment') then
    raise exception 'Unsupported credit grant reason.';
  end if;

  insert into public.ai_parse_credit_events (
    user_id, delta, reason, provider, provider_transaction_id
  ) values (
    p_user_id, p_amount, p_reason, nullif(trim(p_provider), ''), nullif(trim(p_provider_transaction_id), '')
  )
  on conflict (provider, provider_transaction_id) where provider is not null and provider_transaction_id is not null
  do nothing
  returning id into event_id;

  if event_id is null then
    select coalesce(b.balance, 0) into updated_balance
    from public.ai_parse_credit_balances b
    where b.user_id = p_user_id;
    return query select false, coalesce(updated_balance, 0);
    return;
  end if;

  insert into public.ai_parse_credit_balances (user_id, balance)
  values (p_user_id, p_amount)
  on conflict (user_id) do update
    set balance = public.ai_parse_credit_balances.balance + excluded.balance
  returning public.ai_parse_credit_balances.balance into updated_balance;

  return query select true, updated_balance;
end;
$$;

drop function if exists public.consume_reminder_parse_credit(uuid, text);
create function public.consume_reminder_parse_credit(
  p_user_id uuid,
  p_timezone text
)
returns table (
  allowed boolean,
  remaining_free_parses integer,
  remaining_bonus_credits integer,
  credit_source text
)
language plpgsql
security definer
set search_path = public
as $$
declare
  local_usage_date date;
  current_count integer;
  current_bonus integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then raise exception 'A user is required to claim a parse credit.'; end if;
  begin
    local_usage_date := timezone(p_timezone, now())::date;
  exception when invalid_parameter_value then
    raise exception 'Invalid timezone.';
  end;

  insert into public.ai_parse_daily_usage (user_id, usage_date, free_parse_count)
  values (p_user_id, local_usage_date, 1)
  on conflict (user_id, usage_date) do update
    set free_parse_count = public.ai_parse_daily_usage.free_parse_count + 1
    where public.ai_parse_daily_usage.free_parse_count < daily_limit
  returning free_parse_count into current_count;

  if found then
    select coalesce(balance, 0) into current_bonus
    from public.ai_parse_credit_balances where user_id = p_user_id;
    return query select true, greatest(0, daily_limit - current_count), coalesce(current_bonus, 0), 'free';
    return;
  end if;

  update public.ai_parse_credit_balances
  set balance = balance - 1
  where user_id = p_user_id and balance > 0
  returning balance into current_bonus;

  if found then
    insert into public.ai_parse_credit_events (user_id, delta, reason)
    values (p_user_id, -1, 'parse_consumption');
    return query select true, 0, current_bonus, 'bonus';
    return;
  end if;

  return query select false, 0, 0, 'none';
end;
$$;

drop function if exists public.get_reminder_parse_usage(uuid, text);
create function public.get_reminder_parse_usage(
  p_user_id uuid,
  p_timezone text
)
returns table (
  used_free_parses integer,
  remaining_free_parses integer,
  bonus_credits integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  local_usage_date date;
  current_count integer;
  current_bonus integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then raise exception 'A user is required to read parse usage.'; end if;
  begin
    local_usage_date := timezone(p_timezone, now())::date;
  exception when invalid_parameter_value then
    raise exception 'Invalid timezone.';
  end;
  select free_parse_count into current_count from public.ai_parse_daily_usage
  where user_id = p_user_id and usage_date = local_usage_date;
  select balance into current_bonus from public.ai_parse_credit_balances where user_id = p_user_id;
  current_count := least(daily_limit, coalesce(current_count, 0));
  current_bonus := coalesce(current_bonus, 0);
  return query select current_count, greatest(0, daily_limit - current_count), current_bonus;
end;
$$;

revoke all on function public.grant_ai_parse_credits(uuid, integer, text, text, text) from public;
revoke all on function public.consume_reminder_parse_credit(uuid, text) from public;
revoke all on function public.get_reminder_parse_usage(uuid, text) from public;
grant execute on function public.grant_ai_parse_credits(uuid, integer, text, text, text) to service_role;
grant execute on function public.consume_reminder_parse_credit(uuid, text) to service_role;
grant execute on function public.get_reminder_parse_usage(uuid, text) to service_role;
