-- Nudgee: a push token belongs to one active user at a time.
-- This prevents a browser token from remaining active after an account switch.

-- Keep the newest registration for duplicate active tokens and preserve older
-- rows as inactive history. The partial unique index below then enforces the
-- invariant for future writes.
with ranked_tokens as (
  select
    id,
    row_number() over (
      partition by platform, token
      order by last_seen_at desc nulls last, updated_at desc nulls last, created_at desc, id desc
    ) as duplicate_rank
  from public.device_tokens
  where is_active = true
)
update public.device_tokens as device_token
set is_active = false,
    updated_at = now()
from ranked_tokens
where device_token.id = ranked_tokens.id
  and ranked_tokens.duplicate_rank > 1;

create unique index if not exists device_tokens_active_platform_token_idx
  on public.device_tokens (platform, token)
  where is_active = true;

create or replace function public.claim_device_token(
  p_platform text,
  p_token text,
  p_device_name text default null,
  p_app_version text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  requester_id uuid := auth.uid();
  current_token_id uuid;
begin
  if requester_id is null then
    raise exception 'Unauthorized';
  end if;

  if p_platform not in ('web', 'ios', 'android', 'desktop') then
    raise exception 'Unsupported device platform';
  end if;

  if p_token is null or char_length(trim(p_token)) < 20 or char_length(p_token) > 4096 then
    raise exception 'Invalid device token';
  end if;

  -- Serialize claims for the same platform/token so account switches cannot
  -- race into two active owners.
  perform pg_advisory_xact_lock(hashtextextended(p_platform || ':' || p_token, 0));

  update public.device_tokens
  set is_active = false,
      updated_at = now()
  where platform = p_platform
    and token = p_token
    and user_id <> requester_id
    and is_active = true;

  select id into current_token_id
  from public.device_tokens
  where user_id = requester_id
    and platform = p_platform
    and token = p_token
  order by created_at desc
  limit 1;

  if current_token_id is null then
    insert into public.device_tokens (
      user_id, platform, token, is_active, last_seen_at, device_name, app_version
    ) values (
      requester_id, p_platform, trim(p_token), true, now(), p_device_name, p_app_version
    );
  else
    update public.device_tokens
    set is_active = true,
        last_seen_at = now(),
        device_name = p_device_name,
        app_version = p_app_version,
        updated_at = now()
    where id = current_token_id;
  end if;
end;
$$;

revoke all on function public.claim_device_token(text, text, text, text) from public;
grant execute on function public.claim_device_token(text, text, text, text) to authenticated;
