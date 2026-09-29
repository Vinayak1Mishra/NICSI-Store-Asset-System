# Known limitation: store.uom rows leak on every test run

Status: **open / accepted, not fixed.** Unbounded growth in the test database over
time. Not a correctness bug.

## Summary

`BaseIntegrationTest.TRUNCATE_SQL` does not include `store.uom`. Every integration test
that creates a UOM therefore leaves its row behind in `nicsi_store_test` permanently.
The table grows monotonically across runs and is never reclaimed.

## Why it is not truncated

The truncate list contains `store.item` with the comment `-> item_category,
item_subcategory, uom`, which records the FK direction: `store.item` references
`store.uom`, so truncating `item` removes the *references* to UOMs. It does not remove
the UOM rows themselves. `store.uom` never appears as a truncate target.

`truncateInventoryTables()` is called by GrnInspectionIntegrationTest,
GrnPostStockAliasIntegrationTest, GrnToStockPostingIntegrationTest,
ReadEndpointCoverageIntegrationTest and StockAdjustmentAndReversalIntegrationTest, so
the omission affects the shared base class, not one test.

## Why this is not a correctness bug

`AssetLifecycleIntegrationTest` was the only casualty. It derived its per-run tag as:

```java
String tag = "LFC" + System.nanoTime() % 100000;
```

That is a 100,000-value namespace, and `System.nanoTime()` is a monotonic counter from an
arbitrary JVM origin rather than a wall clock, so its value modulo 100k repeats across
separate JVM runs. Combined with UOM rows that are never cleaned up, the
`uom_uom_code_key` unique constraint eventually collides and the test fails with
`DataIntegrityViolationException`. This was observed, not theoretical.

That specific failure is now fixed: the tag is derived from
`UUID.randomUUID().toString().substring(0, 8)`, giving roughly 4.3 billion values, which
matches the convention already used by `IssueWorkflowIntegrationTest`. See commit
`bfb6603`.

Other tests that create UOMs and use a similarly narrow tag are still exposed in
principle, though none have failed so far.

## Real fix (out of scope here)

Adding `store.uom` to `TRUNCATE_SQL` would close the leak, but it changes a shared base
class used by five other test classes and interacts with the row-level immutability
triggers that made `TRUNCATE` (rather than `DELETE`) the chosen mechanism. That warrants
its own change with a full suite run.

The simpler operational remedy is to reset `nicsi_store_test` from scratch periodically.
`mvnw.cmd clean` only removes `target/`; it does not touch the database, so nothing in
the Maven lifecycle bounds this growth.
