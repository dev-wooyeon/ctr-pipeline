# 실시간 CTR 데이터 파이프라인

이 프로젝트는 스트리밍 조회(impression) 및 클릭(click) 이벤트로부터 CTR(Click-Through-Rate)을 처리하는 파이프라인을 실습합니다. Kafka와 Flink를 중심으로 CTR 집계를 수행하고, ClickHouse materialized view를 싱글 데이터 저장소로 활용하여 Superset 대시보드에서 결과를 바로 확인할 수 있도록 구성되어 있습니다.
Redis/serving-api와 DuckDB 계층은 제거되어 ClickHouse 단일 싱크 기반의 일관된 데이터 플로우를 유지하며 운영 부담을 줄였습니다. 현재 Flink는 1.18.x(LTS)를 기준으로 구성되어 있습니다.

## 🏛️ 아키텍처

데이터는 다음과 같이 시스템을 통해 흐릅니다:

```
                                      ┌───────────────┐
                                      │   Superset    │
                                      │ (Dashboards)  │
                                      └───────────────┘
                                               ▲
                                               │ (SQL)
┌──────────┐   ┌───────┐   ┌───────────┐   ┌──────────────┐
│ Producers│──>│ Kafka │──>│ Flink App │──>│ ClickHouse   │
└──────────┘   └───────┘   └───────────┘   └──────────────┘
     (Python)   (Events)    (10s Window)        │
                                               ├── ctr_results_raw
                                               ├── ctr_ml_view
                                               └── ctr_latest_view
```

### 📐 Flink 애플리케이션 구조 (DDD)

Flink 애플리케이션은 **Domain-Driven Design (DDD)** 원칙을 따라 설계되었으며, **경량화**를 위해 Spring Boot 없이 순수 Java로 구현되었습니다:

```
flink-app/
├── domain/             # 순수 비즈니스 로직 (Flink 의존성 없음)
│   ├── model/          # Event, CTRResult 등 도메인 모델
│   └── service/        # EventCountAggregator, CTRResultWindowProcessFunction
├── application/        # 애플리케이션 서비스 (Job 구성)
│   └── CtrJobService   # Flink Job Topology 정의
├── infrastructure/     # 외부 시스템 연동
│   ├── flink/
│   │   ├── source/     # KafkaSourceFactory
│   │   └── sink/       # ClickHouseSink
│   └── config/         # 설정 클래스 (Jackson YAML 기반)
└── CtrApplication      # 진입점 (수동 DI)
```

**설계 특징:**
- **경량화:** Spring Boot 제거로 JAR 크기 최소화 및 시작 시간 단축
- **관심사 분리:** 도메인 로직과 인프라 코드 완전 분리
- **테스트 용이성:** 순수 Java 객체로 단위 테스트 작성 가능
- **유지보수성:** 인프라 변경 시 도메인 로직 영향 최소화
- **수동 DI:** 명시적 의존성 주입으로 투명한 객체 그래프
- **설정 외부화:** Jackson YAML을 통한 타입 안전 설정 로딩

## ✨ 주요 기능

-   **실시간 처리:** **10초** 텀블링 윈도우(Tumbling Window)에 걸쳐 CTR을 계산합니다.
-   **ClickHouse Materialized Views:** Flink가 `ctr_results_raw`에 결과를 기록하면 `ctr_ml_view`(1분 단위 집계)와 `ctr_latest_view`(제품별 최신 상태)가 자동으로 최신 데이터를 유지하여 Redis/serving-api 레이어 없이도 실시간 서빙이 가능합니다.
-   **Superset 대시보드:** ClickHouse materialized view를 직접 조회할 수 있도록 Superset을 구성하여 대시보드 및 SQL 탐색이 가능합니다.
-   **컨테이너화:** 전체 환경은 Docker를 사용하여 컨테이너화되어 있으며, Docker Compose와 쉘 스크립트로 관리됩니다.
-   **Graceful Shutdown:** SIGTERM 시그널 처리로 안전한 종료를 지원합니다.
-   **구조화된 로깅:** Logback을 통한 파일 및 콘솔 로깅, 로그 로테이션 지원을 제공합니다.
-   **Operator Chaining 최적화:** Flink 연산자 체이닝과 슬롯 공유 그룹으로 네트워크 오버헤드 30-50%를 줄이고 리소스 효율을 극대화합니다.

## 🧩 구성 요소

