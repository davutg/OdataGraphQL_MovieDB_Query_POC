# Java 8 / 17 / 21 runtime matrix — 2026-10-06

The verified Java 17 copy was committed before module changes:
`a716aa0c1d24a316fdebe631748c5777a0d98633`, tagged **RUNNING1**.
The tag still points to that snapshot; subsequent work does not move it.

## Modules and endpoints

| Module | Stack | Bytecode | UI |
|---|---|---:|---|
| query-core | Framework-free shared policy/cursor/index decisions | Java 8 (52) | — |
| app-java8 | Spring Boot 2.7.18, Spring GraphQL 1.0, javax/JPA 2.2, EclipseLink 2.7.12, SAP 1.1.1 / Olingo 4.9, Flyway 9.22.3 | Java 8 (52) | http://localhost:8082 |
| app-java17 | Spring Boot 3.5.8, Jakarta/JPA 3.1, EclipseLink 4.0.9, SAP 2.5.0 / Olingo 5 | Java 17 (61) | http://localhost:8080 |
| app-java21 | Same Jakarta stack, compiled and tested with Java 21 | Java 21 (65) | http://localhost:8081 |

All applications query the same PostgreSQL 16.6 database and share the exact
UI, GraphQL schema and Flyway V1 seed. `/api/capabilities` includes the actual
JVM version/vendor; the UI header shows the JVM version.

Java 17 and 21 use one maintained source/test tree in app-java17. The separate
app-java21 Maven module explicitly targets Java 21 and creates its own executable
JAR. It is not a Java 17 JAR merely started on another JVM. Java 8 owns a javax
adapter with ordinary immutable classes instead of records, Java 8 collection
helpers and a Spring GraphQL 1.0 exception resolver. HTTP/query policies, JDBC
EXPLAIN enforcement and shared-core contracts remain equivalent.

SAP 1.1 needs public no-argument entity constructors. Its quoted continuation
literal is accepted only for the UUID token surface and unquoted before token
lookup. Two paging tests and two quoted-token policy tests cover those differences.
Unknown/expired tokens remain rejected; the total result budget is retained.

SAP sources are versioned separately under vendor/sap and vendor/sap-java8.
Their provenance and Apache 2.0 licenses are included. Java 8's framework/SAP
stack is the legacy compatibility adapter; upstream SAP no longer develops its
1.x line. Flyway 9.22.3 reports PostgreSQL 15 as its latest officially tested
major version; this POC's seed/validation was exercised successfully on 16.6.

## Start and stop

From the project directory:

```bash
# Build and start all three JVM variants plus the shared database.
bash scripts/init-local-env.sh
docker compose --profile variants up -d --build --wait

# Preserve movie data while stopping all three applications and the DB.
docker compose --profile variants stop

# Default Java 17 service only.
bash scripts/init-local-env.sh
docker compose up -d --build --wait
```

Application health checks verify HTTP 200 from /api/capabilities, so --wait
waits for a responding API. The DB has a pg_isready health check. Ports bind
only to 127.0.0.1. Default compose startup does not activate the variants profile;
previously running variant containers should be stopped explicitly if needed.

## Reproduce the tests

Docker/OrbStack is sufficient; no host JDK 8 or 21 installation is required:

```bash
bash scripts/verify-matrix.sh
python3 scripts/smoke-matrix.py  # requires all three Compose apps running
```

The matrix script bootstraps both SAP versions and runs Maven clean verify
with the integration profile sequentially under actual JDK 8, 17 and 21.
Sequential builds prevent the shared query-core target directory being written
by different compiler versions concurrently. Maven artifacts are cached in ~/.m2.

For local Maven development with the appropriate JAVA_HOME:

```bash
bash scripts/bootstrap-sap-java8.sh
mvn -Pjava8 -pl app-java8 -am verify -Pintegration

bash scripts/bootstrap-sap.sh
mvn -pl app-java17 -am verify -Pintegration
mvn -Pjava21 -pl app-java21 -am verify -Pintegration
```

`bash scripts/run-mac.sh` is the Java 17 foreground development launcher.

## Verification results

| Actual test JVM | Core checks | Unit tests | PostgreSQL integration tests | Failures / errors / skips |
|---|---:|---:|---:|---:|
| Java 8 (1.8.0_452) | 68 | 47 | 17 | 0 / 0 / 0 |
| Java 17 (17.0.15) | 68 | 43 | 17 | 0 / 0 / 0 |
| Java 21 (21.0.7) | 68 | 43 | 17 | 0 / 0 / 0 |

All three Docker images built successfully. Live Docker JREs were 1.8.0_504,
17.0.20.1 and 21.0.12.1; Maven build images contain different JVM patch versions.
The live smoke suite passed on every JRE for both protocols: selective index
acceptance, actual ix_movies_genre_id in the trace, unindexed ENFORCE rejection,
and a 75-result budget ending with 50 + 25 rows and no further continuation.
The application JAR class versions were inspected as 52 / 61 / 65.

Browser UI verification on the live Java 8 and Java 21 images also passed
OData and GraphQL selective ENFORCE queries with rows and index plans visible.

Machine-readable evidence: java-version-test-summary.json. Existing reports
for the earlier single-module and Java 17 migration stages remain historical.
