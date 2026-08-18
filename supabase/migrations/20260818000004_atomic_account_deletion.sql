-- Nudgee account deletion must be atomic: either every account-owned record
-- and the Auth identity are removed, or PostgreSQL rolls the whole operation
-- back. Only the service-role Edge Function may invoke this RPC.

create or replace function public.delete_nudgee_account(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if p_user_id is null then
    raise exception 'A user is required to delete an account.';
  end if;

  -- Keep these explicit even though the auth foreign keys cascade. This makes
  -- the privacy boundary reviewable as Nudgee-owned tables evolve.
  delete from public.notification_deliveries where user_id = p_user_id;
  delete from public.device_tokens where user_id = p_user_id;
  delete from public.ai_parse_credit_events where user_id = p_user_id;
  delete from public.ai_parse_credit_balances where user_id = p_user_id;
  delete from public.ai_parse_daily_usage where user_id = p_user_id;
  delete from public.profiles where user_id = p_user_id;
  delete from public.tasks where user_id = p_user_id;

  delete from auth.users where id = p_user_id;
  if not found then
    raise exception 'Account not found.';
  end if;
end;
$$;

revoke all on function public.delete_nudgee_account(uuid) from public;
grant execute on function public.delete_nudgee_account(uuid) to service_role;
