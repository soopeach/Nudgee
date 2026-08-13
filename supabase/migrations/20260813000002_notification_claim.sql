-- Atomically claim due tasks so concurrent scheduler invocations cannot send twice.
create or replace function public.claim_due_tasks(p_limit integer default 50)
returns setof public.tasks
language sql
security definer
set search_path = public
as $$
  with candidates as (
    select id
    from public.tasks
    where completed = false
      and notify_at <= now()
      and notification_state in ('pending', 'failed')
    order by notify_at asc
    for update skip locked
    limit least(greatest(coalesce(p_limit, 50), 1), 100)
  )
  update public.tasks as task
  set notification_state = 'processing', updated_at = now()
  from candidates
  where task.id = candidates.id
  returning task.*;
$$;

revoke all on function public.claim_due_tasks(integer) from public, anon, authenticated;
