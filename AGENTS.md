# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Real-time CTR (Click-Through Rate) pipeline that processes streaming impression/click events using Kafka → Flink → Redis/ClickHouse/DuckDB, with a FastAPI serving layer.

## Build & Run Commands

### Full System Setup
```bash
./scripts/setup.sh  # Builds everything, starts Docker, deploys Flink job, starts producers
```

### Flink Application (Java 17, Gradle 8.x)
```bash
cd flink-app
gradle clean build                    # Build with tests
gradle clean shadowJar -x test        # Build fat JAR without tests
gradle test                           # Run tests only
```

### Producers (Python)
```bash
cd producers
uv sync                               # Install dependencies
python impression_producer.py &       # Start impression producer
python click_producer.py &            # Start click producer
```

### Serving API (Python/FastAPI)
```bash
cd serving-api
uv sync
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### Individual Operations
```bash
./scripts/deploy-flink-job.sh    # Deploy Flink job to cluster
./scripts/stop-flink-job.sh      # Cancel running Flink job
./scripts/start-producers.sh     # Start data generators
./scripts/stop-producers.sh      # Stop data generators
./scripts/create-topics.sh       # Create Kafka topics
docker-compose down              # Stop all infrastructure
```

## Architecture

```
Producers (Python) → Kafka (3 brokers) → Flink App → Redis (serving)
                                                   → ClickHouse (analytics)
                                                   → DuckDB (debug)
                                                           ↓
                                               FastAPI Serving API
```

### Flink Application Structure (DDD Pattern)
```
flink-app/src/main/java/com/example/ctr/
├── CtrApplication.java           # Entry point with manual DI
├── domain/
│   ├── model/                    # Event, EventCount, CTRResult (pure Java)
│   └── service/                  # EventCountAggregator, CTRResultWindowProcessFunction
├── application/
│   └── CtrJobService.java        # Job orchestration
└── infrastructure/
    ├── flink/
    │   ├── CtrJobPipelineBuilder.java   # Pipeline topology
    │   ├── FlinkEnvironmentFactory.java # Env setup, checkpointing
    │   ├── source/               # KafkaSourceFactory, deserializers
    │   └── sink/                 # RedisSink, DuckDBSink, ClickHouseSink
    └── config/                   # AppConfig, *Properties classes (Jackson YAML)
```

### Key Processing Parameters
- **Window**: 10-second tumbling event-time windows
- **Watermark**: 2-second out-of-order tolerance
- **Allowed Lateness**: 5 seconds
- **Parallelism**: 2 (default), 1 for DuckDB sink
- **Checkpointing**: 60s interval, EXACTLY_ONCE semantics

## Configuration

- `flink-app/src/main/resources/application.yml` - Docker deployment config
- `flink-app/src/main/resources/application-local.yml` - Local dev config (auto-selected if exists)
- Flink config properties: `ctr.job.*`, `kafka.*`, `redis.*`, `clickhouse.*`, `duckdb.*`

## Data Verification

```bash
# Redis
docker compose exec redis redis-cli HGETALL ctr:latest

# ClickHouse
docker compose exec clickhouse clickhouse-client --query "SELECT * FROM ctr_results LIMIT 10"

# DuckDB (copy from container first)
docker cp flink-taskmanager:/tmp/ctr.duckdb ./ctr_check.duckdb
duckdb ctr_check.duckdb "SELECT * FROM ctr_results LIMIT 10"
```

## Monitoring URLs

- Flink UI: http://localhost:8081
- Kafka UI: http://localhost:8080
- FastAPI Docs: http://localhost:8000/docs
- RedisInsight: http://localhost:5540

## Important Constraints

- **Gradle 8.x required** - Gradle 9.x has compatibility issues with Flink
- **Java 17 required** - Not Java 8 or 11
- No Spring Boot - lightweight manual DI for smaller JAR and faster startup
- Domain layer (`domain/`) has no Flink dependencies - pure Java for testability
