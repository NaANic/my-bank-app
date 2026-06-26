alter table notification add column event_id varchar(64);
update notification set event_id = gen_random_uuid()::text where event_id is null;
alter table notification alter column event_id set not null;
alter table notification add constraint notification_event_id_uniq unique (event_id);
