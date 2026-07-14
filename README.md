# ChargeSquare — EV Şarj Backend (Case Study)

Bir EV şarj backend'inin uçtan uca çalışan küçük ama eksiksiz bir dilimi: **oturumu başlat → durdur → tarifeye göre fiyatla → cüzdandan tahsil et.** İki servis ve paylaşımlı bir PostgreSQL üzerine kurulu.

Repoyu klonlayıp tek komutla ayağa kaldırabilir, tüm akışı `curl` ile deneyebilirsiniz.

---

## Mimari

Sorumlulukları net ayrılmış iki servis. Session Service, başlatma ve durdurma sırasında Station Service'e **gerçek bir senkron REST çağrısı** yapar.

```mermaid
flowchart LR
    Client["İstemci / curl / Postman"]

    subgraph Station["station-service :8081"]
        StationC["Station / Connector Controller'ları"]
        ConnectorService["ConnectorService"]
        StationDB[("PostgreSQL<br/>station şeması")]
    end

    subgraph Session["session-service :8082"]
        SessionC["Session / User Controller'ları"]
        SessionService["SessionService (+ cüzdan)"]
        StationClient["StationClient (RestClient)"]
        SessionDB[("PostgreSQL<br/>session şeması")]
    end

    Client -->|"GET /connectors/{id}"| StationC
    Client -->|"POST /sessions, /sessions/{id}/stop"| SessionC
    StationC --> ConnectorService --> StationDB
    SessionC --> SessionService --> SessionDB
    SessionService --> StationClient -->|"HTTP REST"| StationC
```

- **Station Service** — istasyonların, connector'ların (EVSE) ve tarifelerin tek doğruluk kaynağı. Connector durumunu (`AVAILABLE` / `OCCUPIED`) o tutar.
- **Session Service** — şarj oturumunun yaşam döngüsünü yönetir. Başlangıçta tarifenin bir kopyasını (snapshot) alır, durdurmada maliyeti hesaplar ve cüzdandan tahsil eder. Cüzdan, ayrı bir servis yerine bu servisin içinde bir modül olarak durur.

Diğer diyagramlar (başlatma / durdurma akışları, durum makineleri, hata akışı): [`diagrams/`](diagrams/).

---

## Teknoloji

| | |
|---|---|
| Dil / framework | **Java 21 · Spring Boot 3.3.5** |
| Veritabanı | **PostgreSQL 16** (tek paylaşımlı veritabanı, servis başına bir şema) |
| Migration | Flyway (şema sıfırdan kurulur, `ddl-auto: validate`) |
| Para | `BigDecimal` + `NUMERIC(12,2)`, 2 basamağa `HALF_UP` yuvarlama |
| Altyapı | Docker Compose · Kubernetes manifestleri · GitHub Actions CI |

Spring Boot'u **ilk kez** bu projede kullandım; ChargeSquare'in ana stack'i olduğu için bilinçli seçtim ve süreçte öğrenmeyi hedefledim. Kararları framework'ün kolaylıklarına değil, temiz kod ve katmanlı mimari ilkelerine (domain modeli, ince controller, izole persistence) dayandırdım. Tüm kararların gerekçeleri için: [DESIGN.md](DESIGN.md).

---

## Çalıştırma (tek komut)

```bash
cp .env.example .env          # yerel ayarlar; .env sürüm kontrolüne girmez
docker compose up --build     # Postgres + iki servis
```

Dockerfile'lar `eclipse-temurin-21` ile derler; dolayısıyla çalışan uygulama, bilgisayarınızdaki Java sürümü ne olursa olsun JDK 21 kullanır.

Servisler ayağa kalkınca sağlık kontrolü:

```bash
curl localhost:8081/health    # station-service
curl localhost:8082/health    # session-service
```

---

## Uçtan uca örnek

Başlangıç verisi: `10` numaralı connector `AVAILABLE`, tarife `8.50 TRY/kWh + 2.00` başlangıç ücreti, `7` numaralı kullanıcının bakiyesi `500.00`.

