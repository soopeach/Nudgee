-- Nudgee notification scheduler trigger
--
-- Prerequisite:
--   1. Deploy an Edge Function named `dispatch-notifications`.
--   2. Create these two secrets in Supabase Vault (Dashboard > Vault):
--      - nudgee_project_url: https://<PROJECT_REF>.supabase.co
--      - nudgee_scheduler_key: <SCHEDULER_SECRET_KEY>
--
-- The service-role key is intentionally never written into this file or the
-- cron command. The cron job reads it from Vault at execution time.

create extension if not exists pg_cron;
create extension if not exists pg_net;
create extension if not exists supabase_vault with schema vault;

-- Make this script safe to run again during setup.
do $cleanup$
begin
  perform cron.unschedule(jobid)
  from cron.job
  where jobname = 'nudgee-dispatch-notifications';
end;
$cleanup$;

select cron.schedule(
  'nudgee-dispatch-notifications',
  '* * * * *',
  $job$
  select net.http_post(
    url := (
      select decrypted_secret
      from vault.decrypted_secrets
      where name = 'nudgee_project_url'
    ) || '/functions/v1/dispatch-notifications',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-nudgee-cron-secret', (
        select decrypted_secret
        from vault.decrypted_secrets
        where name = 'nudgee_scheduler_key'
      )
    ),
    body := jsonb_build_object('trigger', 'cron')
  );
  $job$
);

-- Verify the job is registered and active.
select jobid, jobname, schedule, active
from cron.job
where jobname = 'nudgee-dispatch-notifications';

-- To remove it later:
-- select cron.unschedule(jobid)
-- from cron.job
-- where jobname = 'nudgee-dispatch-notifications';
