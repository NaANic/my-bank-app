-- Structured outbox: store event fields instead of pre-rendered text,
-- add a status for atomic claim (FOR UPDATE SKIP LOCKED) and an eventId for
-- idempotent consumption downstream.
alter table outbox drop column message;
alter table outbox add column event_id uuid;
alter table outbox add column amount numeric(19, 2);
alter table outbox add column status varchar(16) not null default 'NEW';
alter table outbox add column processing_at timestamptz;

update outbox set event_id = gen_random_uuid() where event_id is null;
alter table outbox alter column event_id set not null;
alter table outbox add constraint outbox_event_id_uniq unique (event_id);

drop index if exists outbox_pending_idx;
create index outbox_status_new_idx on outbox (id) where status = 'NEW';
