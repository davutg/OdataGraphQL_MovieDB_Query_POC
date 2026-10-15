# OData V4 Cheatsheet

## What it is

**OData (Open Data Protocol)** defines REST-based data services using HTTP, resource URLs, an Entity Data Model, and standard query options. Clients can request different filters and projections through a consistent resource contract. “Open” describes the protocol, not a requirement to make data public. OData is standardized by **OASIS**, the Organization for the Advancement of Structured Information Standards, an international standards organization. [OASIS protocol specification](https://docs.oasis-open.org/odata/odata/v4.0/odata-v4.0-part1-protocol.pdf), [OASIS OData Technical Committee](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=odata).

This sheet targets the POC's restricted **V4** surface. General OData features below require service support.

## Connection and names

| Java | Base URL |
|---|---|
| 17 | `http://localhost:8080/odata` |
| 21 | `http://localhost:8081/odata` |
| 8 | `http://localhost:8082/odata` |

Movie fields: **Id, Title, Year, Rating, Genre**. Navigation property: **Director**, with **Id, Name**. Names are case-sensitive. Examples show readable URLs; percent-encode spaces, quotes, and reserved characters when sending requests. Use `curl --get --data-urlencode` to handle encoding.

## Query patterns supported by the POC

All examples use GET and paths relative to the server origin.

| Goal | Request |
|---|---|
| Service document | `/odata/` |
| Discover the model | `/odata/$metadata` |
| Collection | `/odata/Movies?$top=10` |
| One movie by key | `/odata/Movies(1)` |
| Choose fields | `/odata/Movies?$select=Id,Title,Year&$top=10` |
| String equality | `/odata/Movies?$filter=Genre eq 'RareGenre'&$top=10` |
| Numeric comparison | `/odata/Movies?$filter=Rating ge 8.5&$top=10` |
| Range | `/odata/Movies?$filter=Year ge 2000 and Year le 2020&$top=10` |
| Boolean grouping | `/odata/Movies?$filter=(Genre eq 'Drama' or Genre eq 'Sci-Fi') and Rating ge 8&$top=10` |
| Sort with a tie-breaker | `/odata/Movies?$orderby=Rating desc,Id asc&$top=10` |
| Initial offset | `/odata/Movies?$skip=50&$top=10` |
| Count with rows | `/odata/Movies?$filter=Genre eq 'RareGenre'&$count=true&$top=10` |
| Count only | `/odata/Movies/$count?$filter=Genre eq 'RareGenre'` |
| Include a director | `/odata/Movies?$expand=Director&$top=10` |
| Choose director fields | `/odata/Movies?$select=Id,Title&$expand=Director($select=Id,Name)&$top=10` |

### Filter operators

| Operator | Meaning | Example |
|---|---|---|
| `eq` / `ne` | Equal / not equal | `Genre eq 'Drama'` |
| `gt` / `ge` | Greater than / at least | `Rating ge 8` |
| `lt` / `le` | Less than / at most | `Year lt 2000` |
| `and` / `or` | Combine comparisons | `Year ge 2000 and Rating ge 8` |
| `( ... )` | Explicit grouping | `(Genre eq 'Drama' or Genre eq 'Comedy')` |

Strings use single quotes. Escape an apostrophe by doubling it: `Title eq 'Schindler''s List'`. Numbers are unquoted. The POC accepts filters on Id/Title/Year/Rating/Genre and ordering on Id/Year/Rating/Genre. Title ordering fails policy.

## Navigation properties: access versus expansion

A navigation property represents a relationship, not an ordinary scalar field. **`$select` chooses fields. `$expand` includes related entities.** Select director fields inside the expansion:

```http
GET /odata/Movies(1)?$select=Id,Title&$expand=Director($select=Name)
```

Conceptually, the response contains a movie with a nested Director object:

```json
{"Id": 1, "Title": "...", "Director": {"Name": "..."}}
```

This is a shape illustration, not a complete OData response. Services can include metadata and keys beyond the requested projection.

**Standard patterns, outside this POC's exposed routes:**

```http
GET /odata/Movies(1)/Director
GET /odata/Movies(1)/Director/Name
GET /odata/Movies?$filter=Director/Name eq 'Christopher Nolan'
```

The first follows the relationship, the second accesses its scalar property, and the third filters using a related property. This POC rejects direct navigation routes and navigation filters. Use `$expand=Director` to retrieve the director here. There is no exposed reverse `Director/Movies` collection.

For a general service with a collection relationship, nested options refine the related collection. Semicolons separate options inside parentheses:

```http
GET /Directors?$expand=Movies($filter=Rating ge 8;$orderby=Year desc;$top=5;$select=Title,Year)
```

This is **general syntax only**. The POC exposes neither that Directors route nor nested collection options. [OASIS URL conventions](https://docs.oasis-open.org/odata/odata/v4.02/odata-v4.02-part2-url-conventions.html).

## Query depth

Relationship depth counts navigation hops conceptually:

```text
Movie                              depth 0
Movie / Director                   depth 1
Movie / Director / Studio           depth 2, hypothetical model
```

General nested expansion could be `$expand=Director($expand=Studio)`. Recursive relationships can use `$levels=3` or `$levels=max` where the service supports them and imposes suitable limits. This POC supports neither Studio, nested `$expand`, nor `$levels`. [OASIS expansion rules](https://docs.oasis-open.org/odata/odata/v4.02/odata-v4.02-part1-protocol.html).

**POC depth is a conservative syntax measurement:**

| Expression | POC depth |
|---|---:|
| No `$expand` | 0 |
| `$expand=Director` | 1 |
| `$expand=Director($select=Name)` | 2 |

The default maximum is 2. The actual model has one relationship hop. Filter parentheses have a separate maximum nesting of 8. Depth alone does not measure database expense: row volume and plan cost also matter.

## Pagination and safeguards

- `$top` is the total row budget, default 100 and maximum 500. Server pages contain at most 50 rows. `$top=75` therefore yields at most 50 + 25.
- Follow `@odata.nextLink` **exactly**. A continuation uses `$skiptoken` alone. Preserve quoted tokens returned by the Java 8 adapter.
- OData tokens expire after five minutes by default (`CURSOR_TTL_MS`). They are self-contained and signed: replicas and restarts can validate them with the same configured key.
- `$skip` and continuation offsets cannot exceed 10,000. Offset pagination does not preserve a snapshot during concurrent data changes.
- `X-Index-Policy: OFF | AUDIT | ENFORCE` selects plan behavior when the server permits selection. ENFORCE rejects expensive/unverifiable plans and large-table sequential scans.
- Counts receive plan checks, including OData's internal paging count even without `$count=true`.
- Other defaults: complexity 2,000, database statement cap 3 seconds, worker timeout 5 seconds.

```bash
curl --get 'http://localhost:8080/odata/Movies' \
  -H 'X-Index-Policy: ENFORCE' \
  --data-urlencode "\$filter=Genre eq 'RareGenre'" \
  --data-urlencode '$select=Id,Title' \
  --data-urlencode '$expand=Director($select=Name)' \
  --data-urlencode '$top=10'
```

**Outside the POC:** writes, `$batch`, `$apply`, functions such as `contains`, lambda `any/all`, navigation filters, and recursive expansions. OData standardization does not mean every service enables every feature.

For active limits use `/api/capabilities`. For plan reasons use `X-Request-Id` with `/api/requests/{id}`. See [configuration guide](architecture-and-user-guide.md).

## Multiple pods

Both protocols use signed, self-contained continuation tokens. With the `cluster` profile, a shared signing key and database, paging and request reports work across replicas without sticky sessions. See [deployment settings](multi-pod-deployment.md).
