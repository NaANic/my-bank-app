create table notification (
    id          bigserial primary key,
    login       varchar(64)  not null,
    kind        varchar(64)  not null,
    message     text         not null,
    created_at  timestamptz  not null default now()
);

create index notification_login_idx on notification (login);
