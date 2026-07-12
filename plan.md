# ChargeSquare Case Study — Plan

> Junior Backend Developer take-home. Hedef: START → STOP → BILL → SETTLE akışını
> 2 servisli, doğru ve temiz bir dilimde uçtan uca çalıştırmak.
> İlke: **çalışan ve iyi açıklanmış yazılım > over-engineering.**

---

## 1. Kesinleşen Kararlar (DESIGN.md'ye birer cümleyle girecek)

| Karar | Seçim | Gerekçe (tek cümle) |
|---|---|---|
| Stack | Java 21 + Spring Boot 3 (Maven) | Şirketin house stack'i; en rahat savunulacak seçenek |
| Servis sayısı | 2 — Station + Session (wallet Session içinde modül) | Dokümanın önerdiği default; dilimi küçük tutup doğruluğa zaman ayırır |
| Veritabanı | PostgreSQL, tek shared DB | En basit, açıkça çalışan çözüm; container-friendly |
| Şema | Flyway migration + seed | Temiz checkout'tan sıfırdan kurulur, restart'a dayanır |
| Servis iletişimi | Sync REST (Session → Station) | Zorunlu; wallet aynı serviste in-process çağrı |
| Tarife | Start anında snapshot | Oturum ortasında fiyat değişirse müşteri start'taki fiyatı öder |
| Para | `BigDecimal` + DB'de `NUMERIC(12,2)`, `HALF_UP` yuvarlama | Float hatası yok; sonuç 2 hane |
| Yetersiz bakiye | Stop'a izin ver, bakiye negatife düşebilir | Enerji zaten verildi; connector da serbest kalır |
| Dependency down | Fail-fast, temiz 503 + JSON error body | Retry/fallback yok; trade-off DESIGN.md'de |
| Repo | Monorepo | Take-home için en basit; tek clone tek compose |

## 2. Bilerek Yapılmayacaklar (doküman cezalandırıyor)

- ❌ Retry / backoff / idempotency makinesi (sadece paragraf yazılacak)
- ❌ Broker, saga, exactly-once, event altyapısı
- ❌ Cache, rate limiting, service mesh, gateway
- ❌ 3. servis (Wallet Service)
- ❌ React admin paneli (security tasarımı **yazı olarak** verilecek)

---

## 3. Repo Yapısı (hedef)

```
chargesquare-case-study/
  README.md
  DESIGN.md                # kararlar + 2 reasoning paragrafı + SECURITY bölümü
  .env.example
  docker-compose.yml
  station-service/
    Dockerfile
    src/...
  session-service/
    Dockerfile
    src/...
  k8s/
    station-deployment.yaml
    station-service.yaml
    session-deployment.yaml
    session-service.yaml
    configmap.yaml
  .github/workflows/ci.yml
```

---

## 4. İş Sırası ve Alt Görevler

### Faz 0 — İskelet ve Altyapı Temeli ✅
- [x] 0.1 Git repo başlat, `.gitignore` (Java/Maven/IDE/.env)
- [x] 0.2 `station-service` Spring Boot projesi (web, data-jpa, postgres, flyway, actuator, validation)
- [x] 0.3 `session-service` Spring Boot projesi (aynı bağımlılıklar)
- [x] 0.4 `docker-compose.yml`: postgres + 2 servis; tüm config environment'tan
- [x] 0.5 `.env.example`: DB URL/credentials, servis portları, `STATION_SERVICE_URL`
- [x] 0.6 Her iki serviste `/health` endpoint'i (actuator) ayağa kalksın
- [x] **Kontrol:** `docker compose up` → 2 servis + DB ayakta, health'ler 200 ✓

### Faz 1 — Station Service ✅
- [x] 1.1 Flyway migration V1: `stations`, `tariffs`, `connectors` tabloları
      (connector: type, power_kw, status AVAILABLE/OCCUPIED, tariff_id FK) — invariant'lar CHECK constraint ile
