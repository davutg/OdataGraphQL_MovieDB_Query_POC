# Movie Query Lab — GraphQL / SAP OData POC

Java Spring Boot, PostgreSQL ve gerçek **SAP Olingo JPA Processor** ile aynı film verisini iki API üzerinden sorgulayan, sorgu korumaları ve opsiyonel indeks plan kontrolü içeren çalıştırılabilir POC.

## Querydsl / JPA GraphQL

GraphQL artık Spring Data, Querydsl ve EclipseLink ile ortak JPA entity modelini kullanır.
Movie resolver içinde SQL, ResultSet eşleme veya `nodes/director` ile join seçimi yoktur.
Ortak connection adaptörü limitleri, imzalı cursor ve toplam sonuç bütçesini korur.
JDBC sürümü `archive/jdbc-template` dalında (`401fd16`) saklanır.

- [Migration and implementation choices](docs/querydsl-migration.md)
- [When to choose OData or GraphQL](docs/choosing-odata-or-graphql.md)


## Java 8 / 17 / 21 modülleri

Çalışan Java 17 başlangıç kopyası **RUNNING1** etiketiyle korunuyor.
Üç sürümü birlikte çalıştırın:

```bash
docker compose --profile variants up -d --build --wait
```

| Java | Modül | Sorgu arayüzü |
|---|---|---|
| 8 | app-java8 — Spring Boot 2 / javax / SAP 1.1.1 | http://localhost:8082 |
| 17 | app-java17 — Spring Boot 3 / Jakarta / SAP 2.5.0 | http://localhost:8080 |
| 21 | app-java21 — aynı modern kaynaklar, Java 21 bytecode/JVM | http://localhost:8081 |

Üçü de aynı PostgreSQL verisini, query-core politikalarını ve görsel UI'ı kullanır.
UI başlığı gerçek Java sürümünü gösterir. Her JVM'de 68 core kontrolü ve 17 gerçek
PostgreSQL entegrasyon testi geçti; birim testleri Java 8'de 47, Java 17/21'de 43.
`bash scripts/verify-matrix.sh` testleri gerçek JDK 8, 17 ve 21 konteynerlerinde
tekrarlar. `python3 scripts/smoke-matrix.py` çalışan üç servisi kontrol eder.
Ayrıntılar: [docs/java-versions.md](docs/java-versions.md).

## Başlatma

**Docker Desktop / OrbStack + Docker Compose**:

```bash
docker compose up --build
```

İlk derleme SAP kaynaklarını ve Maven bağımlılıklarını derler/indirir. Ardından:

- Sorgu arayüzü: http://localhost:8080
- GraphQL: `POST http://localhost:8080/graphql`
- OData: `GET http://localhost:8080/odata/Movies`
- OData metadata: http://localhost:8080/odata/$metadata
- Limitler: http://localhost:8080/api/capabilities

PostgreSQL `localhost:5432`, database/user `movies`, geliştirme parolası `movies_dev_only`. Portlar yalnızca localhost'a yayınlanır. Veri volume içinde korunur. Seed: 10 tanıdık film ve 100.000 sentetik film; IMDb'den alınmış bir veri seti değildir.

**Java ile çalıştırma** — JDK 17 veya 21, Maven 3.9+, PostgreSQL 16:

```bash
docker compose up -d db
bash scripts/bootstrap-sap.sh   # Windows: .\scripts\bootstrap-sap.ps1
mvn verify
mvn install -DskipTests
mvn -pl app-java17 spring-boot:run
```

Java 17 bytecode hedeflenir; Docker uygulamayı Java 17'de çalıştırır. SAP Maven Central'da yayımlanmadığı için bu sürümün kaynakları `vendor/sap` içine sabitlenmiştir; kullanıcıdan GitHub token'ı veya SAP hesabı istenmez. `vendor/sap/PROVENANCE.md` kaynak/lisans bilgilerini içerir.

## Mac / OrbStack üzerinde doğrulanan çalıştırma

```bash
cd /Users/davutgurbuz/Documents/Codex/movie-query-poc
bash scripts/run-mac.sh
```

Betik sistem varsayılanından bağımsız olarak Java 17 seçer (SDKMAN dahil),
PostgreSQL hazır olana kadar bekler, SAP/reactor derlemesini ve birim testlerini
çalıştırır, ardından uygulamayı başlatır. `JAVA_HOME` elle de verilebilir.
6 Ekim 2026 Mac doğrulaması: 68 core kontrolü, 43 birim ve 17 PostgreSQL
entegrasyon testi geçti. Java 8 ve 21 modülleri de eklendi ve gerçek JVM testleri tamamlandı;
üçlü çalıştırma için yukarıdaki `variants` profilini kullanın.

## Hızlı örnekler

OData — seçici, indeksli filtre:

```http
GET /odata/Movies?$filter=Genre eq 'RareGenre'&$orderby=Id asc&$top=10&$select=Id,Title,Genre&$expand=Director($select=Name)
X-Index-Policy: ENFORCE
```

