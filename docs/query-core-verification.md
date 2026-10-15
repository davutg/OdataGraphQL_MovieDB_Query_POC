# Shared query core — stage 1 complete

> Historical verification snapshot. For the current shared-key and stateless continuation deployment, see [multi-pod deployment](multi-pod-deployment.md).


Standalone Java 8-targeted module without external runtime dependencies. No Spring,
Jakarta, servlet, GraphQL, SAP, JSON or JDBC types enter the public contract.

Implemented contracts:
- Immutable limits, TOP/OFFSET boundaries and zero expansion-depth configuration.
- Defensive filter/order allow-lists, expansion-depth and long-valued complexity budgets.
- Server page sizing, remaining TOP budget and continuation offsets, including short-page
  termination, zero results and configured offset/overflow protection.
- Optional OFF/AUDIT/ENFORCE selection subject to server authorization.
- HMAC query-bound cursors with injectable key, clock and TTL. Default keys are process-local;
  deployments can inject the same secure 32-byte-or-longer key across instances. Keys are copied.
  Expiry is exclusive: a cursor is invalid at its expiry instant.
- Estimated-plan index evaluation: large/unknown sequential scans and excessive cost are
  rejected in ENFORCE, reported in AUDIT and ignored in OFF. Decision reasons are immutable.

## Verification

Run `sh query-core/verify.sh` from the project root. JDK 9+ is required to compile
with `--release 8`; JAVA/JAVAC environment variables can select executables.
Compilation treats all warnings as errors and cleans previous compiled contracts.

Verified on 2026-10-05 with OpenJDK 17.0.20:
- Java 8 language and standard-library API compilation: PASS (`--release 8`).
- Original contracts: 24 PASS.
- Extended contracts: 44 PASS.
- Total: 68 checks PASS, zero compilation warnings.

This is Java 8 target/API verification and Java 17 execution. Actual Java 8 runtime
execution has not been performed. After compilation, run both contract mains with
JDK 8 to complete that runtime check.

`mvn -f query-core/pom.xml package` builds the library. The dependency-free contract
mains are run by verify.sh and by exec-maven-plugin in the Maven test phase, not Surefire.
Maven execution was not performed in this environment.

## Adapter responsibilities and next stage

The reactor parent and app-java17 adapters now consume this module. Application
verification is pending; see ../docs/java17-migration.md. Stage 3 will add app-java8
with compatible dependencies after Java 17 integration validation.

Adapters parse OData/GraphQL syntax, collect every nested filter/order field and measure
actual depth/complexity before calling QueryPolicy. They bind cursors to a canonical
query plus any authorization/tenant scope, enforce limits again on decoded continuation
values, append deterministic ordering, and omit continuation after a short/final page.
A Page describes the requested page; remaining(returned) reflects actual rows consumed.
Rate/concurrency gates, request and DB timeout enforcement, SQL binding, EXPLAIN extraction,
HTTP mapping and key provisioning remain adapter concerns. Index evaluation is an estimate,
not a guarantee that the executed DB query uses an index.