- [x] 1.2 Seed migration V2: 1 istasyon, 2 connector, 1 tarife (8.50/kWh + 2.00 start fee, TRY)
- [x] 1.3 Entity (davranışlı: occupy/release) + Repository katmanı (fetch join, pessimistic lock)
- [x] 1.4 `GET /connectors/{id}` → status + tariff (404: CONNECTOR_NOT_FOUND)
- [x] 1.5 `GET /stations/{id}/connectors` → connector listesi (404: STATION_NOT_FOUND)
- [x] 1.6 `POST /connectors/{id}/occupy` → AVAILABLE ise OCCUPIED, değilse 409 CONNECTOR_OCCUPIED
- [x] 1.7 `POST /connectors/{id}/release` → AVAILABLE (idempotent mirror)
- [x] 1.8 Ortak JSON error body: `{ "error": "CODE", "message": "..." }` (@RestControllerAdvice)
- [x] 1.9 Key aksiyonlarda log (occupy/release)
- [x] **Kontrol:** curl ile 200 / 404 / 409 / 400 senaryoları doğrulandı ✓

### Faz 2 — Session Service (işin kalbi) ✅
- [x] 2.1 Flyway migration: `users`, `wallets`, `sessions` (+ tarife snapshot + wallet_balance_after kolonları, CHECK constraint'ler)
- [x] 2.2 Seed: user 7 + wallet 500.00 TRY; session id'leri 100'den başlıyor
- [x] 2.3 Station HTTP client (RestClient; base URL env'den; connect/read timeout'lu; hata → typed exception)
- [x] 2.4 **START — `POST /sessions`** (validation, wallet fail-fast, GET connector, 404/409 → session yok, occupy, ACTIVE + snapshot, 201, log, 503)
- [x] 2.5 **STOP — `POST /sessions/{id}/stop`** (404/409 guard, cost HALF_UP, wallet debit negatife izin, COMPLETED, release tx içinde, 200 receipt, log)
- [x] 2.6 `GET /sessions/{id}` → receipt shape
- [x] 2.7 `GET /users/{userId}/sessions` → dizi
- [x] 2.8 Ortak error handler + key aksiyon logları
- [x] **Kontrol:** walkthrough — start → OCCUPIED → **108.25** → bakiye **391.75** → AVAILABLE; tüm guard'lar + Station-down 503 doğrulandı ✓

### Faz 3 — Testler
- [ ] 3.1 Cost hesabı unit testi: 12.5 kWh × 8.50 + 2.00 = 108.25 (worked example)
- [ ] 3.2 Yuvarlama kenar testi (ör. 3 haneli ara sonuç → HALF_UP 2 hane)
- [ ] 3.3 Lifecycle integration testi: start → stop → COMPLETED + bakiye düştü
      (Station client mock/stub; DB için Testcontainers veya H2 — birini seç, README'de not)
- [ ] 3.4 Invalid case testleri: occupied'a start → 409; double-stop → 409; negatif energyKwh → 400
- [ ] 3.5 Station guard testi: occupy on OCCUPIED → 409
- [ ] **Kontrol:** `mvn test` yeşil

### Faz 4 — Containerization & Deploy Artefaktları
- [ ] 4.1 `station-service/Dockerfile` (multi-stage: build + slim runtime)
- [ ] 4.2 `session-service/Dockerfile`
- [ ] 4.3 compose'u finalize et: healthcheck'ler, `depends_on`, env wiring
- [ ] 4.4 **Temiz clone provası:** repo'yu boş klasöre klonla → `docker compose up` → tüm akış curl ile çalışıyor
- [ ] 4.5 k8s manifestleri: 2 Deployment + 2 Service + 1 ConfigMap
      (ConfigMap'te gerçek config: `STATION_SERVICE_URL` vb.; Deployment `envFrom` ile okur)
- [ ] 4.6 `kubectl apply --dry-run=client -f k8s/` doğrulaması (README'ye not düş)
- [ ] 4.7 `.github/workflows/ci.yml`: push'ta build + test + docker image build (push yok)
- [ ] **Kontrol:** dry-run temiz, CI yaml'ı geçerli

### Faz 5 — Dokümantasyon
- [ ] 5.1 **README.md**
      - [ ] Tek komut çalıştırma: `docker compose up --build`
      - [ ] 2–3 curl örneği: start → stop uçtan uca + bir reject case
      - [ ] Ana endpoint tablosu
      - [ ] Stack ve neden (tek cümle)
      - [ ] Testler nasıl koşulur
      - [ ] Para yaklaşımı notu (BigDecimal + HALF_UP, tek cümle)
      - [ ] Varsayımlar (ör. energyKwh'i meter yerine client bildirir — simülasyon)
      - [ ] "Time spent / What I'd do next" bölümü
      - [ ] Hangi opsiyonelleri yaptım/yapmadım (upfront beyan)
- [ ] 5.2 **DESIGN.md**
      - [ ] Karar tablosu (bkz. bölüm 1) — her karar 1 cümle gerekçe
      - [ ] **Paragraf 1 — Idempotent retries:** Stop iki kez gelirse ne bozulur
            (double-charge), state guard bunu nasıl kesiyor, idempotency key ile nasıl
            daha sağlam olurdu
      - [ ] **Paragraf 2 — Stuck connector / partial failure:** occupy başarılı ama
            session create başarısız olursa connector OCCUPIED kalır; timeout/retry/
            cleanup job seçenekleri ve trade-off'ları
      - [ ] Dependency-down trade-off'u (fail-fast seçtik, neden)
- [ ] 5.3 **SECURITY bölümü (DESIGN.md içinde, design-only)**
      - [ ] Auth: login endpoint'i JWT üretir; `Authorization: Bearer <token>`; doğrulama
      - [ ] RBAC: VIEWER (okuma) / ADMIN (start-stop, top-up, internal occupy/release);
            rol token claim'inde — trade-off'u belirt
      - [ ] 401 (anonim) vs 403 (yetkisiz); enforcement backend'de
      - [ ] Secrets: JWT key + DB credentials env/k8s Secret'tan; asla commit yok
      - [ ] Client-side validation'a neden güvenilmez (1 cümle) + CORS yaklaşımı (1 cümle)
      - [ ] Audit log planı: kim start/stop etti, top-up, failed login — timestamp + actor id;
            neden önemli (1 cümle) + 1 örnek log satırı
      - [ ] "Refresh token / OAuth / rate limiting'i ileride nasıl eklerdim" (birkaç cümle)
- [ ] **Kontrol:** README adımlarını temiz clone'da birebir uygula, hepsi tutuyor mu

### Faz 6 — Son Kontrol (Acceptance Criteria Taraması)
- [ ] 6.1 PDF §3.9'daki her kutuyu tek tek işaretle:
      - [ ] Temiz checkout'tan DB sıfırdan kuruluyor
      - [ ] Seed: ≥1 istasyon, ≥2 connector + tarife, ≥1 user + bakiye
      - [ ] Restart sonrası veri duruyor
      - [ ] Happy path: 201 start (OCCUPIED + ACTIVE + snapshot), stop hesap + settle + release
      - [ ] Read endpoint'leri doğru state dönüyor
      - [ ] Guard'lar: 404 / 409 occupied / 409 non-active / 400 validation
      - [ ] Connector status ↔ session state tutarlı
      - [ ] Para decimal-safe, 2 hane
      - [ ] Session → Station gerçek network çağrısı
      - [ ] Health endpoint'leri, env config, .env.example
      - [ ] Secret commit edilmemiş
      - [ ] Key aksiyonlar loglanıyor
      - [ ] 3 test kategorisi mevcut (cost, lifecycle, invalid case)
- [ ] 6.2 Commit geçmişi doğal ve anlamlı mı (tek dev commit değil)
- [ ] 6.3 Teslim: repo linki (veya zip) + upfront kapsam beyanı

---

## 5. Zaman Bütçesi (kılavuz: 8–16 saat)

| Faz | Tahmin |
|---|---|
| 0 — İskelet | 1–1.5 sa |
| 1 — Station | 1.5–2 sa |
| 2 — Session | 3–4 sa |
| 3 — Testler | 1.5–2 sa |
| 4 — Infra | 1.5–2 sa |
| 5 — Dokümantasyon | 1–1.5 sa |
| 6 — Son kontrol | 0.5 sa |

> Zaman daralırsa kırpma sırası: k8s'i dry-run notuyla bırak → stretch hiçbir şeye
> girme → README'de "what I'd do next" ile dürüstçe belgele. (Doküman bunu açıkça ödüllendiriyor.)
