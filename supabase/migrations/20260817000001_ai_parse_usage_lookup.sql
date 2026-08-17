-- Read-only companion to the atomic parse-credit claim RPC.
-- This is intentionally service-role-only: clients learn their usage through the
-- authenticated Edge Function, never by reading the usage table directly.

create or replace function public.get_reminder_parse_usage(
  p_user_id uuid,
  p_timezone text
)
returns table (
  used_free_parses integer,
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
    raise exception 'A user is required to read parse usage.';
  end if;

  begin
    local_usage_date := timezone(p_timezone, now())::date;
  exception
    when invalid_parameter_value then
      raise exception 'Invalid timezone.';
  end;

  select free_parse_count
  into current_count
  from public.ai_parse_daily_usage
  where user_id = p_user_id and usage_date = local_usage_date;

  current_count := coalesce(current_count, 0);
  return query select current_count, greatest(0, daily_limit - current_count);
end;
$$;

revoke all on function public.get_reminder_parse_usage(uuid, text) from public;
grant execute on function public.get_reminder_parse_usage(uuid, text) to service_role;
