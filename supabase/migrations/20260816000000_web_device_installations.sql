-- Keep one active FCM token per logical web-browser installation.
-- Tabs share localStorage and therefore share installation_id; separate browser
-- profiles intentionally have separate ids and remain separate push targets.

alter table public.device_tokens
  add column if not exists installation_id uuid;

create unique index if not exists device_tokens_active_web_installation_idx
  on public.device_tokens (user_id, platform, installation_id)
  where is_active = true and platform = 'web' and installation_id is not null;

drop function if exists public.claim_device_token(text, text, text, text);

create function public.claim_device_token(
  p_platform text,
  p_token text,
  p_device_name text default null,
  p_app_version text default null,
  p_installation_id uuid default null
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
  if requester_id is null then raise exception 'Unauthorized'; end if;
  if p_platform not in ('web', 'ios', 'android', 'desktop') then raise exception 'Unsupported device platform'; end if;
  if p_token is null or char_length(trim(p_token)) < 20 or char_length(p_token) > 4096 then raise exception 'Invalid device token'; end if;
  if p_platform <> 'web' and p_installation_id is not null then raise exception 'installation_id is only supported for web'; end if;

  perform pg_advisory_xact_lock(hashtextextended(p_platform || ':' || p_token, 0));
  if p_platform = 'web' and p_installation_id is not null then
    perform pg_advisory_xact_lock(hashtextextended('web-installation:' || requester_id::text || ':' || p_installation_id::text, 0));
  end if;

  update public.device_tokens
  set is_active = false, updated_at = now()
  where platform = p_platform and token = p_token and user_id <> requester_id and is_active = true;

  -- FCM can rotate a token. Retire the previous token from this exact browser
  -- installation without affecting a different browser profile/device.
  if p_platform = 'web' and p_installation_id is not null then
    update public.device_tokens
    set is_active = false, updated_at = now()
    where user_id = requester_id
      and platform = 'web'
      and installation_id = p_installation_id
      and token <> trim(p_token)
      and is_active = true;
  end if;

  select id into current_token_id
  from public.device_tokens
  where user_id = requester_id and platform = p_platform and token = trim(p_token)
  order by created_at desc
  limit 1;

  if current_token_id is null then
    insert into public.device_tokens (
      user_id, platform, token, installation_id, is_active, last_seen_at, device_name, app_version
    ) values (
      requester_id, p_platform, trim(p_token), p_installation_id, true, now(), p_device_name, p_app_version
    );
  else
    update public.device_tokens
    set is_active = true,
        installation_id = coalesce(p_installation_id, installation_id),
        last_seen_at = now(),
        device_name = p_device_name,
        app_version = p_app_version,
        updated_at = now()
    where id = current_token_id;
  end if;
end;
$$;

revoke all on function public.claim_device_token(text, text, text, text, uuid) from public;
grant execute on function public.claim_device_token(text, text, text, text, uuid) to authenticated;
