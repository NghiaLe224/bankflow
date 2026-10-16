alter table users
    add column role varchar(20);

update users
set role = 'USER'
where role is null;

alter table users
    alter column role set not null;

alter table users
    add constraint ck_users_role
        check (role in ('USER', 'ADMIN'));