| 컴포넌트 | 디렉토리 | 설명 |
| --- | --- | --- |
| **Data Producers** | `producers/` | 사용자 노출 및 클릭을 시뮬레이션하여 Kafka 토픽으로 이벤트를 전송하는 Python 스크립트입니다. |
| **Event Stream** | `docker-compose` | 이벤트 스트림 수집을 위한 3개의 브로커로 구성된 Kafka 클러스터입니다. |
| **Stream Processor** | `flink-app/` | **Gradle + Kotlin** 기반 경량 Flink 애플리케이션으로 CTR을 계산하고 ClickHouse 싱크에 결과를 전송합니다. DDD 아키텍처 적용, Jackson YAML 기반 설정. |
| **Analytics Layer** | `superset` (Docker Compose 서비스) | Superset이 ClickHouse materialized view를 조회하여 대시보드 및 SQL 탐색을 제공합니다. |
| **Performance Test**| `performance-test/`| API 부하 테스트 및 윈도우 로직 검증을 위한 Python 스크립트입니다. |

## 🛠️ 기술 스택

-   **Stream Processing & Languages:** Java 17, Kotlin, Apache Flink 1.18
-   **Streaming Infrastructure:** Kafka, ClickHouse, Superset
-   **Data Producers:** Python 3.8
-   **Containerization:** Docker, Docker Compose
-   **Build Tools:** Gradle 8.x (Flink), uv (Python dependency management)

## 🚀 시작하기

로컬 머신에서 전체 파이프라인을 실행하려면 다음 단계를 따릅니다.

### 사전 요구 사항

-   Docker 및 Docker Compose
-   **Gradle 8.x** (Flink 앱 빌드용, Gradle 9.x는 호환성 문제로 지원하지 않음)
    ```bash
    # macOS (Homebrew)
    brew install gradle@8
    echo 'export PATH="/opt/homebrew/opt/gradle@8/bin:$PATH"' >> ~/.zshrc
    source ~/.zshrc
    ```
-   Docker 이미지를 가져오기 위한 인터넷 연결

### 실행 방법

하나의 스크립트로 인프라 시작, 데이터베이스 초기화, Flink 작업 배포, Kafka 토픽 생성, 데이터 생성기 시작까지 모든 과정을 수행합니다.

```shell
./scripts/setup.sh
```

스크립트가 정상적으로 완료되면 전체 파이프라인이 실행 중인 상태가 됩니다!

## 📊 확인 방법

시스템을 관찰하고 데이터에 접근하는 방법은 다음과 같습니다.

### 모니터링 대시보드

| 서비스 | URL | 설명 |
| --- | --- | --- |
| **Superset** | `http://localhost:8088` | ClickHouse materialized view를 탐색하는 대시보드 및 SQL 에디터. |
| **Flink UI** | `http://localhost:8081` | Flink 작업 및 클러스터 상태 모니터링. |
| **Kafka UI** | `http://localhost:8080` | Kafka 토픽 및 메시지 탐색. |
| **ClickHouse HTTP** | `http://localhost:8123` | ClickHouse HTTP 인터페이스를 통한 직접 SQL 실행. |

### ClickHouse materialized view

Flink는 `ctr_results_raw`에 10초 창 CTR 결과를 저장하고, ClickHouse의 materialized view (`ctr_ml_view`, `ctr_latest_view`)가 자동적으로 집계형 뷰를 유지합니다. Superset과 직접 ClickHouse HTTP 인터페이스는 이 뷰를 바로 조회하여 최신 CTR, 분당 평균 CTR, 클릭/노출 집계를 확인할 수 있습니다.

- **`ctr_results_raw`**: 모든 윈도우별 결과를 저장한 MergeTree 테이블입니다.
- **`ctr_ml_view`**: 1분 단위로 CTR 평균과 총 노출/클릭을 유지하는 AggregatingMergeTree 뷰입니다.
- **`ctr_latest_view`**: 각 상품의 가장 최신 윈도우 결과만 남기는 ReplacingMergeTree 뷰입니다.

### 데이터 검증

**ClickHouse 기본 테이블:**
```bash
docker compose exec clickhouse clickhouse-client --query "SELECT count() FROM default.ctr_results_raw"
```

**Latest view 확인 (최근 CTR):**
```bash
docker compose exec clickhouse clickhouse-client --query "SELECT * FROM default.ctr_latest_view ORDER BY window_end DESC LIMIT 10"
```

