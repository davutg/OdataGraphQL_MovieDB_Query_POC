# Stateless API replicas and multi-pod deployment

The OData and GraphQL HTTP services can run behind a load balancer without sticky sessions. Continuation survives a pod change or restart when replicas use the same signing key, database, compatible code, and policy configuration. PostgreSQL remains stateful: it stores movie data, shared rate counters, and diagnostic reports. Stateless API replicas do not mean a system without stored data.

## Start locally

From the repository root:

```sh
bash scripts/init-local-env.sh
docker compose --profile variants up -d --build --wait
```

The script creates a cryptographically random signing key in the ignored `.env` file with restrictive permissions, preserving an existing key. Docker build contexts exclude that file. Compose passes the same key to all three Java variants and activates `cluster` mode. Missing or invalid keys fail startup rather than creating incompatible replicas.

## Configure containers or Kubernetes pods

Supply these values to every replica of a service:

| Setting | Value / purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `cluster` (append to any other required profiles) |
| `CURSOR_SECRET_BASE64` | The same Base64-encoded secret of at least 32 random bytes; inject from a Kubernetes Secret or equivalent |
| `CURSOR_TTL_MS` | Default `300000`; applies to both protocols |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | The same PostgreSQL database and appropriate credentials |
| `STATE_NAMESPACE` | Default `movie-query`; identical within a service, distinct between unrelated services |
| Query-policy settings | Identical across replicas, including page, timeout, rate and index-policy limits |

Flyway migration `V2__shared_request_state.sql` creates the operational tables. No Redis or HTTP session store is required. The database must support PostgreSQL JSONB and atomic conflict updates. A Kubernetes Deployment may have multiple replicas; route requests through a normal Service without affinity. Compose exposes each runtime separately for verification; it is not a Kubernetes deployment manifest or a load-balancer test.

Keep clocks synchronized because token expiry is checked on the receiving replica. Coordinating a key replacement invalidates outstanding cursors; multiple active signing keys are not implemented. Old UUID-based OData continuations are incompatible with this release. Finish or restart paging during that upgrade.

## What stays where

| Information | Location / lifetime |
|---|---|
| OData query options, remaining budget, offset and page size | Signed continuation token, expiring after the configured TTL |
| GraphQL query fingerprint, remaining budget and offset | Signed cursor, expiring after the configured TTL |
| Rate counters | PostgreSQL, keyed by namespace and remote address, with atomic admission in 60-second windows |
| Request reports | PostgreSQL JSONB, readable from every replica for five minutes |
| Request context, ORM entity manager and buffered response | Only during the current request |
| Worker concurrency limit and connection pool | Per pod, protecting that pod's resources |

`ODataContinuation` verifies and restores the signed request before SAP/Olingo parses it, then revalidates the restored options against current policy. The paging provider issues the next token without retaining an in-memory page. GraphQL already uses a self-contained cursor; shared key configuration makes it portable. Each next page receives a fresh expiry, and neither mechanism provides a database snapshot or single-use tokens.

`SharedRequestStore` uses infrastructure SQL for atomic rate admission and report retention. Movie/domain queries still use Querydsl/JPA or SAP JPA; this does not bring back handwritten movie query translation. Operational-store failure does not fall back to independent per-pod quotas. Database work for admission/reporting is outside the query worker’s request deadline. Existing connection-pool acquisition and PostgreSQL statement timeouts still apply; include this overhead when sizing end-to-end timeouts.

The limiter currently identifies clients using the servlet remote address. Behind an ingress this can be the proxy address, giving its clients a shared quota. Configure trusted proxy handling or introduce an authenticated client/tenant key before relying on per-user quotas. Do not simply trust arbitrary forwarded headers.

Tokens are signed, not encrypted. OData tokens include filter values, so treat next links with the same care as original query URLs. Tokens are capped at 24,000 characters and the application allows continuation query strings up to 32,768 characters; HTTP connector and ingress request-line/header limits may reject large links earlier. Align those limits or restrict accepted query sizes. Ordinary queries remain capped at 8,192 characters.

Outside the `cluster` profile, standalone development may use a random startup key and local rate/report maps. That mode is deliberately unsuitable for replicated deployment. Request reports are diagnostic POC endpoints; access control and tenant scoping must be supplied when integrating this exposure layer into authenticated services.

## Verification

`bash scripts/verify-matrix.sh` runs contracts, unit tests and PostgreSQL integration tests on actual JDK 8, 17 and 21. New cases cover signature/expiry/key failures, restored-policy checks, and concurrent admission across independently constructed stores. `python3 scripts/smoke-cluster.py --restart` exercises cross-runtime continuation and report retrieval, then restarts the Java 17 container and follows previously issued cursors. It changes no movie data.

Recorded results: [stateless verification](stateless-verification.json). All three JDKs passed 68 core contract checks each; unit tests passed 53/49/49 and integration tests passed 24 per runtime. Live cross-instance continuation, restart continuity, shared reports, signature rejection, and existing index-policy checks passed.
