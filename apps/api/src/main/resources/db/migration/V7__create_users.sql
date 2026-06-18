create table users (
    id uuid primary key,
    email text not null,
    normalized_email text not null unique,
    password_hash text not null,
    display_name text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint users_email_not_blank check (length(trim(email)) > 0),
    constraint users_normalized_email_not_blank check (length(trim(normalized_email)) > 0),
    constraint users_password_hash_not_blank check (length(trim(password_hash)) > 0),
    constraint users_display_name_not_blank check (length(trim(display_name)) > 0)
);
