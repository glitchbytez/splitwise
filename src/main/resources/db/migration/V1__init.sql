-- Initial schema. Money is stored as BIGINT minor units (cents); never floating point.
-- Balances are derived from expenses/shares/settlements and are deliberately not stored.
-- gen_random_uuid() is built into PostgreSQL 13+.

create table users
(
    id            uuid primary key      default gen_random_uuid(),
    email         varchar(320) not null,
    display_name  varchar(100) not null,
    -- nullable: cleared when an account is deleted (anonymised)
    password_hash varchar(100),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    deleted_at    timestamptz,
    constraint uk_users_email unique (email),
    constraint ck_users_email_lower_case check (email = lower(email))
);

create table groups
(
    id          uuid primary key      default gen_random_uuid(),
    name        varchar(100) not null,
    description varchar(500),
    created_by  uuid         not null references users (id),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

create table group_members
(
    group_id  uuid        not null references groups (id),
    user_id   uuid        not null references users (id),
    role      varchar(10) not null,
    joined_at timestamptz not null default now(),
    left_at   timestamptz,
    primary key (group_id, user_id),
    constraint ck_group_members_role check (role in ('OWNER', 'MEMBER'))
);

create index idx_group_members_user on group_members (user_id);

create table expenses
(
    id           uuid primary key      default gen_random_uuid(),
    group_id     uuid         not null references groups (id),
    description  varchar(200) not null,
    amount_minor bigint       not null,
    currency     char(3)      not null default 'USD',
    expense_date date         not null,
    paid_by      uuid         not null references users (id),
    category     varchar(50),
    notes        varchar(1000),
    split_type   varchar(20)  not null,
    -- optimistic locking (@Version) to prevent lost updates on concurrent edits
    version      bigint       not null default 0,
    created_by   uuid         not null references users (id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    deleted_at   timestamptz,
    constraint ck_expenses_amount_positive check (amount_minor > 0),
    constraint ck_expenses_currency_usd check (currency = 'USD'),
    constraint ck_expenses_split_type check (split_type in ('EQUAL', 'EXACT', 'PERCENTAGE'))
);

create index idx_expenses_group_date on expenses (group_id, expense_date desc);

create table expense_shares
(
    id            uuid primary key default gen_random_uuid(),
    expense_id    uuid   not null references expenses (id) on delete cascade,
    user_id       uuid   not null references users (id),
    share_minor   bigint not null,
    -- integer basis points (3333 = 33.33%); only set for PERCENTAGE splits
    percentage_bp integer,
    -- the unique index leads with expense_id, so it also serves lookups by expense_id
    constraint uk_expense_shares_expense_user unique (expense_id, user_id),
    constraint ck_expense_shares_share_non_negative check (share_minor >= 0),
    constraint ck_expense_shares_percentage_range check (percentage_bp between 0 and 10000)
);

create table settlements
(
    id           uuid primary key     default gen_random_uuid(),
    group_id     uuid        not null references groups (id),
    from_user    uuid        not null references users (id),
    to_user      uuid        not null references users (id),
    amount_minor bigint      not null,
    settled_at   timestamptz not null default now(),
    created_by   uuid        not null references users (id),
    deleted_at   timestamptz,
    constraint ck_settlements_amount_positive check (amount_minor > 0),
    constraint ck_settlements_not_self check (from_user <> to_user)
);

create index idx_settlements_group on settlements (group_id);

create table activity_log
(
    id          uuid primary key     default gen_random_uuid(),
    group_id    uuid        not null references groups (id),
    actor_id    uuid        not null references users (id),
    type        varchar(50) not null,
    entity_type varchar(50),
    entity_id   uuid,
    payload     jsonb,
    created_at  timestamptz not null default now()
);

create index idx_activity_log_group_created on activity_log (group_id, created_at desc);
