# 실시간 CTR 데이터 파이프라인

이 프로젝트는 스트리밍 조회(impression) 및 클릭(click) 이벤트로부터 CTR(Click-Through-Rate)을 처리하는 파이프라인을 실습합니다.  이 파이프라인은 Kafka, Flink, Redis 를 사용하여 구축되었으며, 결과를 제공하기 위한 FastAPI 백엔드를 포함합니다.  
또한 분석용 싱크(Sink)로 **ClickHouse**와 **DuckDB**를 포함하며, 모니터링을 위한 **Prometheus/Grafana** 스택도 갖추고 있습니다. 현재 Flink는 1.18.x(LTS)를 기준으로 구성되어 있습니다.

## 🏛️ 아키텍처

데이터는 다음과 같이 시스템을 통해 흐릅니다:

```
                                      ┌──────────────────┐
                                      │   Serving API    │
                                      │    (FastAPI)     │
                                      └──────────────────┘
                                               ▲
                                               │ (GET /ctr/...)
                                               │
┌──────────┐   ┌───────┐   ┌───────────┐   ┌───────┐
│ Producers│──>│ Kafka │──>│ Flink App │──>│ Redis │
└──────────┘   └───────┘   └───────────┘   └───────┘
     (Python)   (Events)    (10s Window)   │ (Hashes)
                                           │
                                           ├──> ┌────────────┐
                                           │    │ ClickHouse │
                                           │    └────────────┘
                                           │
                                           └──> ┌────────────┐
                                                │   DuckDB   │
                                                └────────────┘
                                                      
                                             ┌──────────────┐
                                             │ RedisInsight │
                                             └──────────────┘
```

### 📐 Flink 애플리케이션 구조 (DDD)

Flink 애플리케이션은 **Domain-Driven Design (DDD)** 원칙을 따라 설계되었습니다:

```
flink-app/
├── domain/             # 순수 비즈니스 로직 (Flink 의존성 없음)
│   ├── model/          # Event, CTRResult 등 도메인 모델
│   └── service/        # EventCountAggregator, CTRResultWindowProcessFunction
├── application/        # 애플리케이션 서비스 (Job 구성)
│   └── CtrJobService   # Flink Job Topology 정의
├── infrastructure/     # 외부 시스템 연동
│   ├── flink/
│   │   ├── source/     # KafkaSourceAdapter
│   │   └── sink/       # RedisSinkAdapter, DuckDBSinkAdapter, ClickHouseSinkAdapter
│   └── config/         # Spring 설정
└── CtrApplication      # Spring Boot 진입점
```

**설계 특징:**
- **관심사 분리:** 도메인 로직과 인프라 코드 완전 분리
- **테스트 용이성:** 순수 Java 객체로 단위 테스트 작성 가능
- **유지보수성:** 인프라 변경 시 도메인 로직 영향 최소화
- **Spring Boot 통합:** 의존성 주입 및 설정 외부화

## ✨ 주요 기능

-   **실시간 처리:** **10초** 텀블링 윈도우(Tumbling Window)에 걸쳐 CTR을 계산합니다.
-   **상태 기반 분석:** 비교 분석을 위해 **최신** CTR 결과와 **직전** CTR 결과를 모두 유지합니다.
-   **멀티 싱크(Multi-Sink):**
    -   **Redis:** 저지연(Low-latency) 서빙용.
    -   **ClickHouse:** 고성능 OLAP 분석용 (실시간 대시보드).
    -   **DuckDB:** 임베디드/로컬 파일 기반 분석용 (개발/디버깅).
-   **관측 가능성(Observability):** **Prometheus**(메트릭 수집)와 **Grafana**(대시보드)를 통한 포괄적인 모니터링.
-   **RESTful API:** 계산된 CTR 데이터에 접근할 수 있는 엔드포인트를 제공하는 FastAPI 서버.
-   **컨테이너화:** 전체 환경은 Docker를 사용하여 컨테이너화되어 있으며, Docker Compose와 쉘 스크립트로 관리됩니다.
-   **Graceful Shutdown:** SIGTERM 시그널 처리로 안전한 종료 지원.
-   **구조화된 로깅:** Logback을 통한 파일 및 콘솔 로깅, 로그 로테이션 지원.

## 🧩 구성 요소