Uçlar korumalıdır (bkz. [Kimlik doğrulama](#kimlik-doğrulama)), o yüzden önce giriş yapıp token'ı isteklere ekliyoruz.

```bash
# 0) GİRİŞ — admin olarak token al
TOKEN=$(curl -s -X POST localhost:8082/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 1) BAŞLAT — 201 döner, connector OCCUPIED olur, tarifenin kopyası alınır
curl -X POST localhost:8082/sessions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"userId":7,"connectorId":10}'

# 2) DURDUR — maliyet = 12.5 * 8.50 + 2.00 = 108.25, cüzdan 500.00 -> 391.75
curl -X POST localhost:8082/sessions/100/stop \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"energyKwh":12.5}'

# 3) Makbuzu geri oku
curl localhost:8082/sessions/100 -H "Authorization: Bearer $TOKEN"

# 4) Hatalı durum — artık dolu olan connector'a başlatma 409 döner, oturum yaratmaz
curl -X POST localhost:8082/sessions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"userId":7,"connectorId":10}'
```

Bütün hatalar aynı gövde biçimini kullanır: `{ "error": "KOD", "message": "..." }`.

---

**Station Service** (`:8081`)

| Metot | Yol | Erişim | Açıklama |
|---|---|---|---|
| GET | `/connectors/{id}` | VIEWER/ADMIN | Connector durumu + tarife (bilinmiyorsa 404) |
| GET | `/stations/{id}/connectors` | VIEWER/ADMIN | Bir istasyonun connector'ları |
| POST | `/connectors/{id}/occupy` | ADMIN | OCCUPIED yap (zaten doluysa 409) — dahili |
| POST | `/connectors/{id}/release` | ADMIN | AVAILABLE yap (idempotent) — dahili |

**Session Service** (`:8082`)

| Metot | Yol | Erişim | Açıklama |
|---|---|---|---|
| POST | `/auth/login` | açık | Kullanıcı adı+şifre → JWT + rol |
| POST | `/sessions` | ADMIN | BAŞLAT — 201; 404/409/400 durumunda oturum yaratılmaz |
| POST | `/sessions/{id}/stop` | ADMIN | DURDUR + FATURALA + TAHSİL ET — 200 makbuz |
| GET | `/sessions/{id}` | VIEWER/ADMIN | Tek bir oturumu oku |
| GET | `/users/{userId}/sessions` | VIEWER/ADMIN | Bir kullanıcının oturumları |

`/health` uçları herkese açıktır. Durum kodları: `401` token yok/geçersiz · `403` yetersiz rol · `404` bilinmeyen connector/oturum · `409` dolu connector / aktif olmayan (veya ikinci kez) durdurma · `400` geçersiz istek · `503` Station Service'e ulaşılamıyor.

---

## Kimlik doğrulama

Basit bir JWT tabanlı erişim kontrolü (Stage 2, backend tarafı **implement edildi**). `POST /auth/login` geçerli kimlik bilgisinde rol taşıyan bir JWT üretir; token `Authorization: Bearer <token>` ile taşınır ve her iki serviste de doğrulanır.

**Demo kullanıcılar** (şifreler yalnızca demo amaçlıdır):

| Kullanıcı | Şifre | Rol | Yetki |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | okuma + başlat/durdur + dahili occupy/release |
| `viewer` | `viewer123` | VIEWER | yalnızca okuma |

- **Roller sunucuda zorunlu kılınır** (buton gizleyerek değil): token yoksa `401`, yetki yetmezse `403`.
- **Servisler arası:** internal `occupy`/`release` de ADMIN ister; Session Service, Station'a çağrılarında kendi ürettiği bir ADMIN servis token'ı taşır.
- JWT imzalama anahtarı `JWT_SECRET` ortam değişkeninden gelir (iki servis paylaşır), depoya konmaz.

Tam güvenlik modeli ve gerekçeler için [DESIGN.md](DESIGN.md#güvenlik).

---

## Testler

```bash
# servis başına (JDK 21 gerekir — aşağıdaki nota bakın)
mvn -f station-service/pom.xml test
mvn -f session-service/pom.xml test
```

20 test var: maliyet hesabı (örnek: `108.25`, artı yuvarlama durumları), başlat→durdur yaşam döngüsü, hatalı istekler (dolu connector'a başlatma 409, ikinci durdurma 409, eksik alan 400, bilinmeyen kayıt 404) ve kimlik doğrulama/yetki (login, token yok → 401, yetersiz rol → 403).

- Testler **gömülü ama gerçek bir PostgreSQL** (zonky) üzerinde koşar; yani `mvn test` için **Docker gerekmez.** Migration'lar üretimdekiyle birebir aynı çalışır.
- Testleri koşmak için **JDK 21 şarttır:** Mockito'nun kullandığı byte-code kütüphanesi daha yeni JDK sürümlerini desteklemiyor. Docker/Compose yolu bundan etkilenmez (içeride zaten 21 ile derler). CI de JDK 21'e sabitlenmiştir.

---

## Konfigürasyon

Tüm ayarlar ortam değişkenlerinden gelir; hiçbir şey koda gömülü değil, hiçbir secret depoya konmadı. Tam liste için [`.env.example`](.env.example) (veritabanı URL'i ve kimlik bilgileri, servis portları, `STATION_SERVICE_URL`, JWT imzalama anahtarı `JWT_SECRET`).

---

## Kubernetes

[`k8s/`](k8s/) altında sade manifestler var: bir `ConfigMap` ve `Secret`, bir Postgres `Deployment`/`Service`, ayrıca her servis için `/health` uçlarına bağlı readiness/liveness kontrolü olan birer `Deployment` + `Service`.

Manifestlerin tam doğrulaması çalışan bir cluster ister:

```bash
kubectl apply --dry-run=client -f k8s/
```

Bunu canlı bir cluster üzerinde çalıştıramadım; manifestleri geçerli YAML olarak ve beklenen kaynak türleriyle doğruladım. `Secret` DB kimlik bilgilerini ve JWT imzalama anahtarını tutar; değerler örnek (placeholder) değerlerdir — gerçek bir kurulumda bir secret yöneticisinden gelir, asla depodan değil.

---

## Varsayımlar

- **Enerji miktarını durdurma isteğinde istemci bildirir** (`energyKwh`); sayaç simüle edilmiştir, case metni buna izin veriyor.
- **Bakiye yetmezse tahsilata izin verilir ve cüzdan eksiye düşebilir.** Enerji zaten verildiği için durdurmayı reddetmek doğru olmaz; faturayı keser, bakiyenin sıfırın altına inmesine izin verir ve connector'ı yine serbest bırakırız.
- **`/release` idempotenttir:** zaten boşta olan bir connector'ı bırakmak bir şeyi bozmaz, böylece durdurma akışı bu adımda takılmaz.
- **Oturum süresi maliyeti etkilemez:** maliyet yalnızca `enerji × fiyat + başlangıç ücreti`; zaman bilgileri sadece makbuz içindir.
- **Tek paylaşımlı veritabanı, servis başına bir şema:** ikinci bir veritabanı kurmadan temiz bir sahiplik sınırı sağlar.
- **Başlatma; connector'ı (Station üzerinden) ve cüzdanı doğrular, kullanıcı kaydını ayrıca doğrulamaz** — case metnindeki başlatma kurallarıyla uyumlu (bilinmeyen connector 404, dolu 409, geçersiz istek 400).

---

## Ne kodlandı, ne yalnızca yazıya döküldü

**Kodlandı:** tüm başlat → durdur → faturala → tahsil et akışı, bütün durum kontrolleri, kayan noktadan kaçınan maliyet hesabı, gerçek Session → Station ağ çağrısı, gerçek veritabanı kalıcılığı + başlangıç verisi, JWT tabanlı kimlik doğrulama + rol tabanlı erişim (Stage 2 backend), testler, Dockerfile'lar + Compose, Kubernetes manifestleri ve CI.

**Yalnızca yazıya döküldü** ([DESIGN.md](DESIGN.md) içinde — case metninin bizden *çözmemizi değil, üzerine düşünüp anlatmamızı* istediği asıl zor dağıtık sistem konuları): tekrar denemelerde idempotency ve kilitli kalan connector'ın kurtarılması.

## Opsiyonel / Stage 2

Stage 2'nin **backend güvenlik tarafı implement edildi**: JWT login, VIEWER/ADMIN rolleri backend'de zorunlu, korumalı uçlar ve servisler arası ADMIN service token (yukarıdaki [Kimlik doğrulama](#kimlik-doğrulama)). **React yönetim paneli yazılmadı** — Stage 2'nin frontend kısmı kapsam dışı bırakıldı. Tam güvenlik modeli, roller, secret yönetimi ve denetim (audit) log planı [DESIGN.md](DESIGN.md)'in **Güvenlik** bölümündedir.

---

## Harcanan zaman / sırada ne olurdu

- **Zaman:** aşağı yukarı hedeflenen Stage 1 aralığı; odağım çekirdek akışı doğru ve iyi test edilmiş hâle getirmekti.
- **Daha fazla zaman olsa:** idempotent durdurmayı gerçekten kodlardım (idempotency anahtarı ile), cüzdana yükleme (top-up) ucu eklerdim, ikinci bir temiz sınır olarak bir `SessionCompleted` olayı bağlardım ve çalışan bir Station Service'e karşı başlat→durdur akışını sınayan bir servisler arası entegrasyon testi yazardım.

## Repo yapısı

```
station-service/   # istasyonlar, connector'lar, tarifeler (:8081)
session-service/   # oturumlar, cüzdan, başlat/durdur yaşam döngüsü (:8082)
k8s/               # Kubernetes manifestleri
diagrams/          # Mermaid diyagramları (mimari + akışlar)
docker-compose.yml # tek komutla yerel çalıştırma
DESIGN.md          # kararlar, muhakeme yazıları, güvenlik tasarımı
```
