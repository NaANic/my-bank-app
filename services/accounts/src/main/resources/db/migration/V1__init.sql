create table account (
    login      varchar(64)  primary key,
    first_name varchar(100),
    last_name  varchar(100),
    dob        date,
    balance    numeric(19, 2) not null default 0
);

insert into account (login, first_name, last_name, dob, balance) values
    ('alice', 'Alice', 'Andreeva', date '1990-04-12', 12500.00),
    ('bob',   'Bob',   'Borisov',  date '1985-09-25',  7300.00);
