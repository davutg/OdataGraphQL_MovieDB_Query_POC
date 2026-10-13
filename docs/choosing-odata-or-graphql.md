# Choosing OData or GraphQL

A practical decision guide for Movie Query POC · 13 October 2026

**Choose OData when consumers need a consistent way to browse and query business entities. Choose GraphQL when applications need a deliberately designed graph of data, especially across several backend capabilities.** Both can reduce endpoint proliferation when paired with a suitable persistence integration. Neither protocol automatically supplies efficient database access or application security.

This guide distinguishes protocol facts, observations from this repository, and architectural recommendations. The user journeys below are illustrative scenarios, not results from a user study or measured performance comparisons. Sources are official specifications and project documentation.

## 1. Decide from the experience you need

| Consumer experience | Suggested starting point | Reason and tradeoff |
|---|---|---|
| A back-office grid needs filtering, ordering, selected columns and related records | OData | Reuse a common query vocabulary across entity sets; check that the chosen implementation supports the required options. |
| A partner integrates a catalogue using an existing OData client | OData | Metadata and established URL conventions can reduce integration-specific learning. |
| Mobile and web screens need different subsets of the same graph | GraphQL | Each operation selects fields for that screen. OData also supports field selection, so this alone is not decisive. |
| A screen combines catalogue, availability and user-specific recommendations | GraphQL | A product-oriented schema can compose these sources; resolvers and service boundaries still need implementation. |
| A mostly relational domain should be exposed with minimal handwritten query code | OData/JPA or GraphQL with repository integration | Compare the integrations, not just the protocols. Measure how much work a second entity requires. |
| An existing API already serves one simple, stable workflow | Keep the existing API unless there is a concrete gap | Adding another protocol creates maintenance work without an automatic user benefit. |

These are recommendations inferred from the capabilities described below. End users experience loading time, relevant information, stable paging and understandable errors; they rarely care which query language produced them.

## 2. What OData standardizes