| 컴포넌트 | 디렉토리 | 설명 |
| --- | --- | --- |
| **Data Producers** | `producers/` | 사용자 노출 및 클릭을 시뮬레이션하여 Kafka 토픽으로 이벤트를 전송하는 Python 스크립트입니다. |
| **Event Stream** | `docker-compose` | 이벤트 스트림 수집을 위한 3개의 브로커로 구성된 Kafka 클러스터입니다. |
| **Stream Processor** | `flink-app/` | **Spring Boot + Gradle** 기반 Flink 애플리케이션으로 CTR을 계산하고 결과를 여러 싱크로 전송합니다. DDD 아키텍처 적용. |
| **Serving Layer** | `serving-api/` | Redis에서 CTR 데이터를 가져오는 FastAPI 서버입니다. |
| **Monitoring** | `prometheus.yml`, `grafana/` | Flink 메트릭 수집(Prometheus) 및 시각화(Grafana)를 위한 설정입니다. |
| **Monitoring UIs** | `docker-compose` | 시스템 컴포넌트를 관찰하고 관리하기 위한 Kafka UI, Flink Dashboard, RedisInsight, Grafana입니다. |
| **Performance Test**| `performance-test/`| API 부하 테스트 및 윈도우 로직 검증을 위한 Python 스크립트입니다. |

## 🛠️ 기술 스택

-   **Backend & Serving:** FastAPI, Uvicorn
-   **Databases:** Redis, ClickHouse, DuckDB
-   **Monitoring:** Prometheus, Grafana
-   **Languages:** Java 17, Python 3.8
-   **Containerization:** Docker, Docker Compose
-   **Build Tools:** Gradle 8.x, uv

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
| **Serving API** | `http://localhost:8000/docs` | 대화형 API 문서 (Swagger UI). |
| **Flink UI** | `http://localhost:8081` | Flink 작업 및 클러스터 상태 모니터링. |
| **Kafka UI** | `http://localhost:8080` | Kafka 토픽 및 메시지 탐색. |
| **RedisInsight** | `http://localhost:5540` | Redis에 저장된 데이터 검사를 위한 GUI. |
| **Grafana** | `http://localhost:3000` | Flink 메트릭 시각화 (User/Pass: admin/admin). |

### API 엔드포인트

Serving API는 다음과 같은 주요 엔드포인트를 제공합니다:

| 메서드 | 엔드포인트 | 설명 |
| --- | --- | --- |
| `GET` | `/ctr/latest` | 모든 상품에 대한 최신 CTR 데이터를 가져옵니다. |
| `GET` | `/ctr/previous` | 모든 상품에 대한 이전 CTR 데이터를 가져옵니다. |
| `GET` | `/ctr/{product_id}` | 특정 상품에 대한 최신 및 이전 CTR 통합 뷰를 가져옵니다. |

### 데이터 검증

**ClickHouse:**
```bash
docker compose exec -it clickhouse clickhouse-client --query "SELECT count() FROM ctr_results"
```

**DuckDB:**
```bash
# DuckDB가 설치가 되어있지 않다면 설치합니다.
brew install duckdb
```

```bash
# 로컬로 복사합니다.
docker cp flink-taskmanager:/tmp/ctr.duckdb ./ctr_check.duckdb
docker cp flink-taskmanager:/tmp/ctr.duckdb.wal ./ctr_check.duckdb.wal

# DuckDB CLI로 조회합니다. (DuckDB가 로컬에 설치되어 있는 경우 동작합니다.)
duckdb ctr_check.duckdb "SELECT * FROM ctr_results LIMIT 10"
```

**Redis:**
```bash
docker compose exec -it redis redis-cli HGETALL ctr:latest
```

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
- 싱크 견고성: Redis/DuckDB 싱크에 재시도·백오프 및 Flink 메트릭(success/failure 카운터) 추가.
- 관측성: Prometheus 리포터 활성화 시 위 메트릭을 노출해 모니터링 가능.

### Serving API 안정화
- Redis 연결을 애플리케이션 수명주기에서 초기화/정리하며, 연결/소켓 타임아웃을 설정.
- FastAPI 의존성 주입으로 요청 처리 시 Redis 가용성 확인, 에러 메시지를 구체화.

### Flink 처리 로직

-   **Window:** 10초 텀블링 윈도우 (Tumbling Window)
-   **Time Characteristic:** 이벤트 시간 (Event Time)
-   **Watermark:** 2초 (최대 2초 늦게 도착하는 이벤트 처리)
-   **Allowed Lateness:** 5초 (매우 늦은 이벤트로 윈도우 업데이트 허용)

### Redis 데이터 구조

-   **`ctr:latest`**: 각 상품의 가장 최근 CTR 결과를 저장하는 Redis Hash.
    -   `field`: `product_id`
    -   `value`: CTR 데이터가 포함된 JSON 문자열
-   **`ctr:previous`**: 최신 윈도우 바로 직전 윈도우의 결과를 저장하는 Redis Hash. 구조는 `ctr:latest`와 동일합니다.

---

*이 프로젝트는 AI 어시스턴트 **Codex**를 활용하여 반복적으로 개발하고 개선했습니다.*
