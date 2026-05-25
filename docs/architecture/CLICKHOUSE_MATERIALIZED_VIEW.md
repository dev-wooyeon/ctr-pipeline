# ClickHouse Materialized View Design

This document captures the Phase 1 ClickHouse design work that prepares the base table, materialized views, and partitioning strategy required for the Kotlin-native CTR pipeline.

## Base table: `ctr_results_raw`

- **Purpose:** store raw windowed CTR snapshots emitted by the Flink pipeline before aggregation for ML/analytics consumers.
- **Schema:** captures product identifiers, CTR metrics, window bounds, and insertion timestamp to support late-arriving data inspection.
- **Engine & tuning:**
  ```sql
  CREATE TABLE IF NOT EXISTS default.ctr_results_raw (
      product_id String,
      ctr Float64,
      impressions UInt64,
      clicks UInt64,
      window_start DateTime64(3),
      window_end DateTime64(3),
      inserted_at DateTime64(3) DEFAULT now64(3)
  ) ENGINE = MergeTree()
  PARTITION BY toYYYYMM(window_end)
  ORDER BY (window_end, product_id)
  SETTINGS index_granularity = 8192;
  ```
  - Partitioning by month of the window end keeps writes distributed and supports ranged purging.
  - Ordering by `(window_end, product_id)` ensures queries can use stable ranges for window-bound scans.

## Materialized views

### `ctr_ml_view` (ML team view)

- Aggregates into minute-level buckets suitable for ML features while preserving average CTR and counts.
- Uses `AggregatingMergeTree` for incremental stateful aggregation.
  ```sql
  CREATE MATERIALIZED VIEW IF NOT EXISTS default.ctr_ml_view
  ENGINE = AggregatingMergeTree()
  PARTITION BY toYYYYMM(window_end)
  ORDER BY (product_id, window_end) AS
  SELECT
      product_id,
      toStartOfInterval(window_end, INTERVAL 1 MINUTE) AS window_minute,
      avgState(ctr) AS avg_ctr,
      sumState(impressions) AS total_impressions,
      sumState(clicks) AS total_clicks,
      maxState(window_end) AS latest_window
  FROM default.ctr_results_raw
  GROUP BY product_id, window_minute;
  ```
  - `toStartOfInterval` bins windows into minute slots so ML consumers can query stable aggregations.
  - Aggregating states keep intermediate computations compact for real-time updates.

### `ctr_latest_view`

- Keeps the latest CTR snapshot per product to serve dashboards or lightweight queries.
  ```sql
  CREATE MATERIALIZED VIEW IF NOT EXISTS default.ctr_latest_view
  ENGINE = ReplacingMergeTree(window_end)
  ORDER BY product_id AS
  SELECT
      product_id,
      argMax(ctr, window_end) AS latest_ctr,
      argMax(impressions, window_end) AS latest_impressions,
      argMax(clicks, window_end) AS latest_clicks,
      max(window_end) AS window_end
  FROM default.ctr_results_raw
  GROUP BY product_id;
  ```
  - ReplacingMergeTree retains only the latest window per product while enabling efficient point lookups.

## Running the initialization script

The helper script `scripts/init-clickhouse.sh` applies the schema and view definitions by connecting to a ClickHouse instance via `clickhouse-client`:

```bash
CLICKHOUSE_HOST=clickhouse CLICKHOUSE_PORT=9000 CLICKHOUSE_DB=default ./scripts/init-clickhouse.sh
```

- Environment variables default to `clickhouse:9000` and the `default` database, but they can be overridden for staging/production clusters.
- The script runs in `--multiquery` mode so the base table and both materialized views are created in a single execution.

After running the script, the Flink pipeline can safely write to `ctr_results_raw`, and the materialized views keep the ML/team consumers in sync without Redis/serving-api barriers.
