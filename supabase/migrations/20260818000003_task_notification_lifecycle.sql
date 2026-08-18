-- Keep completion and reminder lifecycle server-owned for every client.
-- A completed task must never be claimed by the scheduler. Reopening it only
-- re-enables delivery when its reminder is still in the future.

create or replace function public.manage_task_notification_lifecycle()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Completing a task cancels only work that has not reached a terminal
  -- dispatch state. A send that has already succeeded remains historically sent.
  if old.completed = false and new.completed = true then
    if old.notification_state in ('pending', 'failed', 'processing') then
      new.notification_state := 'cancelled';
    end if;
    return new;
  end if;

  -- Reopening a previously cancelled task restores delivery only before its
  -- chosen reminder time. Reopening an overdue task requires an explicit
  -- reschedule, preventing surprise stale notifications.
  if old.completed = true and new.completed = false then
    if old.notification_state = 'cancelled' then
      new.notification_state := case when new.notify_at > now() then 'pending' else 'cancelled' end;
    end if;
    return new;
  end if;

  -- A pre-dispatch reminder rescheduled while still open gets a fresh pending
  -- state. Terminal sent tasks deliberately remain sent: their per-device
  -- delivery records are immutable for this one-time MVP task occurrence.
  if new.completed = false
    and new.notify_at is distinct from old.notify_at
    and old.notification_state in ('pending', 'failed', 'cancelled') then
    new.notification_state := case when new.notify_at > now() then 'pending' else 'cancelled' end;
  end if;

  return new;
end;
$$;

drop trigger if exists tasks_manage_notification_lifecycle on public.tasks;
create trigger tasks_manage_notification_lifecycle
before update on public.tasks
for each row execute function public.manage_task_notification_lifecycle();
