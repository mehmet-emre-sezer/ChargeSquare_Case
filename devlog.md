# Geliştirme Günlüğü (devlog)

ChargeSquare Junior Backend Developer Case Study — ilerleme kaydı.
Detaylı görev listesi için bkz. [plan.md](plan.md).

---

## Genel Durum

| Faz | Konu | Durum |
|-----|------|-------|
| 0 | İskelet & Altyapı | ✅ Tamam |
| 1 | Station Service | ✅ Tamam |
| 2 | Session Service (start/stop akışı) | ✅ Tamam |
| 3 | Testler | ⬜ Bekliyor |
| 4 | Docker / k8s / CI | ⬜ Bekliyor |
| 5 | README / DESIGN / SECURITY | ⬜ Bekliyor |
| 6 | Son kontrol (acceptance) | ⬜ Bekliyor |

**Stack:** Java 21 (Temurin) · Spring Boot 3.3.5 · Maven · PostgreSQL 16 · Flyway · Docker Compose

---

## Faz 0 — İskelet & Altyapı ✅

**Yapılanlar**
- Monorepo + `git init` + `.gitignore` (`.env` ignore'da, doğrulandı).
- İki Spring Boot servisi: `station-service` (port 8081), `session-service` (port 8082).
- Bağımlılıklar: web, data-jpa, validation, actuator, flyway-core, flyway-database-postgresql, postgresql, test.
- `docker-compose.yml`: tek paylaşımlı Postgres + 2 servis; DB healthcheck ile bağımlılık sıralaması.
- `.env.example`: tüm config ortam değişkenlerinden (DB, portlar, `STATION_SERVICE_URL`).
- Her serviste `/health` endpoint'i (actuator, liveness/readiness).

**Önemli tasarım kararları**
- **Tek DB içinde şema izolasyonu** (`station` / `session` şemaları) — böylece Flyway geçmiş tabloları çakışmıyor, temiz servis sınırı korunuyor.
- `ddl-auto: validate` — şema tamamen Flyway'in sorumluluğunda, Hibernate yalnızca doğrular.
- Secret yok: tüm kimlik bilgileri env/compose'tan, `.env` commit'lenmiyor.

**Doğrulama**
- `docker compose up --build` → DB + 2 servis ayağa kalktı.
- `GET /health` (8081 & 8082) → `200 {"status":"UP"}`.

**Commit:** `İskelet: iki Spring Boot servisi + docker-compose + Postgres`

---

## Faz 1 — Station Service ✅

**Yapılanlar**
- Flyway migration V1: `stations`, `tariffs`, `connectors` tabloları; invariant'lar CHECK constraint ile (negatif olmayan para, geçerli status).
- Flyway migration V2: seed — 1 istasyon, 2 connector, 1 tarife (`8.50 TRY/kWh + 2.00` start fee); sequence'ler `setval` ile ileri alındı.
- Davranışlı domain: `Connector.occupy()` / `release()`, `ConnectorStatus` enum — status dışarıdan set edilmiyor.
- Repository: fetch join ile N+1'siz okuma; occupy/release için pessimistic lock.
- Endpoint'ler:
  - `GET /connectors/{id}` → status + tariff (404 `CONNECTOR_NOT_FOUND`)
  - `GET /stations/{id}/connectors` → connector listesi (404 `STATION_NOT_FOUND`)
  - `POST /connectors/{id}/occupy` → 200 OCCUPIED / 409 `CONNECTOR_OCCUPIED`
  - `POST /connectors/{id}/release` → 200 AVAILABLE (idempotent)
- Ortak hata gövdesi `{ "error": ..., "message": ... }` — `@RestControllerAdvice` ile; beklenmeyen hatalar maskeleniyor (500 `INTERNAL_ERROR`).
- occupy/release loglanıyor.

**Önemli tasarım kararları** (DESIGN.md'ye taşınacak)
1. `/release` **idempotent** — zaten AVAILABLE ise hata değil; stop akışının gereksiz kırılmaması için.
2. Bilinmeyen istasyonda liste → **404 STATION_NOT_FOUND** (resource-oriented tutarlılık).
3. occupy/release'de **pessimistic lock** — iki eşzamanlı start'ın aynı connector'ı kapmasını engeller.

**Doğrulama (curl ile)**
| Senaryo | Sonuç |
|---|---|
| GET /connectors/10 | 200, tam shape (8.50 / 2.00 ondalıkları korunuyor) |
| GET /connectors/999 | 404 CONNECTOR_NOT_FOUND |
| GET /stations/1/connectors | 200, 2 connector |
| occupy (available) | 200 OCCUPIED |
| occupy (tekrar) | 409 CONNECTOR_OCCUPIED |
| release | 200 AVAILABLE |
| occupy 999 | 404 |
| GET /connectors/abc | 400 VALIDATION_ERROR |

**Ek not:** Tüm kod yorumları Türkçe'ye çevrildi; bundan sonra yorumlar Türkçe yazılıyor.

**Commit:** `feat: Station Service şema, seed, connector read'leri ve occupy/release`

---

## Faz 2 — Session Service ✅

**Yapılanlar**
- Flyway V1: `users`, `wallets`, `sessions` (status ACTIVE/COMPLETED CHECK; tarife snapshot kolonları; `wallet_balance_after`). V2 seed: user 7 + 500.00 TRY cüzdan; session id'leri 100'den.
- Domain: `SessionStatus` enum; `TariffSnapshot` (@Embeddable value object — cost hesabı `costFor()` burada, HALF_UP 2 hane); davranışlı `Session` (start factory + stop guard) ve `Wallet` (debit, negatife izin); `User`.
- `StationClient` (RestClient, base URL env'den, connect 2s / read 5s timeout): `getConnector` / `occupy` / `release`; Station yanıtları typed exception'a çevriliyor (404/409/unreachable).
- START `POST /sessions`: input validation → wallet fail-fast → GET connector → OCCUPIED/404 ise session yok → occupy → ACTIVE session + snapshot → 201.
- STOP `POST /sessions/{id}/stop`: oturumu kilitle + ACTIVE guard (double-stop dahil) → cost → wallet debit → COMPLETED → **release transaction içinde** → 200 receipt.
- Read'ler: `GET /sessions/{id}`, `GET /users/{userId}/sessions`.
- Ortak error handler: 404 / 409 / 400 / 503 / 500; key aksiyonlar loglanıyor. Clock enjekte (test için).

**Önemli tasarım kararları** (DESIGN.md'ye taşınacak)
1. **Tarife start'ta snapshot** — fiyat oturum ortasında değişse bile bu oturum etkilenmez.
2. **Yetersiz bakiye → stop'a izin, negatife düş** — enerji zaten verildi.
3. **STOP'ta release transaction içinde** — Station düşükse tüm işlem geri alınır, 503 döner; borç yazılmaz, connector kilitli kalmaz (temiz, tekrar denenebilir). Stuck-connector penceresi minimuma iner.
4. **START'ta cüzdan fail-fast doğrulaması** — faturalanamayacak oturum başlatılmaz (invariant koruması). Spec'in start guard listesine ek, ama onunla çelişmez.
5. **Money merkezi**: `BigDecimal` + `NUMERIC(12,2)`, yuvarlama tek yerde (`TariffSnapshot.costFor`).

**Doğrulama (curl + psql ile)**
- Happy path: 201 ACTIVE (snapshot 8.50/2.00) → connector OCCUPIED → stop cost **108.25**, bakiye **391.75**, COMPLETED → connector AVAILABLE.
- Read'ler doğru; double-stop 409; bilinmeyen connector 404; occupied'a start 409; eksik userId 400; negatif energyKwh 400; bilinmeyen session 404.
- Başarısız start'lar DB'de **session yaratmadı** (psql ile doğrulandı: sadece 100 + 101).
- **Station down → START = 503 STATION_UNAVAILABLE** (fail-fast).

**Commit:** `feat: Session Service start/stop akışı, tarife snapshot ve wallet settle`

---

## Çalışma Kuralları (hatırlatma)

- PDF spec'e **birebir** bağlılık.
- `clean-code-fullstack-agent-rules-final.md` ruleset'ine **birebir** bağlılık.
- Yorumlar **kısa, açıklayıcı ve Türkçe**.
- Commit'ler **conventional commits** (feat/fix/chore/docs/test/refactor), mesajlar Türkçe ve kısa.
- Commit'leri kullanıcı atıyor; ben her faz sonunda hazır komutu veriyorum.
- Bilerek yapılmayacaklar: retry/broker/saga/cache/rate-limit, 3. servis, admin panel (Stage 2 sadece tasarım yazısı).

---

## Sırada — Faz 3 (Testler)

- Cost hesabı unit testi: 12.5 kWh × 8.50 + 2.00 = 108.25 (worked example) + yuvarlama kenar durumu.
- Lifecycle integration testi: start → stop → COMPLETED + bakiye düştü (Station client stub/mock, DB için Testcontainers ya da H2 — birini seç, README'de not).
- Invalid case testleri: occupied'a start → 409; double-stop → 409; negatif energyKwh → 400.
- Station guard testi: occupy on OCCUPIED → 409.
