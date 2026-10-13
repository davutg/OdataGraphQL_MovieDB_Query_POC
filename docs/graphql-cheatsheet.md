# GraphQL Cheatsheet

## What it is

**GraphQL** is a query language for APIs and a server-side execution model based on a typed schema. Clients request fields, and resolvers obtain their values. It is independent of a particular database. Facebook originally developed it. The **GraphQL Foundation**, hosted by the **Linux Foundation**, provides a neutral home for its ecosystem. The specification and related collaboration use the Joint Development Foundation within the Linux Foundation family. [GraphQL Foundation](https://www.graphql.org/community/foundation/), [standards collaboration](https://www.graphql.org/community/foundation/join/).

This sheet uses the Movie Query POC schema. Features marked **general only** are not implemented here.

## Send a request

POST JSON to `/graphql`: Java 17 uses port **8080**, Java 21 **8081**, and Java 8 **8082**.

```bash
curl 'http://localhost:8080/graphql' \
  -H 'Content-Type: application/json' \
  -H 'X-Index-Policy: ENFORCE' \
  --data-binary '{"query":"query { movies(first: 10, filter: {genre: \"RareGenre\"}) { nodes { id title } pageInfo { hasNextPage endCursor } } }"}'
```

The JSON envelope accepts `query`, `variables`, and `operationName`. Field names are case-sensitive. GraphQL strings use double quotes. Enum values such as `RATING` are unquoted inside a GraphQL document, but strings in JSON variables.

## Query patterns

### Select fields

```graphql
query {
  movies(first: 10) {
    nodes { id title year rating genre }
  }
}
```

`movies` returns a connection object, so movie fields belong inside `nodes`. This POC uses `nodes` and `pageInfo`, not an `edges` structure.

### Filter and sort

```graphql
query {
  movies(
    first: 10
    filter: { yearFrom: 2000, yearTo: 2020, ratingMin: 8 }
    orderBy: RATING
    descending: true
  ) {
    nodes { id title year rating }
  }
}
```

| Filter input | Example | Meaning |
|---|---|---|
| `id` | `id: "1"` | One numeric movie ID, expressed as GraphQL ID |
| `genre` / `title` | `genre: "RareGenre"` | Exact string match |
| `year` | `year: 2010` | Exact year |
| `yearFrom` / `yearTo` | `yearFrom: 2000, yearTo: 2020` | Inclusive year bounds |
| `rating` | `rating: 8.5` | Exact rating |
| `ratingMin` / `ratingMax` | `ratingMin: 8, ratingMax: 9` | Inclusive rating bounds |

Provided filters combine with **AND**. This schema has no general OR/NOT input or string functions. GraphQL itself does not prescribe filter operators: each service defines them. Orders allowed by policy are `ID`, `YEAR`, `RATING`, and `GENRE`. `TITLE` exists in the enum but policy rejects it. Default order is ascending ID. Other orders add ID as a stable tie-breaker.

### Named operation and variables

```graphql
query FindMovies($filter: MovieFilter, $budget: Int!, $order: MovieOrder!) {
  movies(filter: $filter, first: $budget, orderBy: $order) {
    nodes { id title genre }
    pageInfo { hasNextPage endCursor }
  }
}
```

```json
{
  "filter": {"genre": "RareGenre"},
  "budget": 75,
  "order": "ID"
}
```

Put the document in `query`, this object in `variables`, and `"FindMovies"` in `operationName`. GraphQL validates variable types against the schema. [Official query guide](https://www.graphql.org/learn/queries/).

## Relationships and navigation

Access a relationship by nesting a selection set under its field:

```graphql
query {
  movies(first: 10, filter: { id: "1" }) {
    nodes {
      id
      title
      director { id name }
    }
  }
}
```

There is no OData-style `/Director` URL or `$expand`. The schema exposes `Movie.director` as a singular object. Its child fields are `id` and `name`. There is no `Director.movies` reverse collection or root `director` lookup in this POC.

The resolver joins director data when requested, avoiding one query per movie. Scalar selection controls response fields, but the current SQL resolver still fetches all movie scalar columns. Requesting fewer fields therefore does not necessarily reduce database projection work.

### Fragments reuse selections

```graphql
fragment MovieSummary on Movie {
  id
  title
  director { name }
}
query {
  movies(first: 10) {
    nodes { ...MovieSummary }
  }
}
```

Fragments reuse document fields. They do not bypass depth or complexity limits.

### Aliases return separately named results

```graphql
query {
  rare: movies(first: 5, filter: {genre: "RareGenre"}) {
    nodes { id title }
  }
  drama: movies(first: 5, filter: {genre: "Drama"}) {
    nodes { id title }
  }
}
```

Aliases rename response keys and permit different arguments on the same field. Each movies selection performs work and contributes to the aggregate complexity budget. ENFORCE can reject either plan. An alias does not merge these into one SQL query.

### Conditional relationship selection

```graphql
query Movies($includeDirector: Boolean!) {
  movies(first: 10) {
    nodes {
      id
      title
      director @include(if: $includeDirector) { name }
    }
  }
}
```

Use `{"includeDirector": true}` as variables. Standard `@skip(if: ...)` omits a selection when its condition is true. [Official directives guide](https://www.graphql.org/learn/queries/).

## Query depth and complexity

This example counts selected field levels, starting the root field at one:

```text
movies                       level 1
  nodes                      level 2
    director                 level 3
      name                   level 4
```

`movies / nodes / title` has three levels. `movies / pageInfo / endCursor` also has three. **Selection depth includes connection wrappers and scalar leaves, not just relationship hops.** The director example has one entity relationship hop despite four field levels.

The POC's GraphQL structural limit is `maxExpandDepth + 4`, which defaults to **6**. This differs from OData's syntax depth measurement. The schema has no recursive relationship, so repeated director nesting is invalid even if a numeric depth limit would permit it.

Complexity also limits **breadth and volume**. Each field contributes `1 + child complexity`. A movies selection costs:

```text
20 + max(1, min(first, pageSize)) × (1 + child complexity)
```

The default aggregate ceiling is **2,000**. Aliases and fragments count. A shallow query can still exceed complexity by requesting many aliases or expensive selections. Server-side implementation details: [GraphqlConfig](../app-java17/src/main/java/dev/poc/GraphqlConfig.java).

## Pagination and count

```graphql
query NextPage($cursor: String) {
  movies(first: 75, filter: {genre: "RareGenre"}, orderBy: ID, after: $cursor) {
    nodes { id title }
    pageInfo { hasNextPage endCursor }
  }
}
```

Start with `{"cursor": null}`. If `hasNextPage` is true, reuse the returned cursor with the same filter, order, and direction.

- `first` is a total budget, default 100, maximum 500. Server pages contain at most 50 rows: 75 means at most 50 + 25.
- A cursor binds offset, remaining budget, filter/order fingerprint, and expiry with an HMAC signature. It expires after five minutes by default.
- Continuation cannot enlarge remaining budget. Offsets are bounded at 10,000.
- Add `totalCount` next to `nodes` only when required. It counts matching records independently of the row budget and executes another guarded SQL query.
- Offset pagination does not guarantee a snapshot while data changes.

## Types, operations, and errors

| Syntax / feature | Meaning / POC status |
|---|---|
| `ID`, `Int`, `Float`, `String`, `Boolean` | Built-in scalar types |
| `Movie!` | Non-null Movie |
| `[Movie!]!` | Non-null list containing non-null movies |
| `query` | Read operation, supported |
| `mutation` | Write operation, general only, no mutation root here |
| `subscription` | Ongoing event delivery, general only, no subscription root here |
| Introspection | Schema discovery, disabled here |

Inspect both HTTP status and the JSON `errors` array. GraphQL can return HTTP 200 with errors, potentially alongside partial data. Policy details may appear in `errors[].extensions.code`. Plan reports appear in response `extensions` and through `X-Request-Id` at `/api/requests/{id}`.

The shared defaults include a 3-second database statement cap, 5-second worker timeout, and OFF/AUDIT/ENFORCE plan modes. GraphQL selection flexibility does not replace database safeguards. See [configuration guide](architecture-and-user-guide.md) and the [OData cheatsheet](odata-cheatsheet.md).