GraphQL — aynı veri:

```graphql
query {
  movies(first: 10, filter: {genre: "RareGenre"}, orderBy: ID) {
    nodes { id title genre director { name } }
    pageInfo { hasNextPage endCursor }
  }
}
```

GraphQL isteği `{ "query": "...", "variables": { ... }, "operationName": "..." }` şeklinde gönderilir. HTTP header aynı şekilde `X-Index-Policy: ENFORCE` olabilir.

Arayüzde `Selective index`, `Unindexed title`, `Over limit` senaryoları hazırdır. Sorgu önizlemesi elle değiştirilebilir. `Title` sıralaması kasıtlı olarak seçim listesinde bulunur ve allow-list politikasınca reddedilir. Filtre olarak Title eşitliği geçerlidir; indeksi olmadığı için ENFORCE reddedebilir.

## Koruma sözleşmesi

| Kontrol | Varsayılan | Uygulama |
|---|---:|---|
| default `$top` / `first` | 100 | Limits + API politikası |
| max `$top` / `first` | 500 | 501 reddedilir; `MAX_TOP=1000` seçilebilir |
| Server page size | 50 | OData nextLink / GraphQL imzalı cursor |
| max expand depth | 2 | OData policy; GraphQL yapısal derinlik limiti |
| max complexity | 2000 | OData token/alan/expand maliyeti; GraphQL AST, alias ve fragment maliyeti |
| max offset | 10000 | Derin offset reddedilir |
| Request processing timeout | 5000 ms | Sınırlı worker havuzu + Future deadline; HTTP 504 |
| DB statement timeout | 3000 ms | PostgreSQL statement_timeout; kalan request süresine göre azalır |
| Filter allow-list | Id, Title, Year, Rating, Genre | Fonksiyon, lambda, navigation filter yok |
| Order allow-list | Id, Year, Rating, Genre | SQL kolonları yalnızca sabit eşleme üzerinden |
| rate limit | 60/dakika/IP | HTTP 429 ve Retry-After |
| concurrency | 8 | Kuyruksuz sınırlı worker; doluyken HTTP 503 |
| Body / query size | 32 KiB / 8192 karakter | SQL çalıştırılmadan kontrol |
| Writes / `$batch` / `$apply` | Kapalı | Okuma POC'si |

OData property adları **Id, Title, Year, Rating, Genre, Director**; GraphQL adları küçük harfle başlar. OData filtreleri `eq/ne/gt/ge/lt/le`, `and/or` ve parantezleri destekler; gerçek OData çözümlemesini ve SQL üretimini SAP/Olingo yapar. Özel kod bir OData parser yerine güvenli public yüzeyi sınırlandıran preprocessor'dür.

Bu veri modelinde yalnızca `Movie → Director` tekil ilişkisi açılır. Koleksiyon expand ve recursive navigasyon yayımlanmaz; bu nedenle meşru sorgulardaki gerçek ilişki derinliği 1'dir. Derinlik limiti 2 olsa da sırf bunu göstermek için sınırsız ters ilişki eklenmemiştir. GraphQL şemasında rekürsif ilişki yoktur.

Sayfalama için `$top`/`first` **ilk isteğin toplam sonuç bütçesidir**. Örneğin 75 → 50 + 25. Sonraki sayfa bu bütçeyi büyütemez. OData nextLink aynen kullanılmalı; başka filtrelerle birleştirilemez. GraphQL `after` aynı filtre ve sıralamayla kullanılmalı. Varsayılan cursor/token ömrü 5 dakika. OData tokenları süreç belleğindedir ve yeniden başlatılınca geçersiz olur. GraphQL cursorları varsayılan rastgele anahtarla aynı davranışı gösterir; CURSOR_SECRET_BASE64 ile en az 32 baytlık sabit bir anahtar verilirse restart ve birden fazla instance arasında korunur. CURSOR_TTL_MS GraphQL cursor süresini belirler. Id ikinci sıralama anahtarı olarak eklenir. Offset sayfalaması eşzamanlı veri değişikliklerinde snapshot garantisi vermez.

## Opsiyonel index policy preprocessor

```yaml
poc:
  index-policy:
    mode: OFF                  # OFF | AUDIT | ENFORCE
    allow-client-selection: true
    min-table-rows: 10000
    max-plan-cost: 100000
```

| Mod | Davranış |
|---|---|
| OFF | EXPLAIN çalıştırılmaz |
| AUDIT | Plan raporlanır; politika ihlali sorguyu engellemez |
| ENFORCE | Büyük tablodaki Seq Scan / Parallel Seq Scan, yüksek tahmini maliyet veya doğrulanamayan plan sorguyu engeller |

