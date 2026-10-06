# Index policy: what is checked

The interception point is DataSource → Connection → JDBC PreparedStatement execution. This is downstream of SAP's OData-to-JPA translation and EclipseLink's SQL compilation; the policy therefore sees generated SQL, not an assumed filter-to-index mapping. GraphQL uses the same DataSource.

1. Validate API input and query complexity before generating database work.
2. Apply statement_timeout using the remaining HTTP processing budget.
3. When enabled, run EXPLAIN (FORMAT JSON, VERBOSE TRUE) with the actual bound parameters.
4. Traverse every plan node. Record relation, scan type, index name and root Total Cost.
5. Reject Seq Scan / Parallel Seq Scan when pg_class.reltuples is unknown or >= min-table-rows. Also reject root Total Cost above max-plan-cost.
6. In AUDIT, record the violation and continue. In ENFORCE, throw before the original statement executes. A plan inspection failure is fail-closed in ENFORCE.
7. Reapply the remaining statement deadline before actual execution.

## Why it does not demand an index for every scan

Scanning five directors is usually cheaper than using an index. A selective RareGenre filter over 100,010 movies is a different case. The policy uses whole-relation statistics, not the estimated number of output rows of the scan, so a selective unindexed filter cannot evade the threshold merely by returning one movie.

Bitmap Heap Scan may be efficient when driven by Bitmap Index Scan. A query containing both an indexed branch and a large sequential branch can still be rejected. A global count can legitimately require scanning most rows, even when all requested result pages are small; include totalCount/$count only when needed.

## Limits

- EXPLAIN is a planner estimate. It does not prove the eventual runtime execution path.
- prepareThreshold=0 reduces generic prepared-plan mismatch. Other session settings and live statistics still matter.
- reltuples needs ANALYZE; stale statistics can change both the selected plan and the threshold decision.
- min-table-rows and max-plan-cost are policy thresholds, not portable latency predictions.
- No automatic index creation and no enable_seqscan=off. Inspect real workloads and create justified indexes through migrations.
- There is extra planning/metadata-query overhead. OFF incurs no EXPLAIN; AUDIT is useful while tuning; ENFORCE is appropriate only when legitimate queries have been characterized.
- The proxy is intended for the generated SELECT statements in this read-only POC, not as a general firewall for arbitrary JDBC SQL, streaming parameters, stored procedures or batch APIs.
- Client mode selection is a local demonstration feature. Disable it to prevent weakening centrally enforced policy.

## Experiments

```sql
EXPLAIN (FORMAT JSON, VERBOSE TRUE)
SELECT id,title FROM movies WHERE genre='RareGenre' ORDER BY id LIMIT 10;

EXPLAIN (FORMAT JSON, VERBOSE TRUE)
SELECT id,title FROM movies WHERE title='Inception' ORDER BY id LIMIT 10;

EXPLAIN (FORMAT JSON, VERBOSE TRUE)
SELECT count(*) FROM movies;
```

The first query has a suitable composite index (genre,id). The second intentionally has no title index. Running through the API demonstrates the actual SAP-generated / GraphQL-generated SQL plans rather than only these representative SQL examples.
