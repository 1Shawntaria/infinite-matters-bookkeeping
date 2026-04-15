alter table app_users
    add column password_hash varchar(255);

update app_users
set password_hash = '{noop}temporary-password'
where password_hash is null;

alter table app_users
    alter column password_hash set not null;
