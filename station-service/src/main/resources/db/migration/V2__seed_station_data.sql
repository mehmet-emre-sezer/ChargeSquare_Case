-- Başlangıç verisi: bir istasyon, bir tarifeye bağlı iki connector.
-- Id'ler ve fiyatlar keyfi örnek değerlerdir; README ve testlerle tutarlı tutulur.

insert into tariffs (id, price_per_kwh, start_fee, currency) values
    (5, 8.50, 2.00, 'TRY');

insert into stations (id, name) values
    (1, 'ChargeSquare Kadikoy');

insert into connectors (id, station_id, tariff_id, type, power_kw, status) values
    (10, 1, 5, 'CCS2-DC', 60, 'AVAILABLE'),
    (11, 1, 5, 'Type2-AC', 22, 'AVAILABLE');

-- Sequence'leri yukarıdaki açık id'lerin ilerisine taşı ki sonraki insert'ler çakışmasın.
select setval('station.tariffs_id_seq',    (select max(id) from tariffs));
select setval('station.stations_id_seq',   (select max(id) from stations));
select setval('station.connectors_id_seq', (select max(id) from connectors));