OData is the Open Data Protocol, standardized by OASIS. It defines REST-based data services around an Entity Data Model. “Open” refers to the protocol, not a requirement to expose public or unauthenticated data. [OData overview](https://www.odata.org/).

Its URL conventions give clients a shared vocabulary: `$filter`, `$orderby`, `$select`, `$expand`, `$top` and `$skip`. Navigation paths address related resources; expansion includes related data in a response. These capabilities are more standardized than application-specific parameters such as `sortBy` or `minRating`. [OASIS URL conventions](https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html).

For example, a movie grid can request:

```http
GET /odata/Movies?$filter=Rating ge 8&$select=Id,Title,Rating&$expand=Director($select=Name)&$orderby=Rating desc&$top=20
```

The user can change a grid filter or column set without a new endpoint for every combination. That benefit depends on the client and server implementing the relevant options. The POC intentionally allows only a subset; the [OData cheatsheet](odata-cheatsheet.md) identifies those boundaries.

**Implementation benefit in this project:** SAP's OData JPA Processor derives metadata from JPA metadata and translates reads into Criteria queries. That integration removes much of the entity-by-entity controller and SQL work. EclipseLink supplies the underlying ORM. [SAP processor documentation](https://github.com/SAP/olingo-jpa-processor-v4).

## 3. What GraphQL standardizes

GraphQL defines typed fields, arguments and executable operations. A client selects a nested response shape, uses variables, reuses fragments and can rename response keys with aliases. The particular filter arguments and order enums are application schema decisions; GraphQL does not prescribe an OData-like universal filter vocabulary. [GraphQL queries](https://graphql.org/learn/queries/).

For the POC's movie schema:

```graphql
query MovieCards {
  movies(first: 20, filter: {ratingMin: 8}) {
    nodes {
      heading: title
      director { name }
    }
    pageInfo { hasNextPage endCursor }
  }
}
```

Here `heading` is an alias for the existing `title` field. It does not calculate a new value. A client can omit `director` on a compact screen without defining a separate compact-movie endpoint. Selection alone does not guarantee that the persistence layer fetches only those columns.

Resolvers supply values for fields, and may obtain them from databases, services or existing objects. This separation lets an API schema represent product concepts rather than reproduce storage tables. [GraphQL execution](https://graphql.org/learn/execution/).

## 4. Is GraphQL better for a BFF?

**Often, yes: GraphQL is a useful fit for a Backend for Frontend when the frontend needs composition and a stable, product-oriented contract.** This is an architectural recommendation, not a requirement of GraphQL or an exclusive capability.

Consider a movie-detail page with title, director, regional availability and a personalized recommendation reason. A GraphQL schema could expose those concepts together, even if three services own them. The client selects its desired fields in one operation. The server still implements the composition; one HTTP request can produce multiple backend requests.

Distinguish three kinds of “response transformation”:

| Need | What provides it |
|---|---|
| Return only chosen fields and relationships | GraphQL field selection; OData has `$select` and `$expand` too. |
| Rename `title` to `heading` in this response | A GraphQL alias changes the response key. |
| Compute `displayTitle`, convert money, merge services or apply business rules | Server-side fields/resolvers and domain services must implement the behavior. |

GraphQL is not a client-supplied arbitrary transformation program. A client cannot invent `displayTitle` unless the schema exposes it. OData can also expose modeled computed properties and operations; using it does not require publishing raw tables. [GraphQL execution model](https://graphql.org/learn/execution/), [OData operations and extensibility](https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html).

For a BFF, keep business rules in reusable services and use the API layer to compose and present them. If each screen needs unrelated bespoke rules, a GraphQL schema alone will not remove that work.

## 5. Does OData have a state problem?

**OData does not inherently require an in-memory user session.** Its server-driven paging contract returns a next link that clients follow without interpreting or reconstructing it. The specification does not mandate our particular token store. ETags concern resource versions and conditional operations; they are not proof of a conversational session. [OASIS protocol: server-driven paging and HTTP headers](https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html).

There is, however, a concrete state limitation in this POC:

| POC continuation mechanism | Operational consequence |
|---|---|
| OData: UUID tokens refer to an in-process map; entries expire after five minutes and the store is capped at 1,000 | A restart, eviction or request reaching another independent instance can invalidate continuation. |
| GraphQL: signed cursor includes offset, remaining budget, query fingerprint and expiry | No token lookup is required, but instances need compatible validation and the same signing secret. A randomly generated startup secret invalidates earlier cursors after restart. |

These are implementation choices visible in [ODataPaging](../app-java17/src/main/java/dev/poc/ODataPaging.java), [CursorCodec](../query-core/src/main/java/dev/poc/core/CursorCodec.java) and [CoreConfig](../app-java17/src/main/java/dev/poc/CoreConfig.java). A shared continuation store or a suitable self-contained token design could change the OData deployment behavior. GraphQL could equally be implemented with stored cursors.

Neither token design creates a database snapshot. Both POC flows use offsets, so concurrent changes can move records between pages. A signed cursor is tamper-resistant; it is not automatically keyset pagination or repeatable-read consistency. Cursor and connection patterns are API design choices. [GraphQL pagination guidance](https://graphql.org/learn/pagination/).

## 6. Persistence integration determines how effortless exposure feels

A fair comparison is **OData plus its JPA integration versus GraphQL plus its repository/persistence integration**.

The earlier GraphQL adapter in this project manually assembled SQL and nested results through `JdbcTemplate`. That was a property of the adapter, not a GraphQL requirement. The current Querydsl/JPA adapter reuses entity mappings and removes SQL strings and manual row mapping. A reusable connection wrapper retains the POC’s budget cursors; it is not native `QuerydslDataFetcher` auto-registration or automatic schema generation. The JDBC baseline is preserved on `archive/jdbc-template` at `401fd16`.

Spring GraphQL supports Querydsl and Query-by-Example repository data fetchers, including eligible repository auto-registration. Querydsl integration can bind GraphQL arguments to predicates and work with supported Spring Data repositories. That is useful infrastructure, but not a promise that every existing schema, range filter, nested relation or custom cursor contract is generated automatically. [Spring GraphQL data integration](https://docs.spring.io/spring-graphql/reference/data.html).

Querydsl itself is a query construction layer; JPA/EclipseLink provides ORM behavior in this relational application. Familiarity with Querydsl on MongoDB transfers at the predicate/API level, while persistence mappings and supported queries remain store-specific. The exact integration and compatibility code are documented in the [developer guide](developer-implementation-guide.md).

Our recommendation is to judge the migration by adding another entity: how many new files, field mappings and custom query branches are necessary? A short controller that delegates to a large entity-specific helper has not necessarily reduced implementation.

## 7. Caching, errors and perceived performance

OData's resource and query URLs fit common HTTP caching workflows. Correct cache headers, authorization boundaries and invalidation are still application responsibilities. GraphQL also supports HTTP caching, including suitable query operations sent with GET and persisted-operation approaches. It is inaccurate to say “GraphQL cannot be cached.” The current POC GraphQL endpoint accepts POST only, so GET-based caching is not currently exposed. [GraphQL performance guidance](https://graphql.org/learn/performance/).

A GraphQL response may contain both data and field errors. This can support a partially useful screen, but the UI must inspect `errors` rather than treat HTTP 200 as complete success. Whether to show a partial movie page is a product decision. [GraphQL response behavior](https://graphql.org/learn/response/).

Neither protocol guarantees fewer database queries or lower latency. ORM loading strategy, joins, batching, indexes and counts determine much of the actual work. A deeply nested request can be expensive even when the returned JSON is small.

## 8. Safeguards remain necessary with either choice

The POC keeps these responsibilities outside entity-specific SQL construction:

- Limit result budgets and offsets, and bind continuations to their query.
- Restrict permitted fields, operators and relationship traversal.
- Bound query depth and complexity; depth alone does not bound a wide query.
- Limit request time, database statement time, concurrency and request rate.
- Inspect estimated database plans, rejecting excessive cost or large sequential scans when enforcement is enabled.

These are local implementation facts, described in the [implementation guide](developer-implementation-guide.md). PostgreSQL plan cost is an estimate, not elapsed milliseconds. Count queries and ORM-generated relationship queries need protection too. GraphQL's own guidance likewise discusses pagination, depth, breadth, rate and complexity controls. [GraphQL security guidance](https://graphql.org/learn/security/).

For this project, favor OData for reusable entity browsing and GraphQL for a curated application graph. Preserve a common persistence model where appropriate and a common safeguard layer across both. Expose both protocols when there are consumers for both—not simply because the libraries make it possible.
