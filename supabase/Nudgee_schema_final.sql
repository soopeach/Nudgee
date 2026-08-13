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

commit;
