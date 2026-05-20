create table outbox (
    id          bigserial   primary key,
    login       varchar(64) not null,
    kind        varchar(64) not null,
    message     text        not null,
    created_at  timestamptz not null default now(),
    sent_at     timestamptz,
    attempts    integer     not null default 0
);

create index outbox_pending_idx on outbox (created_at) where sent_at is null;
