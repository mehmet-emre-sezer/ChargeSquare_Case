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

Java + Spring Boot hem ChargeSquare'in ana stack'i hem de en rahat, en temiz yazabildiğim ikili — bu sayede araçlarla değil, işin kendisiyle uğraştım. Tüm kararların gerekçeleri için: [DESIGN.md](DESIGN.md).

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

```bash
# 1) BAŞLAT — 201 döner, connector OCCUPIED olur, tarifenin kopyası alınır
curl -X POST localhost:8082/sessions \
  -H 'Content-Type: application/json' \
  -d '{"userId":7,"connectorId":10}'

# 2) DURDUR — maliyet = 12.5 * 8.50 + 2.00 = 108.25, cüzdan 500.00 -> 391.75
curl -X POST localhost:8082/sessions/100/stop \
  -H 'Content-Type: application/json' \
  -d '{"energyKwh":12.5}'

# 3) Makbuzu geri oku
curl localhost:8082/sessions/100

# 4) Hatalı durum — artık dolu olan connector'a başlatma 409 döner, oturum yaratmaz
curl -X POST localhost:8082/sessions \
  -H 'Content-Type: application/json' \
  -d '{"userId":7,"connectorId":10}'
```

Bütün hatalar aynı gövde biçimini kullanır: `{ "error": "KOD", "message": "..." }`.

---

## API

**Station Service** (`:8081`)

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/connectors/{id}` | Connector durumu + tarife (bilinmiyorsa 404) |
| GET | `/stations/{id}/connectors` | Bir istasyonun connector'ları |
| POST | `/connectors/{id}/occupy` | OCCUPIED yap (zaten doluysa 409) — dahili |
| POST | `/connectors/{id}/release` | AVAILABLE yap (idempotent) — dahili |

**Session Service** (`:8082`)

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/sessions` | BAŞLAT — 201; 404/409/400 durumunda oturum yaratılmaz |
| POST | `/sessions/{id}/stop` | DURDUR + FATURALA + TAHSİL ET — 200 makbuz |
| GET | `/sessions/{id}` | Tek bir oturumu oku |
| GET | `/users/{userId}/sessions` | Bir kullanıcının oturumları |

Durum kodları: `404` bilinmeyen connector/oturum · `409` dolu connector / aktif olmayan (veya ikinci kez) durdurma · `400` geçersiz istek · `503` Station Service'e ulaşılamıyor.

---

## Testler

```bash
# servis başına (JDK 21 gerekir — aşağıdaki nota bakın)
mvn -f station-service/pom.xml test
mvn -f session-service/pom.xml test
```

14 test var: maliyet hesabı (örnek: `108.25`, artı yuvarlama durumları), başlat→durdur yaşam döngüsü ve hatalı istekler (dolu connector'a başlatma 409, ikinci durdurma 409, eksik alan 400, bilinmeyen kayıt 404).

- Testler **gömülü ama gerçek bir PostgreSQL** (zonky) üzerinde koşar; yani `mvn test` için **Docker gerekmez.** Migration'lar üretimdekiyle birebir aynı çalışır.
- Testleri koşmak için **JDK 21 şarttır:** Mockito'nun kullandığı byte-code kütüphanesi daha yeni JDK sürümlerini desteklemiyor. Docker/Compose yolu bundan etkilenmez (içeride zaten 21 ile derler). CI de JDK 21'e sabitlenmiştir.

---

## Konfigürasyon

Tüm ayarlar ortam değişkenlerinden gelir; hiçbir şey koda gömülü değil, hiçbir secret depoya konmadı. Tam liste için [`.env.example`](.env.example) (veritabanı URL'i ve kimlik bilgileri, servis portları, `STATION_SERVICE_URL`).

---

## Kubernetes

[`k8s/`](k8s/) altında sade manifestler var: bir `ConfigMap` ve `Secret`, bir Postgres `Deployment`/`Service`, ayrıca her servis için `/health` uçlarına bağlı readiness/liveness kontrolü olan birer `Deployment` + `Service`.

Manifestlerin tam doğrulaması çalışan bir cluster ister:

```bash
kubectl apply --dry-run=client -f k8s/
```

Bunu canlı bir cluster üzerinde çalıştıramadım; manifestleri geçerli YAML olarak ve beklenen kaynak türleriyle doğruladım. Secret değerleri örnek (placeholder) değerlerdir — gerçek bir kurulumda bir secret yöneticisinden gelir, asla depodan değil.

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

**Kodlandı:** tüm başlat → durdur → faturala → tahsil et akışı, bütün durum kontrolleri, kayan noktadan kaçınan maliyet hesabı, gerçek Session → Station ağ çağrısı, gerçek veritabanı kalıcılığı + başlangıç verisi, testler, Dockerfile'lar + Compose, Kubernetes manifestleri ve CI.

**Yalnızca yazıya döküldü** ([DESIGN.md](DESIGN.md) içinde — case metninin bizden *çözmemizi değil, üzerine düşünüp anlatmamızı* istediği asıl zor dağıtık sistem konuları): tekrar denemelerde idempotency ve kilitli kalan connector'ın kurtarılması.

## Opsiyonel / Stage 2

Stage 2 (yönetim paneli + kimlik doğrulama) **yalnızca güvenlik tasarımı** olarak ele alındı; panel yazılmadı. Kimlik doğrulama modeli, roller, API koruması, secret yönetimi ve denetim (audit) log planı [DESIGN.md](DESIGN.md)'in **Güvenlik** bölümündedir.

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