Hem **SAP/EclipseLink'in ürettiği JDBC SQL**, hem **GraphQL/Querydsl/EclipseLink SQL** gerçek JDBC çalıştırma sınırında kontrol edilir. PreparedStatement parametreleri aynı JDBC setter çağrılarıyla EXPLAIN'e aktarılır; SQL'e değer birleştirilmez. `EXPLAIN (FORMAT JSON, VERBOSE TRUE)` kullanılır, **ANALYZE kullanılmaz**. Plan ağacı alt sorgu/join dalları dahil dolaşılır. Index Scan, Index Only Scan ve Bitmap Index Scan bilgileri raporlanır. Küçük tablolardaki sequential scan'e izin verilir. Tablo büyüklüğü `pg_class.reltuples` istatistiğinden alınır; bilinmeyen istatistik ENFORCE'ta reddedilir. Seed sonrasında ANALYZE çalıştırılır.

Bu politika “sorguda herhangi bir indeks geçtiyse kabul et” demez: bir join'de indeks kullanılırken başka büyük tablo full scan yapıyorsa reddedebilir. `count(*)` ve SAP sayfalamasının count sorguları da kontrol edilir. Geniş filtre, tüm kayıtları sayma veya uygun olmayan sıralama ENFORCE'ta reddedilebilir. Plan seçimi veriye/istatistiğe/PostgreSQL sürümüne bağlıdır; sabit indeks adı testi `RareGenre` gibi seçici bir veri üzerinde yapılır.

Raporlar GraphQL `extensions.indexPolicy` içinde ve her iki API için `X-Request-Id` ile `/api/requests/{id}` üzerinden görülebilir. Son 200 rapor bellekte tutulur. SQL metni ve parametre değerleri rapora eklenmez.

**Operasyonel sınır:** EXPLAIN tahmini plan kontrolüdür; çalıştırılmış planı kanıtlamaz. PostgreSQL `prepareThreshold=0` ile server-side generic prepared plan farkı azaltılır. İstatistik değişimi, eşzamanlı DDL, farklı session ayarları veya plan değişimi yine mümkündür. Kontrol SQL'i çalıştırır ve planlama maliyeti ekler; bu nedenle opsiyoneldir. `enable_seqscan=off` gibi indeks zorlaması yapılmaz.

Yerel POC'de istemci mod seçebilir. Merkezi zorunlu politika için `INDEX_POLICY_MODE=ENFORCE` ve `INDEX_POLICY_CLIENT_SELECTION=false` ayarlayın; istemci OFF/AUDIT'e düşüremez. Kimlik doğrulama, tenant yetkilendirmesi ve paylaşımlı rate/token store bu POC'nin kapsamında değildir. Dış erişime açmadan önce ilgili uygulama katmanları eklenmelidir.

## Testler

```bash
mvn verify                       # birim/politika testleri, DB gerekmez
mvn verify -Pintegration          # gerçek PostgreSQL ile API / planner testleri
```

İkinci komut için db servisi açık olmalıdır. Alternatif veritabanı:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/movies?prepareThreshold=0'
export DB_USER=movies
export DB_PASSWORD=movies_dev_only
mvn verify -Pintegration
```

Testler: top/offset sınırları, OData filtre grameri ve allow-list, expand derinliği, cursor bütünlüğü ve sorguya bağlanması, indeks denetiminde parametre replay, engellenen SQL'in çalışmaması, küçük tablo istisnası, EXPLAIN hata durumunda fail-closed, rate/concurrency/timeout, gerçek SAP metadata/filter/select/expand/count, GraphQL alias complexity, iki API'de gerçek index/Seq Scan kontrolü ve SQL injection'ın literal kalması.

Ek örnekler: [docs/queries.http](docs/queries.http), [docs/graphql-examples.graphql](docs/graphql-examples.graphql), [docs/index-policy.md](docs/index-policy.md). Önceki tek modüllü sürümün sonuçları [docs/verification.md](docs/verification.md), bu değişikliğin durumu [docs/java17-migration.md](docs/java17-migration.md) dosyasındadır.


## Ortak sorgu çekirdeği

`query-core` Java 8 hedeflidir ve framework bağımlılığı içermez. Java 8 adaptörü
javax/Spring Boot 2, Java 17/21 adaptörleri Jakarta/Spring Boot 3 kullanır.
Java 21 modülü Java 17 kaynak/test ağacını paylaşır ve kendi Java 21 JAR'ını üretir.
Sözdizimi, HTTP, JDBC ve framework kodu adaptörlerde kalır; alan izinleri,
sayfalama bütçesi, cursor ve tahmini index planı kararları ortak çekirdektedir.
Tam matris doğrulaması [docs/java-versions.md](docs/java-versions.md) dosyasındadır.

## English documentation

- [Architecture, usage, and configuration](docs/architecture-and-user-guide.md) — running the applications, query builder, API examples, paging, policies, configuration, and troubleshooting.
- [Developer implementation](docs/developer-implementation-guide.md) — core contracts and data structures, protocol evaluation, SQL plan inspection, request lifecycle, module compatibility, and extension guidance.

- [OData cheatsheet](docs/odata-cheatsheet.md) — query syntax, navigation, depth, and POC restrictions.
- [GraphQL cheatsheet](docs/graphql-cheatsheet.md) — selections, variables, relationships, depth, and pagination.
