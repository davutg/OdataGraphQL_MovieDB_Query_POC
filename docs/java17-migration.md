# Java 17 migration — 2026-10-05

Stage 2 implementation and full reactor/PostgreSQL validation are complete.
See the local Mac verification below; historical session details remain for provenance.

## Changes

- Root Maven reactor: query-core → app-java17. Core remains independently buildable,
  targets Java 8 and has no framework/runtime dependencies.
- Application Java sources, resources and tests moved to app-java17.
- Spring Limits binds configuration and delegates validation/TOP/OFFSET to core Limits.
- Core CursorCodec and PolicyException replace the duplicated application classes.
- OData filter/order fields, expansion depth and complexity use shared QueryPolicy.
  GraphQL parsed fields and weighted complexity use the same policy.
- Both protocols use shared page budgets, including configured offset protection and
  preferred OData page sizes; SAP UriInfo/token storage stays in the adapter.
- JDBC EXPLAIN/row-statistics extraction remains in the application; estimated-plan
  acceptance and reasons now come from IndexPolicyEvaluator. Missing plan cost and
  NULL row statistics fail closed in ENFORCE.
- GraphQL cursor key/TTL can be injected with CURSOR_SECRET_BASE64/CURSOR_TTL_MS.
  Keys must decode to at least 32 bytes. The default key is still process-local.
- Docker build/runtime updated to JDK/JRE 17 and reactor output paths.
- Core contract mains are wired into Maven's test phase. Eight application regression
  tests added for adapter delegation, shared keys, invalid configuration and plan data.

## Verification performed in this session

| Check | Result |
|---|---|
| Core compilation, --release 8, all warnings as errors | PASS |
| Original core contract checks | 24 PASS |
| Extended core contract checks | 44 PASS |
| Java 17 syntax parsing, application + test sources | 26 source files PASS |
| XML reactor descriptors, module paths, core dependency, duplicate removal | PASS |
| Full Maven reactor compilation and unit tests | NOT RUN |
| PostgreSQL API/planner tests | NOT RUN |
| Docker image build | NOT RUN |
| Actual Java 8 runtime execution | NOT RUN |

Core tests executed on OpenJDK 17.0.20. Syntax parsing does not resolve dependencies
or types and is not evidence of a successful application compilation. The environment
has no Maven or PostgreSQL, and attempts to obtain them/dependencies timed out.
The earlier 52-test report in verification.md belongs to the prior single-module
release; it does not validate this migration.

## Complete validation on a configured machine

Requires JDK 17, Maven 3.9+ and PostgreSQL 16 (or Docker for the DB):

```bash
bash scripts/verify-java17.sh        # builds vendored SAP, core contracts and unit tests
docker compose up -d db
mvn verify -Pintegration            # real SAP OData/GraphQL and PostgreSQL plan tests
mvn install -DskipTests             # install reactor modules for spring-boot:run
mvn -pl app-java17 spring-boot:run
```

In PowerShell, run scripts/bootstrap-sap.ps1 before the Maven commands.
The packaged application is app-java17/target/movie-query-poc-1.0.0.jar.
`mvn verify` includes core contract mains via exec-maven-plugin, in addition to
application Surefire tests. `-Pintegration` adds app-java17 Failsafe API tests.

The release gate is a successful full reactor build and API suite. Resolve any failures
before treating stage 2 as verified or proceeding to app-java8. Stage 3 will introduce
its own compatible Spring/SAP adapters rather than forcing Jakarta dependencies into core.

## Local Mac verification — 2026-10-06

Environment: macOS arm64, SDKMAN Temurin 17.0.18, Maven 3.9.12, OrbStack
Docker 29.4.0 and PostgreSQL 16.6 (arm64). Java 25 is installed as the system
default; this run explicitly selected the SDKMAN Java 17 installation.

- Vendored SAP source bootstrap: PASS.
- `mvn verify`: PASS — 68 core contract checks and 43 application unit tests.
- `mvn verify -Pintegration`: PASS — the same contracts/unit tests plus 17 API
  integration tests; zero failures/errors/skips.
- Actual JAR startup on Java 17 against the Compose database: PASS.
- Browser UI: OData and GraphQL selective index queries return HTTP 200 with
  `ix_movies_genre_id` in the plan. OData unindexed Title with ENFORCE returns
  HTTP 422; GraphQL first=501 returns HTTP 422.
- OData and GraphQL UI continuation: budget=75 returns 50 rows, then 25; continuation
  is disabled after the final page.

- Docker image build using the project Dockerfile: PASS.
- `docker compose up -d --wait`: PASS. Both containers remain running locally;
  `/api/capabilities` returns HTTP 200 from the Docker application.

`bash scripts/run-mac.sh` selects JDK 17, waits for the Compose database,
stops the Compose app to free port 8080, builds SAP and the reactor, and runs
the JAR in the foreground. Ctrl-C stops
the application; `docker compose stop db` stops PostgreSQL without deleting data.

Java 8 application adapter and the separate Java 21 module were subsequently
implemented and verified on actual JVMs. See [java-versions.md](java-versions.md)
for the completed stage and test evidence.
