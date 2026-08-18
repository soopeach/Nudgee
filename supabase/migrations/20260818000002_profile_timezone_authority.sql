-- AI allowance dates are derived from the server-stored profile timezone.
-- The request timezone remains available to the parser only for interpreting
-- natural-language dates; it must not decide a user's daily credit boundary.

create or replace function public.get_profile_timezone(p_user_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  profile_timezone text;
begin
  select timezone into profile_timezone
  from public.profiles
  where user_id = p_user_id;

  if profile_timezone is null or btrim(profile_timezone) = '' then
    raise exception 'A profile timezone is required.';
  end if;

  begin
    perform timezone(profile_timezone, now());
  exception when invalid_parameter_value then
    raise exception 'Profile timezone is invalid.';
  end;
  return profile_timezone;
end;
$$;

-- Keep the existing RPC signatures for a non-breaking rollout, but deliberately
-- ignore p_timezone when calculating the daily ledger date.
create or replace function public.consume_reminder_parse_credit(p_user_id uuid, p_timezone text)
returns table (allowed boolean, remaining_free_parses integer, remaining_bonus_credits integer, credit_source text)
language plpgsql security definer set search_path = public
as $$
declare
  local_usage_date date;
  profile_timezone text;
  current_count integer;
  current_bonus integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then raise exception 'A user is required to claim a parse credit.'; end if;
  profile_timezone := public.get_profile_timezone(p_user_id);
  local_usage_date := timezone(profile_timezone, now())::date;

  insert into public.ai_parse_daily_usage (user_id, usage_date, free_parse_count)
  values (p_user_id, local_usage_date, 1)
  on conflict (user_id, usage_date) do update
    set free_parse_count = public.ai_parse_daily_usage.free_parse_count + 1
    where public.ai_parse_daily_usage.free_parse_count < daily_limit
  returning free_parse_count into current_count;

  if found then
    select coalesce(balance, 0) into current_bonus from public.ai_parse_credit_balances where user_id = p_user_id;
    return query select true, greatest(0, daily_limit - current_count), coalesce(current_bonus, 0), 'free';
    return;
  end if;

  update public.ai_parse_credit_balances set balance = balance - 1
  where user_id = p_user_id and balance > 0 returning balance into current_bonus;
  if found then
    insert into public.ai_parse_credit_events (user_id, delta, reason) values (p_user_id, -1, 'parse_consumption');
    return query select true, 0, current_bonus, 'bonus';
    return;
  end if;
  return query select false, 0, 0, 'none';
end;
$$;

create or replace function public.get_reminder_parse_usage(p_user_id uuid, p_timezone text)
returns table (used_free_parses integer, remaining_free_parses integer, bonus_credits integer)
language plpgsql security definer set search_path = public
as $$
declare
  local_usage_date date;
  profile_timezone text;
  current_count integer;
  current_bonus integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then raise exception 'A user is required to read parse usage.'; end if;
  profile_timezone := public.get_profile_timezone(p_user_id);
  local_usage_date := timezone(profile_timezone, now())::date;
  select free_parse_count into current_count from public.ai_parse_daily_usage where user_id = p_user_id and usage_date = local_usage_date;
  select balance into current_bonus from public.ai_parse_credit_balances where user_id = p_user_id;
  current_count := least(daily_limit, coalesce(current_count, 0));
  return query select current_count, greatest(0, daily_limit - current_count), coalesce(current_bonus, 0);
end;
$$;

create or replace function public.refund_reminder_parse_credit(p_user_id uuid, p_timezone text, p_credit_source text)
returns table (remaining_free_parses integer, remaining_bonus_credits integer)
language plpgsql security definer set search_path = public
as $$
declare
  local_usage_date date;
  profile_timezone text;
  current_free_count integer;
  current_bonus integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then raise exception 'A user is required to refund a parse credit.'; end if;
  if p_credit_source not in ('free', 'bonus') then raise exception 'Invalid parse credit source.'; end if;
  profile_timezone := public.get_profile_timezone(p_user_id);
  local_usage_date := timezone(profile_timezone, now())::date;
  if p_credit_source = 'free' then
    update public.ai_parse_daily_usage set free_parse_count = free_parse_count - 1
    where user_id = p_user_id and usage_date = local_usage_date and free_parse_count > 0;
  else
    insert into public.ai_parse_credit_balances (user_id, balance) values (p_user_id, 1)
    on conflict (user_id) do update set balance = public.ai_parse_credit_balances.balance + 1
    returning balance into current_bonus;
    insert into public.ai_parse_credit_events (user_id, delta, reason) values (p_user_id, 1, 'parse_refund');
  end if;
  select coalesce(free_parse_count, 0) into current_free_count from public.ai_parse_daily_usage where user_id = p_user_id and usage_date = local_usage_date;
  select coalesce(balance, 0) into current_bonus from public.ai_parse_credit_balances where user_id = p_user_id;
  return query select greatest(0, daily_limit - coalesce(current_free_count, 0)), coalesce(current_bonus, 0);
end;
$$;

revoke all on function public.get_profile_timezone(uuid) from public;
grant execute on function public.get_profile_timezone(uuid) to service_role;
