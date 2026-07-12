-- Başlangıç verisi: bir kullanıcı ve 500.00 TRY bakiyeli cüzdanı (keyfi örnek değerler).

insert into users (id, name) values
    (7, 'Demo Driver');

insert into wallets (id, user_id, balance, currency) values
    (1, 7, 500.00, 'TRY');

-- Sequence'leri açık id'lerin ilerisine taşı ki sonraki insert'ler çakışmasın.
select setval('session.users_id_seq',   (select max(id) from users));
select setval('session.wallets_id_seq', (select max(id) from wallets));

-- Oturum id'leri 100'den başlasın — spec'teki örnek makbuzlarla (sessionId 100) tutarlı olsun diye.
alter sequence session.sessions_id_seq restart with 100;
