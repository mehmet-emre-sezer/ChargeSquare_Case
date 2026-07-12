-- Station Service şeması: stations, tariffs ve connectors (EVSE).
-- Constraint'ler her zaman doğru olması gereken invariant'ları kodlar (negatif olmayan para, geçerli status).

create table tariffs (
    id            bigserial primary key,
    price_per_kwh numeric(12, 2) not null check (price_per_kwh >= 0),
    start_fee     numeric(12, 2) not null default 0 check (start_fee >= 0),
    currency      varchar(3)     not null
);

create table stations (
    id   bigserial primary key,
    name varchar(120) not null
);

create table connectors (
    id         bigserial   primary key,
    station_id bigint      not null references stations (id),
    tariff_id  bigint      not null references tariffs (id),
    type       varchar(40) not null,
    power_kw   integer     not null check (power_kw > 0),
    -- Status küçük ve kapalı bir küme — hatalı değerler asla kalıcı olamasın diye burada guard'lanır.
    status     varchar(20) not null check (status in ('AVAILABLE', 'OCCUPIED'))
);

-- Connector'lar neredeyse her zaman sahibi olan istasyon üzerinden sorgulanır.
create index idx_connectors_station on connectors (station_id);
