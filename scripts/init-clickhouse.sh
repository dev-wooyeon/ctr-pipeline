#!/bin/bash
set -euo pipefail

CLICKHOUSE_HOST="${CLICKHOUSE_HOST:-clickhouse}"
CLICKHOUSE_PORT="${CLICKHOUSE_PORT:-9000}"
CLICKHOUSE_DB="${CLICKHOUSE_DB:-default}"

echo "Initializing ClickHouse schema for CTR pipeline at ${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/${CLICKHOUSE_DB}"

CLICKHOUSE_CMD=("docker" "compose" "exec" "-T" "clickhouse" "clickhouse-client" "--host" "$CLICKHOUSE_HOST" "--port" "$CLICKHOUSE_PORT" "--multiquery")

("${CLICKHOUSE_CMD[@]}") <<SQL
CREATE TABLE IF NOT EXISTS ${CLICKHOUSE_DB}.ctr_results (
    product_id String,
    ctr Float64,
    impressions UInt64,
    clicks UInt64,
    window_start UInt64,
    window_end UInt64
) ENGINE = MergeTree()
ORDER BY (window_end, product_id);

CREATE TABLE IF NOT EXISTS ${CLICKHOUSE_DB}.ctr_results_raw (
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

CREATE MATERIALIZED VIEW IF NOT EXISTS ${CLICKHOUSE_DB}.ctr_ml_view
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
FROM ${CLICKHOUSE_DB}.ctr_results_raw
GROUP BY product_id, window_minute;

CREATE MATERIALIZED VIEW IF NOT EXISTS ${CLICKHOUSE_DB}.ctr_latest_view
ENGINE = ReplacingMergeTree(window_end)
ORDER BY product_id AS
SELECT
    product_id,
    argMax(ctr, window_end) AS latest_ctr,
    argMax(impressions, window_end) AS latest_impressions,
    argMax(clicks, window_end) AS latest_clicks,
    max(window_end) AS window_end
FROM ${CLICKHOUSE_DB}.ctr_results_raw
GROUP BY product_id;
SQL

echo "ClickHouse schema initialized"