**ML view 확인 (1분 집계):**
```bash
docker compose exec clickhouse clickhouse-client --query "SELECT product_id, window_minute, avgMerge(avg_ctr) AS avg_ctr FROM default.ctr_ml_view WHERE window_minute >= now64(3) - INTERVAL 10 MINUTE GROUP BY product_id, window_minute ORDER BY product_id, window_minute LIMIT 20"
```

**Superset 대시보드 확인:** Superset을 통해 `ctr_latest_view`/`ctr_ml_view`를 SQL Lab 또는 Dashboards에서 직접 탐색하여 실시간 데이터를 검증합니다.

## 🛑 중지 방법

제공된 스크립트를 사용하여 애플리케이션의 각 부분을 중지할 수 있습니다.

```bash
# 모든 인프라 서비스 중지 및 제거
docker-compose down

# 데이터 생성기 중지
./scripts/stop-producers.sh

# Flink 작업 중지
./scripts/stop-flink-job.sh
```

## 📜 스크립트 개요

이 프로젝트는 관리를 단순화하기 위해 `/scripts` 디렉토리에 여러 헬퍼 스크립트를 포함하고 있습니다:

| 스크립트 | 설명 |
| --- | --- |
| `setup.sh` | 전체 파이프라인(인프라, DB 초기화, Flink Job, Producer)을 한 번에 설정하고 실행합니다. |
| `create-topics.sh` | Kafka에 필요한 `impressions` 및 `clicks` 토픽을 생성합니다. |
| `deploy-flink-job.sh` | Flink 애플리케이션을 클러스터에 배포합니다. |
| `stop-flink-job.sh` | 실행 중인 Flink 작업을 찾아 취소합니다. |
| `start-producers.sh` | Python 데이터 생성기를 백그라운드에서 시작합니다. |
| `stop-producers.sh` | 백그라운드 생성기 프로세스를 중지합니다. |

*참고: 스크립트를 실행 가능하게 만들어야 할 수도 있습니다: `chmod +x scripts/*.sh`*

## ⚙️ 구성 상세

### Flink 신뢰성 설정
- 체크포인트: `ctr.job.checkpoint-*`로 간격/타임아웃/동시성/스토리지/모드 설정 (`RETAIN_ON_CANCELLATION` 외부 체크포인트 정리 정책).
- 재시작 전략: `ctr.job.restart-attempts`, `ctr.job.restart-delay-ms`로 설정 (기본 3회, 10초 간격).
- 타임 핸들링: 이벤트 타임은 UTC 기준 millis로 변환해 워터마크 생성.
- 유효성 검증: 역직렬화 단계에서 스키마 검증(필수 필드/이벤트 타입) 후 잘못된 이벤트는 드롭 및 경고 로그.
- 싱크 견고성: ClickHouse sink에 재시도·백오프를 포함하여 JVM/네트워크 오류 대응과 배치 쓰기 효율을 높입니다.

### Flink 처리 로직

-   **Parallelism:** 2 (모든 연산자의 기본 병렬도)
-   **Window:** 10초 텀블링 윈도우 (Tumbling Window)
-   **Time Characteristic:** 이벤트 시간 (Event Time)
-   **Watermark:** 2초 (최대 2초 늦게 도착하는 이벤트 처리)
-   **Allowed Lateness:** 5초 (매우 늦은 이벤트로 윈도우 업데이트 허용)

### ClickHouse materialized view 설정

- `scripts/init-clickhouse.sh`은 `CLICKHOUSE_HOST`, `CLICKHOUSE_PORT`, `CLICKHOUSE_DB` 환경변수를 받아 ClickHouse에 접속하여 `ctr_results_raw`, `ctr_ml_view`, `ctr_latest_view`를 한 번에 생성합니다. `docker compose exec clickhouse clickhouse-client`로 실행하거나 로컬 `clickhouse-client`가 있을 경우 실행 가능합니다.
- `ctr_results_raw`는 MergeTree 엔진으로 `window_end`를 기준으로 파티셔닝하며 `(window_end, product_id)` 순서로 정렬하여 쓰기 성능과 범위 조회 효율을 확보합니다.
- `ctr_ml_view`는 AggregatingMergeTree로 1분 단위 평균/합계를, `ctr_latest_view`는 ReplacingMergeTree로 각 상품의 최신 윈도우 결과만 유지하여 Superset과 SQL Lab에서 빠르게 조회할 수 있도록 합니다.

---

*이 프로젝트는 AI 어시스턴트 **Codex**를 활용하여 반복적으로 개발하고 개선했습니다.*
