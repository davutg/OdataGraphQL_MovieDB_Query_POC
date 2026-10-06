# Verification — 2026-10-03

The application and SAP dependencies compiled successfully. A full Maven verify run completed with all **52 tests passing** on Java 21.0.12.1 (Temurin) against real PostgreSQL 16.6. The earlier Java 17 run also passed 49 tests before three additional regression/variable tests were added.

| Suite | Tests | Failures | Errors | Test JVM |
|---|---:|---:|---:|---|
| `CursorCodecTest` | 3 | 0 | 0 | 21.0.12.1 |
| `GuardedDataSourceTest` | 8 | 0 | 0 | 21.0.12.1 |
| `ODataPolicyTest` | 18 | 0 | 0 | 21.0.12.1 |
| `RequestGateTest` | 6 | 0 | 0 | 21.0.12.1 |
| `ApiIT` | 17 | 0 | 0 | 21.0.12.1 |

## Database-backed evidence

- Real SAP OData metadata, filter/select/expand/count and server-driven continuation passed.
- GraphQL queries, variables, totals and signed cursor continuation passed.
- Both APIs accepted selective RareGenre queries and captured index scans.
- Both APIs rejected an unindexed Title predicate under ENFORCE; SAP AUDIT returned the result and reported Seq Scan.
- Global count was covered by the same guard and rejected under ENFORCE.
- PreparedStatement parameter replay was verified, including a SQL-looking string value.
- The original JDBC execution was verified not to occur when enforcement rejected the plan.
- PostgreSQL cancelled a deliberately slow statement using statement_timeout.
- Variable-based limits, aliases, allow-lists, signed cursors, wall-clock timeout, concurrent worker rejection and per-IP rate limiting were checked.

## UI evidence

A real Java 21 application JAR was started against PostgreSQL and checked using Chromium 151 and Playwright:

- OData and GraphQL queries returned rows.
- Index scan details appeared in the plan panel.
- ENFORCE rejection appeared in the UI.
- Both protocols continued from 50 rows to a final 25-row page for a 75-row budget.
- A 390 px mobile viewport had no horizontal overflow.
- No JavaScript page errors were observed.

Desktop and mobile screenshots are in `ui-preview.png` and `ui-mobile.png`. They were also visually inspected.

## Scope of validation

Docker is not installed in this execution environment. The Dockerfile/Compose configuration is supplied, but the actual Docker image build was not run here. The source-bootstrap Maven build and application JAR runtime were tested directly. This is a POC: authentication/tenant authorization, distributed token/rate stores, load tests, live-plan guarantees and production deployment are outside the verified scope.

Machine-readable results: `test-summary.json`.
