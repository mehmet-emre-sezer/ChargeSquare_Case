# Tasarım Notları

Önemli kararlar, iki zor dağıtık sistem sorusunun arkasındaki mantık ve Stage 2 güvenlik tasarımı. Tamamlayıcı diyagramlar [`diagrams/`](diagrams/) altında.

---

## Tasarım kararları

Her satır bilinçli bir karar: neden bu case için uygun ve **bedeli ne**. Hiçbir seçim bedelsiz değil; aşağıdaki son sütun neyi feda ettiğimi ve gerçek bir sistemde ne zaman farklı seçeceğimi söylüyor.

| Karar | Seçim | Neden bu case için | Bedeli / ne zaman farklı seçerdim |
|---|---|---|---|
| Dil / framework | Java 21 + Spring Boot 3.3.5 | Spring Boot'u ilk kez bu projede kullandım; ChargeSquare'in ana stack'i olduğu için bilinçli seçtim ve öğrenmek istedim. Kararları framework'e değil temiz kod/katmanlı mimari ilkelerine dayandırdım. | Spring'e yeni olduğum için bazı yerler ekosistemi yıllardır kullanan birinin yazacağından daha uzun olabilir. Salt hız isteseydim daha önce kullandığım bir stack'i seçerdim; ama şirketin stack'inde çalışabildiğimi göstermek daha değerliydi. |
| Servis sayısı | İki (Station + Session); cüzdan Session içinde | Önerilen varsayılan; dilimi küçük tutup emeği doğruluğa ayırıyor. Cüzdan yalnızca stop anında, aynı transaction içinde kullanılıyor — ayırmak kazanç sağlamazdı. | Cüzdan gerçek bir servis sınırı olarak modellenmiyor. Ayrı bir ekip sahiplenecek olsaydı ya da cüzdan başka ürünlerce de kullanılsaydı Wallet Service'e ayırırdım — bedeli fazladan bir ağ hop'u ve yeni bir kısmi hata senaryosu olurdu. |
| Veritabanı | Tek paylaşımlı PostgreSQL, servis başına bir şema (`station`, `session`) | Açıkça çalışan en yalın çözüm; şema ayrımı temiz bir sahiplik sınırı verir ve iki servisin Flyway geçmişinin çakışmasını önler. | Gerçek izolasyon yok: bir servis veritabanını yorarsa diğeri de etkilenir ve bağımsız ölçekleme/yedekleme yapılamaz. Servisler ayrı ayrı ölçeklenip deploy edilecekse servis başına ayrı veritabanına geçerdim. |
| Şema yönetimi | Flyway migration, `ddl-auto: validate` | Şema sıfırdan kurulur ve versiyonlanır; Hibernate şemayı değiştirmez, yalnızca eşlemeyi doğrular. Yeniden başlatmaya dayanır. | Her şema değişikliği elle migration yazmayı gerektirir; hızlı prototipte auto-DDL daha çevik olurdu. Ama auto-DDL üretimde veri kaybettirebileceği için bu bedeli bilerek kabul ettim. |
| Servis iletişimi | Senkron REST (Session → Station) | İstenen gerçek ağ çağrısı; en az hareketli parça. Broker, saga ya da retry mekanizması yok — case metni bunları bu dilim için açıkça dışarıda bırakıyor. | Sıkı bağlılık: Station düşükse start/stop da düşer. Kesinti toleransı veya yüksek hacim gerekseydi settlement'ı `SessionCompleted` olayına taşırdım — bedeli eventual consistency ve idempotent tüketici zorunluluğu olurdu. |
| Oturumda tarife | **Başlangıçta** kopyala (snapshot) | Fiyat oturumun ortasında değişse bile müşteri fişi taktığı andaki fiyatı öder; oturum kendi içinde tutarlı ve öngörülebilir kalır. | Yanlış girilmiş bir tarifeyi geriye dönük düzeltmek mümkün değil: snapshot dondurulmuş durumda. Düzeltme ihtiyacı olan bir üründe ayrı bir "yeniden fiyatlandırma" akışı gerekirdi. |
| Para | `BigDecimal` + `NUMERIC(12,2)`, 2 basamağa `HALF_UP` | Para söz konusuysa asla kayan nokta kullanmam; yuvarlama kuralı tek bir yerde (`TariffSnapshot.costFor`) ve örnek hesapla (`108.25`) test edilmiş. | `BigDecimal` daha ayrıntılı yazım ister ve `equals` scale'e duyarlıdır (testlerde bu yüzden `isEqualByComparingTo` kullandım). Çok yüksek hacimli bir fiyatlandırma servisinde tam sayı minor birim (kuruş) daha hızlı ve tuzaksız olurdu. |
| Yetersiz bakiye | Durdurmaya izin ver; cüzdan eksiye düşebilir | Enerji zaten fiziksel olarak verildi; durdurmayı reddetmek parayı geri getirmez, sadece connector'ı kilitli bırakır. Borç negatif bakiye olarak takip edilir. | Tahsil edilemeyecek borç riski doğar. Üretimde ön provizyon + canlı sayaçla bir **önleme** katmanı eklerdim; bunun bu dilimde neden mümkün olmadığı ve negatif bakiyenin o tasarımda bile neden gerektiği aşağıda ayrı bölümde. |
| Bağımlılık erişilemezse | Hızlı hata ver, `503` | Temiz ve tekrar denenebilir bir hata, yarım kalmış retry mantığından iyidir (ayrıntı aşağıda). | Geçici bir kesintide kullanıcı hatayı görür; kısa bir retry bunu gizleyebilirdi. Ama idempotency olmadan retry çift faturalama demek — bu yüzden sıralamam net: önce idempotency, sonra retry. |
| Eşzamanlılık | Connector durumu ve cüzdan üzerinde satır kilidi (pessimistic) | Ucuz ve yerel bir doğruluk garantisi: aynı anda gelen iki başlatma tek connector'ı birlikte kapamaz, eşzamanlı iki tahsilat bakiyeyi bozamaz. | Kilit tutulurken yapılan işler transaction'ı uzatır (bkz. aşağıdaki durdurma transaction sınırı notu). Yüksek eşzamanlılıkta optimistic locking + daha kısa transaction'lar tercih ederdim. |
| Repo | Tek repo (monorepo) | Take-home için en pratiği: tek klon, tek `docker compose up`. | Servisler bağımsız versiyonlanıp deploy edilmiyor; CI her şeyi birlikte kurar. Ayrı ekipler ve ayrı release kadansı olsaydı multi-repo (veya en azından bağımsız pipeline'lar) gerekirdi. |

Durum makineleri ile başlat/durdur akışları şu diyagramlarda çizili: [`diagrams/state-machines.md`](diagrams/state-machines.md), [`diagrams/start-flow.md`](diagrams/start-flow.md) ve [`diagrams/stop-flow.md`](diagrams/stop-flow.md).

---

## Durum geçişleri

Tüm yaşam döngüsü bu dört satır. Reddedilen her geçiş **hiçbir değişiklik bırakmaz** — yarım durum yok.

| Mevcut durum | İşlem | Sonuç |
|---|---|---|
| Connector `AVAILABLE` | Başlat | Connector `OCCUPIED` + `ACTIVE` oturum (tarife snapshot'lanır) |
| Connector `OCCUPIED` | Başlat | `409` — oturum yaratılmaz, connector'a dokunulmaz |
| Oturum `ACTIVE` | Durdur | `COMPLETED` + cüzdan tahsilatı + connector `AVAILABLE` |
| Oturum `COMPLETED` | Durdur | `409` — cüzdan tekrar düşülmez, hiçbir değişiklik olmaz |

---

## Domain invariant'ları

Sistemin her zaman doğru tutması gereken kurallar. Her birini veriye en yakın yerde korumaya çalıştım — kontrolün controller'a ya da çağırana bırakıldığı bir yer yok.

| Invariant | Nerede korunuyor |
|---|---|
| Bir connector aynı anda yalnızca tek bir oturum tarafından tutulabilir. | `Connector.occupy()` yalnızca `AVAILABLE` iken izin verir; `findByIdForUpdate` satır kilidi eşzamanlı iki start'ın yarışmasını engeller. |
| Dolu connector'da oturum başlatılamaz ve başarısız start hiçbir kayıt bırakmaz. | `SessionService.start()` önce Station'dan durumu okur, dolu ise `occupy` denenmeden 409 atar; oturum ancak occupy başarılı olduktan sonra yazılır. |
| Bir oturumun tarifesi başladıktan sonra değişmez. | `TariffSnapshot` oturum satırına gömülüdür; stop yalnızca snapshot'ı okur, tarife için Station'a tekrar gitmez (testle doğrulanmıştır). |
| ACTIVE olmayan oturum faturalanamaz; aynı oturum iki kez tahsil edilemez. | `Session.stop()` içindeki durum guard'ı (`SessionNotActiveException`) + stop yolunda oturum satır kilidi. |
| Cüzdan yalnızca oturumun kendi maliyeti kadar ve yalnızca bir kez düşülür. | Tahsilat `Session.stop()`'un döndürdüğü maliyetle yapılır; `Wallet.debit()` kilitli satırda ve tamamlama ile aynı transaction'da çalışır. |
| COMPLETED bir oturumun connector'ı serbest kalır. | Stop transaction'ı `release` başarılı olmadan commit etmez; başarısızsa her şey geri alınır ve oturum ACTIVE kalır. |
| Para asla kayan noktayla temsil edilmez; maliyet 2 basamağa yuvarlanır. | `BigDecimal` + `NUMERIC(12,2)` kolonlar; yuvarlama tek yerde: `TariffSnapshot.costFor()`. |
| Connector durumu ile oturum durumu tutarlıdır. | Durum değişimleri yalnızca start/stop yollarından geçer; Session, connector durumunu kendi tarafında ikinci bir kopya olarak tutmaz — tek kaynak Station'dır. |

Veritabanı bu kuralların bir kısmını bağımsız olarak da destekler: `status` kolonlarında CHECK constraint, cüzdanda `user_id` unique, tarife tutarlarında negatif olmama kontrolü.

---

## Yetersiz bakiye — neden durdurmayı reddetmiyoruz

Bakiye maliyeti karşılamıyorsa durdurmayı **reddetmiyoruz**: faturayı keser, cüzdanın eksiye düşmesine izin verir ve connector'ı yine serbest bırakırız.

Gerekçe fiziksel: **elektrik zaten verilmiş.** Durdurmayı reddetmek enerjiyi geri getirmez; yalnızca oturumu `ACTIVE`, connector'ı `OCCUPIED` bırakır. Yani ödeme sorununu çözmediği gibi bir de donanımı kilitler ve sıradaki sürücüyü engeller. Borcu negatif bakiye olarak takip etmek, hem muhasebeyi dürüst tutar hem de istasyonu serbest bırakır.

**Gerçek bir sistemde bunu önlerdim** — ama bu dilimde önleyemem, sebebiyle birlikte:

Üretimde doğru kurgu katmanlı olurdu: şarj başlamadan **ön provizyon / kredi limiti** almak, oturum boyunca **canlı sayaç değerleriyle** tüketimi izlemek, kredi tükenmeden uygulamaya **bildirim + "bakiye yükle" akışı** gönderilir, kullanıcı yüklerse oturumu sürdürülür, yüklemezse **uzaktan durdurma** komutu verilir. Bu, sürücünün de operasyonun da lehine.

Bu dilimde uygulanabilir değil, çünkü **canlı tüketim verimiz yok**: enerji yalnızca stop isteğinin içinde bildiriliyor (sayaç simüle ediliyor). Ne kadar elektrik aktığını ancak oturum biterken öğreniyoruz — dolayısıyla "bakiye tükendi, şimdi durdur" diyebileceğim bir an hiç oluşmuyor. Bunu kurmak sayaç akışı + periyodik kontrol + yeni durum geçişleri demek olurdu; case metninin bu dilim için açıkça istemediği türden bir makine.

**Önemli nüans:** o önleme katmanı kurulsa bile **negatif bakiye ihtiyacı ortadan kalkmaz.** "Kredi bitti" tespiti ile şarjın fiilen durması arasında gecikme vardır (durdurma komutu ağdan gidip gelir, araç o sırada birkaç saniye daha çeker); aradaki enerji zaten teslim edilmiş olur ve küçük bir aşım oluşur. Yani ön provizyon bir **önleme** katmanı, negatif bakiye ise **mutabakat** katmanıdır — biri diğerinin alternatifi değil. Bu dilimde yalnızca mutabakat katmanını kodladım; önleme katmanının yeri belli ve üstüne temiz şekilde eklenir.

---

## Bağımlılık erişilemediğinde

Session Service, Station Service'e ulaşamazsa **hızlıca hata verir**: `503 STATION_UNAVAILABLE` ve net bir hata gövdesi — retry yok, fallback yok. RestClient'ın açık bağlanma/okuma zaman aşımları vardır; böylece yavaşlayan bir Station isteği süresiz askıda bırakamaz.

Durdurmada `release` çağrısı, cüzdandan tahsilat yapan ve oturumu tamamlayan *aynı transaction'ın içinde* yapılır. Station erişilemezse transaction'ın tamamı geri alınır: hiçbir tahsilat olmaz, oturum `ACTIVE` kalır, connector `OCCUPIED` kalır ve çağıran, gönül rahatlığıyla tekrar deneyebileceği bir `503` alır. Böylece bir bağımlılık kesintisi, yarım kalmış bir durdurma yerine tutarlı ve tekrar denenebilir bir duruma dönüşür.

### Durdurmada transaction sınırı — bilinçli bir ödünleşme

Bu tercihin bir bedeli var ve saklamak istemem: `release` bir **ağ çağrısı** ve onu transaction'ın içinde yaptığım için, çağrı sürerken oturum ve cüzdan satırlarının kilidi ile bir veritabanı bağlantısı açık kalıyor. Station yavaşlarsa kilitler o kadar uzun tutulur; bunu sınırlayan tek şey RestClient'ın zaman aşımları (2 sn bağlanma / 5 sn okuma).

Alternatifi düşündüm: önce parasal işi commit'leyip (`COMPLETED` + tahsilat), `release`'i transaction dışında elden geldiğince çağırmak. Bu, kilit süresini en aza indirir; ama `release` başarısız olursa **oturum faturalanmış olmasına rağmen connector kilitli kalır** ve bunu ancak aşağıdaki temizlik işi toparlar.

İkisi arasında şu nedenle bu tarafı seçtim: bu ölçekte (tek istasyon, düşük eşzamanlılık, zaman aşımlarıyla sınırlı çağrı) **tutarlılık, kilit süresinden daha değerli** — kullanıcı yanlışlıkla faturalanmıyor ve elde kalıntı durum kalmıyor. Yüksek eşzamanlılıkta tercihi tersine çevirirdim: parayı kısa bir transaction'da atomik olarak yaz, `release`'i dışarıda yap ve tutarlılığı uzlaştırıcı (reconciler) işe bırak.

---

## Muhakeme sorusu 1 — Tekrar denemelerde idempotency

**Durum:** uygulama `POST /sessions/100/stop` gönderir, yanıt yolda kaybolur ve uygulama aynı isteği tekrar yollar. İstek iki kez ulaşır.

**Koruma olmasa ne bozulur:** naif bir kurgu her iki gelişte de maliyeti hesaplayıp cüzdandan düşerdi — sürücü tek oturum için **iki kez faturalanır.**

**Bugün bizi ne koruyor:** durdurma, oturumun durum makinesiyle korunuyor. İlk durdurma oturumu `ACTIVE → COMPLETED` yapar; ikincisi artık `COMPLETED` olan oturumu okur ve `Session.stop()` `SessionNotActiveException` fırlatır → `409 SESSION_NOT_ACTIVE`. Cüzdana ikinci kez dokunulmaz. Oturum satırı ayrıca pessimistic kilit ile okunduğu için *aynı anda* gelen iki durdurma yarışmak yerine sıraya girer. Yani tekrarlanan bir durdurma zararsızdır: ya hiçbir şey yapmaz ya da çakışma döndürür — bu dilim için bu kadarı yeterli.

**Gerçekten idempotent nasıl yapardım:** mevcut kontrol, tekrar denemede hata (`409`) döndürüyor; oysa yanıtı kaybolan istemci ideal olarak bir hata değil, **ilk seferki makbuzu** geri almak ister. Bunun için bir idempotency anahtarı eklerdim — istemci sabit bir `Idempotency-Key` başlığı gönderir (veya doğal anahtar olarak oturum id'sini kullanır) ve ilk başarılı durdurma, sonucunu bu anahtara bağlı saklar. Aynı anahtarla gelen tekrar, yeniden hesaplamak ya da hata vermek yerine saklanan makbuzu `200` ile döner. Böylece hiçbir broker veya saga olmadan, istemci açısından işlem tam olarak bir kez gerçekleşmiş olur.

---

## Muhakeme sorusu 2 — Kilitli kalan connector / kısmi hata

**Durum:** başlatmada `POST /connectors/10/occupy` Station Service'te başarılı olur, ama hemen sonraki adım — `ACTIVE` oturum satırını yazmak — başarısız olur (veritabanı kesintisi, çökme, zaman aşımı). Connector artık Station'da `OCCUPIED`, ama onu durduracak hiçbir oturum yok. Connector **kilitli kalır.**

**Bu boşluk neden var:** `occupy` başka bir servise yapılan bir ağ çağrısıdır ve kendi yerel transaction'ımızla geri alınamaz. Case metnindeki başlatma sırası (doğrula → kapa → oturum yarat), occupy'ın Station tarafında kesinleşmesiyle oturumun Session tarafında kesinleşmesi arasında her zaman küçük bir boşluk bırakır. Bu boşluğu mümkün olduğunca daraltıyorum (oturum kaydı hemen bir sonraki adım), ama yalnızca senkron REST ile tümüyle kapatılamaz.

**Nasıl kurtarırdım, artı ve eksileriyle:**
- **Zaman aşımı + hızlı hata (hâlihazırda var):** RestClient süresiz beklemek yerine zaman aşımına uğrar, böylece belirsizlikte takılmadan temiz bir hata veririz. Bu hasarı sınırlar ama sahipsiz kalmış bir occupy'ı geri almaz.
- **Telafi edici bir release:** oturum kaydı başarısız olunca, occupy'ı geri almak için elden geldiğince bir `release` çağrısı yapmak. Basittir ve çoğu durumu kurtarır, ama telafinin kendisi de başarısız olabileceği için garanti değil, bir hafifletme yöntemidir.
- **Periyodik bir temizlik işi (gerçek sistemde bunu yapardım):** `ACTIVE` bir oturumu olmadığı hâlde belli bir süredir `OCCUPIED` duran connector'ları bulup serbest bırakan küçük bir uzlaştırıcı (reconciler). Sağlam seçenek budur: eventually consistent çalışır, tek tük hatalara dayanıklıdır ve dağıtık transaction gerektirmez — bedeli, temizlik çalışana kadar connector'ın kısa süre kullanılamamasıdır.

Bu dilimde, case metninin istediği gibi, kurtarmayı kodlamak yerine anlatıyorum. Durum kontrolleri *oturum* tarafını zaten tutarlı tutuyor; temizlik işi ise servisler arasındaki bu boşluğu kapatırdı.

---

## Güvenlik (Stage 2 — backend + panel yapıldı)

Stage 2'nin hem backend güvenliğini hem de yönetim panelini **kodladım**. Aşağıdaki model hem uygulanan davranışı hem de arkasındaki gerekçeyi anlatır.

**Kimlik doğrulama.** `POST /auth/login` ucu, doğru kimlik bilgisinde bir **JWT** üretir; bu token `Authorization: Bearer <token>` ile taşınır ve her iki serviste de doğrulanır. Demo kullanıcılar (`admin`/`admin123` → ADMIN, `viewer`/`viewer123` → VIEWER) config'ten gelir ve şifreleri BCrypt ile hash'lenir; gerçek kimlik bilgisi depoya konmaz. Token, kullanıcı ve rol bilgisini taşır ve `JWT_SECRET` ortam değişkeninden gelen paylaşılan HMAC anahtarıyla imzalanır — böylece her servis token'ı ekstra ağ çağrısı olmadan yerel doğrular.

**Yetkilendirme — iki rol, sunucuda zorunlu kılınır.**

| Yetki | VIEWER | ADMIN |
|---|---|---|
| Giriş, istasyon / connector / oturum görüntüleme | ✓ | ✓ |
| Oturum başlatma / durdurma, cüzdana yükleme | ✗ | ✓ |
| Dahili `occupy` / `release` | ✗ | ✓ (servisler arası) |

Roller **sunucuda** kontrol edilir, buton gizleyerek değil. Kimliği doğrulanmamış istek `401`, kimliği doğrulanmış ama yetkisi olmayan istek `403` alır. Rol, token'ın içinde durur (hızlıdır, her istekte ayrı sorgu gerektirmez); bunun bedeli, bir rolü geri almanın kısa token ömrü veya küçük bir kara liste gerektirmesidir — bu ölçekte kabul edilebilir. (Cüzdana yükleme ucu bu dilimde yazılmadı; eklendiğinde tablodaki gibi ADMIN altına girer.)

**API koruması.** Okuma uçları geçerli bir token ister; yazma uçları (start/stop) ve dahili `occupy`/`release` yolu ayrıca `ADMIN` ister ve anonim ya da salt-görüntüleyici çağrıları reddeder — hepsi uygulandı ve testlerle doğrulandı. Backend her girdiyi kendisi doğrular; panel de girdiyi doğrulasa bile ona güvenilmez, çünkü arayüz atlanıp API'ye doğrudan istek atılabilir (panelde VIEWER için pasifleşen "Durdur" butonu backend'de ayrıca 403 ile korunur). CORS, config tabanlı olarak yalnızca panelin origin'ine (`CORS_ALLOWED_ORIGINS`, varsayılan `http://localhost:5173`) açılacak şekilde uygulandı.

**Secret yönetimi.** JWT imzalama anahtarı ve veritabanı kimlik bilgileri ortamdan/konfigürasyondan (bir k8s `Secret`) gelir, asla depodan değil — depodaki [`k8s/secret.yaml`](k8s/secret.yaml) yalnızca örnek değerler içerir. Gerçek bir kurulumda bunlar bir secret yöneticisinden gelir (Sealed Secrets / External Secrets / bir bulut KMS).

**Denetim (audit) log.** Güvenlik açısından önemli işlemleri kaydederdim — kim oturum başlattı/durdurdu, kim cüzdana yükleme yaptı, hangi girişler başarısız oldu — her birini zaman damgası ve işlemi yapanın kimliğiyle, örneğin:

```
2026-07-14T10:45:00Z action=SESSION_STOPPED actor=user:7 role=ADMIN sessionId=100 cost=108.25
```

Bir operasyon paneli için bu önemlidir, çünkü başlat/durdur ve cüzdan işlemleri hem para hem de donanım hareket ettirir; sonradan "bunu kim, ne zaman yaptı" sorusuna cevap verebilmek gerekir.

**Panel token saklama.** Panel token'ı `localStorage`'da tutar — basitlik için tercih edildi ve sayfa yenilemede oturum korunur. Trade-off: `localStorage` XSS'e açıktır (script token'ı okuyabilir); `httpOnly` cookie XSS'i kapatır ama CSRF önlemi gerektirir; en güvenlisi token'ı yalnızca bellekte tutmaktır ama yenilemede oturum kaybolur. Bu ölçekte `localStorage` kabul edilebilir; gerçek sistemde kısa ömürlü token + yenileme akışıyla birlikte cookie'ye geçerdim.

**Sonradan ekleyeceklerim:** yenileme (refresh) token'ları, OAuth/SSO, şifre sıfırlama, girişte rate limiting ve gerçek bir kullanıcı deposu. Modeli göstermek için hiçbiri şart değil; hepsi bu JWT + RBAC temelinin üzerine rahatça eklenir.
