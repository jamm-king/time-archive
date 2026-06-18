alter table users
    add column role text not null default 'USER';

alter table users
    add constraint users_role_valid check (role in ('USER', 'ADMIN'));
