-- A failed Gemini request must not consume a user's daily or rewarded parse credit.

alter table public.ai_parse_credit_events
  drop constraint if exists ai_parse_credit_events_reason_check;

alter table public.ai_parse_credit_events
  add constraint ai_parse_credit_events_reason_check
  check (reason in ('rewarded_ad', 'parse_consumption', 'parse_refund', 'admin_adjustment'));

create or replace function public.refund_reminder_parse_credit(
  p_user_id uuid,
  p_timezone text,
  p_credit_source text
)
returns table (
  remaining_free_parses integer,
  remaining_bonus_credits integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  local_usage_date date;
  current_free_count integer;
  current_bonus integer;
  daily_limit constant integer := 10;
begin
  if p_user_id is null then raise exception 'A user is required to refund a parse credit.'; end if;
  if p_credit_source not in ('free', 'bonus') then raise exception 'Invalid parse credit source.'; end if;
  begin
    local_usage_date := timezone(p_timezone, now())::date;
  exception when invalid_parameter_value then
    raise exception 'Invalid timezone.';
  end;

  if p_credit_source = 'free' then
    update public.ai_parse_daily_usage
    set free_parse_count = free_parse_count - 1
    where user_id = p_user_id
      and usage_date = local_usage_date
      and free_parse_count > 0
    returning free_parse_count into current_free_count;
  else
    insert into public.ai_parse_credit_balances (user_id, balance)
    values (p_user_id, 1)
    on conflict (user_id) do update
      set balance = public.ai_parse_credit_balances.balance + 1
    returning balance into current_bonus;

    insert into public.ai_parse_credit_events (user_id, delta, reason)
    values (p_user_id, 1, 'parse_refund');
  end if;

  select coalesce(free_parse_count, 0) into current_free_count
  from public.ai_parse_daily_usage
  where user_id = p_user_id and usage_date = local_usage_date;
  select coalesce(balance, 0) into current_bonus
  from public.ai_parse_credit_balances
  where user_id = p_user_id;

  return query select
    greatest(0, daily_limit - coalesce(current_free_count, 0)),
    coalesce(current_bonus, 0);
end;
$$;

revoke all on function public.refund_reminder_parse_credit(uuid, text, text) from public;
grant execute on function public.refund_reminder_parse_credit(uuid, text, text) to service_role;
