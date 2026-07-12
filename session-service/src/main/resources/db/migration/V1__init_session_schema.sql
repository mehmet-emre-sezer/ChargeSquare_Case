-- Session Service şeması: users, wallets ve sessions.
-- Constraint'ler her zaman doğru olması gereken invariant'ları kodlar (geçerli status, para ölçekleri).

create table users (
    id   bigserial primary key,
    name varchar(120) not null
);

create table wallets (
    id       bigserial     primary key,
    user_id  bigint        not null unique references users (id),
    balance  numeric(12, 2) not null,
    currency varchar(3)     not null
);

create table sessions (
    id            bigserial   primary key,
    user_id       bigint      not null,
    connector_id  bigint      not null,
    -- Oturum yaşam döngüsü küçük ve kapalı bir küme.
    status        varchar(20) not null check (status in ('ACTIVE', 'COMPLETED')),
    started_at    timestamptz not null,
    ended_at      timestamptz,
    energy_kwh    numeric(12, 3),
    cost          numeric(12, 2),
    -- Bu oturum ayarlandıktan (settle) sonraki cüzdan bakiyesi — makbuz için snapshot'lanır.
    wallet_balance_after numeric(12, 2),
    -- Tarife, oturum başında snapshot'lanır; sonradan fiyat değişse bile bu oturumu etkilemez.
    price_per_kwh numeric(12, 2) not null,
    start_fee     numeric(12, 2) not null,
    currency      varchar(3)     not null
);

-- Bir kullanıcının oturum geçmişi user_id üzerinden sorgulanır.
create index idx_sessions_user on sessions (user_id);
