-- Nudgee final schema cleanup + RLS hardening
-- Supabase SQL Editor에서 이 파일 하나만 실행하세요.
-- 전제: tasks, device_tokens, notification_deliveries, profiles 테이블이 이미 존재해야 합니다.

begin;

-- 1) notification_state를 발송 상태의 단일 기준으로 사용
do $func$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'tasks' and column_name = 'notified'
  ) then
    execute $sql$update public.tasks
      set notification_state = 'sent'
      where notified = true and notification_state = 'pending'$sql$;
    alter table public.tasks drop column notified;
  end if;
end;
$func$;

alter table public.tasks enable row level security;
alter table public.device_tokens enable row level security;

-- 2) tasks: 로그인한 사용자는 본인 데이터만 접근
drop policy if exists "Users can read own tasks" on public.tasks;
create policy "Users can read own tasks" on public.tasks
for select to authenticated using (user_id = auth.uid());

drop policy if exists "Users can create own tasks" on public.tasks;
create policy "Users can create own tasks" on public.tasks
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "Users can update own tasks" on public.tasks;
create policy "Users can update own tasks" on public.tasks
for update to authenticated
using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "Users can delete own tasks" on public.tasks;
create policy "Users can delete own tasks" on public.tasks
for delete to authenticated using (user_id = auth.uid());

-- 3) device_tokens: 로그인한 사용자는 본인 디바이스 토큰만 관리
drop policy if exists "Users can read own device tokens" on public.device_tokens;
create policy "Users can read own device tokens" on public.device_tokens
for select to authenticated using (user_id = auth.uid());

drop policy if exists "Users can register own device tokens" on public.device_tokens;
create policy "Users can register own device tokens" on public.device_tokens
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "Users can update own device tokens" on public.device_tokens;
create policy "Users can update own device tokens" on public.device_tokens
for update to authenticated
using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "Users can delete own device tokens" on public.device_tokens;
create policy "Users can delete own device tokens" on public.device_tokens
for delete to authenticated using (user_id = auth.uid());

-- A push token can belong to only one active user. This handles browser
-- account switches without allowing the client to update another user's row.
with ranked_tokens as (
  select id, row_number() over (
    partition by platform, token
    order by last_seen_at desc nulls last, updated_at desc nulls last, created_at desc, id desc
  ) as duplicate_rank
  from public.device_tokens
  where is_active = true
)
update public.device_tokens as device_token
set is_active = false, updated_at = now()
from ranked_tokens
where device_token.id = ranked_tokens.id and ranked_tokens.duplicate_rank > 1;

create unique index if not exists device_tokens_active_platform_token_idx
on public.device_tokens (platform, token) where is_active = true;

-- Tabs share an installation_id via localStorage. A browser profile stays a
-- separate notification target, while FCM token rotation retires its old token.
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
returns void language plpgsql security definer set search_path = public
as $claim$
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

  if p_platform = 'web' and p_installation_id is not null then
    update public.device_tokens
    set is_active = false, updated_at = now()
    where user_id = requester_id
      and platform = 'web'
      and installation_id = p_installation_id
      and token <> trim(p_token)
      and is_active = true;
  end if;

  select id into current_token_id from public.device_tokens
  where user_id = requester_id and platform = p_platform and token = trim(p_token)
  order by created_at desc limit 1;

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
        last_seen_at = now(), device_name = p_device_name,
        app_version = p_app_version, updated_at = now()
    where id = current_token_id;
  end if;
end;
$claim$;

revoke all on function public.claim_device_token(text, text, text, text, uuid) from public;
grant execute on function public.claim_device_token(text, text, text, text, uuid) to authenticated;

-- 4) notification_deliveries: 클라이언트는 본인 기록 조회만 가능
alter table public.notification_deliveries enable row level security;
drop policy if exists "Users can read own notification deliveries" on public.notification_deliveries;
create policy "Users can read own notification deliveries" on public.notification_deliveries
for select to authenticated using (user_id = auth.uid());

-- 5) profiles: 본인 설정만 접근
alter table public.profiles enable row level security;
drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile" on public.profiles
for select to authenticated using (user_id = auth.uid());

drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile" on public.profiles
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile" on public.profiles
for update to authenticated
using (user_id = auth.uid()) with check (user_id = auth.uid());

-- 6) 데이터베이스 레벨 입력값 검증
alter table public.tasks drop constraint if exists tasks_title_length_check;
alter table public.tasks add constraint tasks_title_length_check
check (char_length(trim(title)) between 1 and 500);

alter table public.device_tokens drop constraint if exists device_tokens_platform_check;
alter table public.device_tokens add constraint device_tokens_platform_check
check (platform in ('web', 'ios', 'android', 'desktop'));

-- Include old row values in UPDATE/DELETE Realtime events.
alter table public.tasks replica identity full;

-- 7) Realtime publication: task changes are delivered to subscribed clients.
do $realtime$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'tasks'
  ) then
    alter publication supabase_realtime add table public.tasks;
  end if;
end;
$realtime$;

-- 8) Server-owned natural-language reminder allowance (10 free parses/day).
create table if not exists public.ai_parse_daily_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  usage_date date not null,
  free_parse_count integer not null default 0 check (free_parse_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, usage_date)
);

alter table public.ai_parse_daily_usage enable row level security;
create index if not exists ai_parse_daily_usage_date_idx on public.ai_parse_daily_usage (usage_date);

drop trigger if exists ai_parse_daily_usage_set_updated_at on public.ai_parse_daily_usage;
create trigger ai_parse_daily_usage_set_updated_at
before update on public.ai_parse_daily_usage
for each row execute function public.set_updated_at();

-- 9) Rewarded AI credits. Only trusted server callbacks may grant credits;
-- clients cannot read or mutate balances/events directly through RLS.
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

create or replace function public.grant_ai_parse_credits(
  p_user_id uuid,
  p_amount integer,
  p_reason text,
  p_provider text,
  p_provider_transaction_id text
)
returns table (granted boolean, balance integer)
language plpgsql security definer set search_path = public
as $grant_credit$
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
    from public.ai_parse_credit_balances b where b.user_id = p_user_id;
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
$grant_credit$;

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
language plpgsql security definer set search_path = public
as $parse_credit$
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
$parse_credit$;

create or replace function public.refund_reminder_parse_credit(
  p_user_id uuid,
  p_timezone text,
  p_credit_source text
)
returns table (
  remaining_free_parses integer,
  remaining_bonus_credits integer
)
language plpgsql security definer set search_path = public
as $refund_credit$
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
    where user_id = p_user_id and usage_date = local_usage_date and free_parse_count > 0;
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
  from public.ai_parse_credit_balances where user_id = p_user_id;
  return query select greatest(0, daily_limit - coalesce(current_free_count, 0)), coalesce(current_bonus, 0);
end;
$refund_credit$;

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
language plpgsql security definer set search_path = public
as $parse_usage$
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
$parse_usage$;

revoke all on function public.grant_ai_parse_credits(uuid, integer, text, text, text) from public;
revoke all on function public.consume_reminder_parse_credit(uuid, text) from public;
revoke all on function public.refund_reminder_parse_credit(uuid, text, text) from public;
revoke all on function public.get_reminder_parse_usage(uuid, text) from public;
grant execute on function public.grant_ai_parse_credits(uuid, integer, text, text, text) to service_role;
grant execute on function public.consume_reminder_parse_credit(uuid, text) to service_role;
grant execute on function public.refund_reminder_parse_credit(uuid, text, text) to service_role;
grant execute on function public.get_reminder_parse_usage(uuid, text) to service_role;

commit;
