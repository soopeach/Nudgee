-- Snoozing creates a new delivery occurrence for the same reminder. Delivery
-- history remains immutable per occurrence instead of being overwritten.

alter table public.tasks
  add column if not exists notification_occurrence integer not null default 0
  check (notification_occurrence >= 0);

alter table public.notification_deliveries
  add column if not exists occurrence integer not null default 0
  check (occurrence >= 0);

drop index if exists public.notification_deliveries_task_device_channel_idx;
create unique index if not exists notification_deliveries_task_device_channel_occurrence_idx
  on public.notification_deliveries (task_id, device_token_id, channel, occurrence);
