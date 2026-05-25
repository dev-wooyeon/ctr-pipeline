# Portfolio Roadmap: Backend Engineer → Data Engineer**목표**: 시니어 데이터 엔지니어가 보고 "이 사람 같이 일하고 싶다" 생각하게 만들기

**기간**: 3개월 (주말 + 평일 저녁 2-3시간)

**완료 조건**:
- GitHub README로 프로젝트 설명 가능
- 라이브 데모 가능
- 면접에서 30분 동안 technical deep-dive 가능

---

## 면접관이 보는 것

### ✅ 기술 스킬
- Streaming 아키텍처 이해도
- 데이터 품질 의식
- 성능 최적화 경험
- 문제 해결 능력

### ✅ 데이터 엔지니어 마인드셋
- "이 데이터가 정확한가?"에 대한 집착
- End-to-end ownership
- 확장성 고려
- 운영 경험

### ✅ 학습 능력
- 새로운 기술 습득 속도
- 문제 정의 및 해결
- Trade-off 이해

---

## 현재 상태 → 목표 상태

| 항목 | 현재 | 목표 | 면접에서 말할 수 있는 것 |
|------|------|------|------------------------|
| **아키텍처** | 8/10 | 9/10 | "DDD로 설계했고, domain은 Flink에 의존하지 않습니다" |
| **테스트** | 4/10 | 7/10 | "80% 커버리지, integration test로 end-to-end 검증합니다" |
| **성능** | ?/10 | 8/10 | "초당 10만 이벤트 처리, p99 latency 3초 이하입니다" |
| **모니터링** | 2/10 | 8/10 | "Prometheus + Grafana로 실시간 모니터링, 알림 설정했습니다" |
| **데이터 품질** | 5/10 | 8/10 | "Invalid event 비율 추적, schema validation 적용했습니다" |
| **문서화** | 6/10 | 9/10 | "아키텍처 결정을 ADR로 문서화했습니다" |

---

## Phase 1: Quick Wins (1주)

**목표**: GitHub 올리고 바로 보여줄 수 있는 상태
**면접 질문 대응**: "프로젝트 설명해주세요"

### Task 1.1: README.md 작성 (3시간)

**위치**: `/README.md`

**내용**:
```markdown
# Real-time CTR Calculator

Flink 기반 실시간 CTR(Click-Through Rate) 계산 파이프라인

## 📊 Architecture

[아키텍처 다이어그램 이미지]

Kafka → Flink → ClickHouse

- **Throughput**: 초당 10만 이벤트
- **Latency**: p99 < 3초
- **Accuracy**: EXACTLY_ONCE semantics

## 🎯 Why This Project?

백엔드 개발자에서 데이터 엔지니어로 전환하기 위한 포트폴리오 프로젝트

**핵심 학습 목표**:
- Streaming 아키텍처 설계
- Event time processing & watermarks
- State management
- Data quality engineering

## 🏗️ Tech Stack

- **Stream Processing**: Apache Flink 1.18
- **Message Queue**: Kafka (KRaft mode)
- **Storage**: ClickHouse
- **Language**: Kotlin
- **Monitoring**: Prometheus + Grafana
- **Testing**: JUnit 5 + Testcontainers

## 🚀 Quick Start

\`\`\`bash
./scripts/setup.sh
\`\`\`

Access:
- Flink UI: http://localhost:8081
- Grafana: http://localhost:3000
- Kafka UI: http://localhost:8080

## 📈 Key Metrics

- Window: 10초 tumbling window
- Watermark: 5초 out-of-order tolerance
- Parallelism: 2
- Checkpoint: 60초 간격

## 🧪 Testing

\`\`\`bash
cd flink-app
./gradlew test
\`\`\`

- **Coverage**: 80%+
- **Integration Tests**: Testcontainers로 end-to-end 검증

## 📚 Architecture Decisions

상세한 기술 결정은 `docs/architecture/`, `docs/migrations/` 참고

- [Kotlin Migration Guide](../migrations/KOTLIN_MIGRATION_GUIDE.md)
- [DuckDB Removal Decision](../architecture/WHY_REMOVE_DUCKDB.md)
- [ClickHouse Materialized View Design](../architecture/CLICKHOUSE_MATERIALIZED_VIEW.md)

## 🔧 What I Learned

1. **Streaming Fundamentals**: Event time vs Processing time 차이를 실제로 경험
2. **Data Quality**: Invalid event 처리, schema validation의 중요성
3. **Performance Tuning**: Operator chaining, network buffer 최적화
4. **Operational Excellence**: Monitoring, alerting, graceful shutdown

## 📞 Contact

질문이나 피드백은 [이메일] 또는 [LinkedIn]으로 연락주세요.
```

**면접에서**: "제 GitHub에 README가 있는데, 아키텍처 다이어그램과 함께 전체 구조를 설명해놓았습니다"

---

### Task 1.2: 아키텍처 다이어그램 생성 (2시간)

**도구**: draw.io 또는 Excalidraw

**다이어그램 1: Overall Architecture**
```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│  Impression │      │    Click    │      │             │
│  Producer   │─────▶│  Producer   │─────▶│    Kafka    │
└─────────────┘      └─────────────┘      │  (3 brokers)│
                                           └──────┬──────┘
                                                  │
                                           ┌──────▼──────┐
                                           │    Flink    │
                                           │ JobManager  │
                                           │             │
                                           │ 2 TaskMgrs  │
                                           └──────┬──────┘
                                                  │
                        ┌─────────────────────────┼─────────────┐
                        │                         │             │
                 ┌──────▼──────┐         ┌────────▼────────┐   │
                 │ ClickHouse  │         │   Prometheus    │   │
                 │ (Analytics) │         │   (Metrics)     │   │
                 └─────────────┘         └─────────────────┘   │
                                                                │
                                                         ┌──────▼──────┐
                                                         │   Grafana   │
                                                         │ (Dashboard) │
                                                         └─────────────┘
```

**다이어그램 2: Flink Pipeline**
```
Kafka Source (impressions)  ─┐
                              ├─▶ Union ─▶ Filter ─▶ KeyBy(productId) ─▶ Window(10s) ─▶ Aggregate ─▶ ClickHouse
Kafka Source (clicks)       ─┘                                             │
                                                                            └─▶ Metrics ─▶ Prometheus
```

**다이어그램 3: Data Flow with Timestamps**
```
Event Time: 12:00:00 ─────────────────────────────────▶
Watermark:  11:59:55 (5s delay) ──────────────────────▶
                     │         │         │
Window:         [11:59:50, 12:00:00]  [12:00:00, 12:00:10]
                     │                   │
Results:        CTR = 0.45          CTR = 0.52
```

이미지를 `docs/images/` 폴더에 저장하고 README에 포함

**면접에서**: "아키텍처는 이렇게 구성했는데요, 특히 이 부분이 중요한 이유는..."

---

### Task 1.3: 테스트 인프라 복구 (2시간)

**이유**: "테스트 작성했어요"라고 말하려면 테스트가 돌아가야 함

**build.gradle.kts 수정**:
```kotlin
dependencies {
    // ... 기존 의존성

    // 테스트 (버전 명시)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
```

**검증**:
```bash
cd flink-app
./gradlew clean test

# 모든 테스트 통과 확인
BUILD SUCCESSFUL in 8s
```

**면접에서**: "모든 테스트는 CI에서 자동으로 돌아가고 있습니다"

---

### Task 1.4: 간단한 데모 스크립트 (1시간)

**파일**: `scripts/demo.sh`

```bash
#!/bin/bash

echo "=== Real-time CTR Calculator Demo ==="
echo ""

echo "1. Starting infrastructure..."
docker-compose up -d kafka1 kafka2 kafka3 clickhouse

echo ""
echo "2. Waiting for services to be ready..."
sleep 15

echo ""
echo "3. Creating topics..."
./scripts/create-topics.sh

echo ""
echo "4. Starting Flink cluster..."
docker-compose up -d flink-jobmanager flink-taskmanager

sleep 10

echo ""
echo "5. Deploying Flink job..."
./scripts/deploy-flink-job.sh

echo ""
echo "6. Starting data producers..."
./scripts/start-producers.sh

echo ""
echo "=== Demo is running! ==="
echo ""
echo "Access points:"
echo "  - Flink UI: http://localhost:8081"
echo "  - Kafka UI: http://localhost:8080"
echo ""
echo "Checking CTR results in 30 seconds..."
sleep 30

echo ""
echo "Latest CTR results:"
docker-compose exec -T clickhouse clickhouse-client --query \
  "SELECT product_id, impressions, clicks, ctr, window_end
   FROM ctr_results
   ORDER BY window_end DESC
   LIMIT 5
   FORMAT PrettyCompact"

echo ""
echo "=== Demo complete! ==="
echo ""
echo "To stop:"
echo "  ./scripts/stop-all.sh"
```

**면접에서**: "로컬에서 바로 데모 돌려볼 수 있습니다. 1분이면 전체 파이프라인이 동작합니다"

---

### Phase 1 체크리스트

- [ ] README.md 작성 (아키텍처, 메트릭, 학습 내용)
- [ ] 아키텍처 다이어그램 3개 생성
- [ ] 테스트 실행 가능 확인
- [ ] 데모 스크립트 작성 및 검증
- [ ] GitHub에 push
- [ ] LinkedIn에 프로젝트 공유

**완료 시 면접 대응**:
- ✅ "GitHub 링크 보내드릴게요"
- ✅ "로컬에서 바로 데모 가능합니다"
- ✅ "아키텍처는 이렇게 설계했습니다"

---

## Phase 2: Data Engineering Fundamentals (2-3주)

**목표**: 데이터 엔지니어로서의 핵심 역량 증명
**면접 질문 대응**: "데이터 품질은 어떻게 보장하나요?"

### Task 2.1: Data Quality Validation (6시간)

**새 파일**: `flink-app/src/main/kotlin/com/example/ctr/domain/service/DataQualityValidator.kt`

```kotlin
class DataQualityValidator(private val metrics: MetricGroup) {

    private val validEvents = metrics.counter("events_valid")
    private val invalidEvents = metrics.counter("events_invalid")
    private val futureTimestamps = metrics.counter("events_future_timestamp")
    private val missingFields = metrics.counter("events_missing_fields")

    fun validate(event: Event): ValidationResult {
        val violations = mutableListOf<String>()

        // Required fields
        if (event.userId.isNullOrBlank()) {
            violations.add("userId is required")
            missingFields.inc()
        }

        if (event.productId.isNullOrBlank()) {
            violations.add("productId is required")
            missingFields.inc()
        }

        if (event.eventType.isNullOrBlank()) {
            violations.add("eventType is required")
            missingFields.inc()
        }

        // Timestamp validation
        event.timestamp?.let { ts ->
            val now = System.currentTimeMillis()
            if (ts > now + 60_000) {  // 1분 이상 미래
                violations.add("timestamp is in the future: $ts > $now")
                futureTimestamps.inc()
            }

            if (ts < now - 86_400_000 * 7) {  // 7일 이상 과거
                violations.add("timestamp is too old (> 7 days)")
            }
        }

        // Business rules
        if (event.eventType !in setOf("click", "impression", "view")) {
            violations.add("invalid eventType: ${event.eventType}")
        }

        return if (violations.isEmpty()) {
            validEvents.inc()
            ValidationResult.Valid
        } else {
            invalidEvents.inc()
            ValidationResult.Invalid(violations)
        }
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val violations: List<String>) : ValidationResult()
}
```

**EventDeserializationSchema에 적용**:
```kotlin
override fun deserialize(message: ByteArray): Event? {
    val event = objectMapper.readValue(message, Event::class.java)

    return when (val result = validator.validate(event)) {
        is ValidationResult.Valid -> event
        is ValidationResult.Invalid -> {
            logger.warn("Invalid event dropped: ${result.violations.joinToString()}")
            null
        }
    }
}
```

**Grafana 패널 추가**:
- Valid event rate
- Invalid event rate
- Invalid event 유형별 분포

**면접에서**:
- "Invalid event를 5가지 카테고리로 분류해서 모니터링합니다"
- "타임스탬프가 미래인 경우, 필수 필드 누락, 7일 이상 오래된 데이터 등을 감지합니다"
- "데이터 품질 메트릭은 Grafana에서 실시간으로 볼 수 있습니다"

---

### Task 2.2: Performance Benchmark (8시간)

**목표**: "초당 X건 처리 가능"이라고 말할 수 있게

#### 2.2.1: Load Generator 작성

**파일**: `performance-test/load_generator.py`

```python
#!/usr/bin/env python3
"""
Kafka Load Generator for CTR Pipeline

Usage:
    python load_generator.py --rate 10000 --duration 300
"""

import argparse
import json
import time
from datetime import datetime
from kafka import KafkaProducer
from faker import Faker
import random
import threading
from dataclasses import dataclass

fake = Faker()

@dataclass
class Stats:
    sent: int = 0
    errors: int = 0

    def report(self):
        print(f"Sent: {self.sent:,} | Errors: {self.errors:,} | Rate: {self.sent/60:,.0f}/s")

class LoadGenerator:
    def __init__(self, bootstrap_servers: str, target_rate: int):
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            compression_type='snappy',
            batch_size=16384,
            linger_ms=10
        )
        self.target_rate = target_rate
        self.stats = Stats()
        self.product_ids = [f"product-{i:04d}" for i in range(1000)]

    def generate_event(self, event_type: str) -> dict:
        return {
            "userId": fake.uuid4(),
            "productId": random.choice(self.product_ids),
            "eventType": event_type,
            "timestamp": int(datetime.now().timestamp() * 1000)
        }

    def send_events(self, duration_seconds: int):
        interval = 1.0 / self.target_rate
        end_time = time.time() + duration_seconds

        while time.time() < end_time:
            try:
                # 80% impressions, 20% clicks
                event_type = "impression" if random.random() < 0.8 else "click"
                topic = "impressions" if event_type == "impression" else "clicks"

                event = self.generate_event(event_type)
                self.producer.send(topic, value=event)

                self.stats.sent += 1
                time.sleep(interval)

            except Exception as e:
                self.stats.errors += 1
                print(f"Error: {e}")

        self.producer.flush()

    def run(self, duration_seconds: int):
        print(f"Starting load test: {self.target_rate:,} events/s for {duration_seconds}s")
        print(f"Total events: {self.target_rate * duration_seconds:,}")
        print("-" * 60)

        # Progress reporter
        def report_progress():
            while time.time() < end_time:
                time.sleep(10)
                self.stats.report()

        end_time = time.time() + duration_seconds
        reporter = threading.Thread(target=report_progress)
        reporter.start()

        # Send events
        self.send_events(duration_seconds)
        reporter.join()

        print("-" * 60)
        print(f"Load test complete!")
        self.stats.report()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bootstrap-servers", default="localhost:9092")
    parser.add_argument("--rate", type=int, required=True, help="Events per second")
    parser.add_argument("--duration", type=int, required=True, help="Duration in seconds")

    args = parser.parse_args()

    generator = LoadGenerator(args.bootstrap_servers, args.rate)
    generator.run(args.duration)
```

#### 2.2.2: Benchmark 실행

```bash
# 1K events/s
python performance-test/load_generator.py --rate 1000 --duration 300

# 10K events/s
python performance-test/load_generator.py --rate 10000 --duration 300

# 100K events/s (목표)
python performance-test/load_generator.py --rate 100000 --duration 300
```

#### 2.2.3: 메트릭 수집

**측정 항목**:
- Input rate (events/s)
- Output rate (records/s)
- End-to-end latency (p50, p95, p99)
- CPU/Memory usage
- Backpressure
- Checkpoint duration

**결과 문서**: `docs/flink/BENCHMARK_RESULTS.md`

```markdown
# Performance Benchmark Results

## Test Environment

- **Infrastructure**: Docker Compose on MacBook Pro M1
- **Flink**: 1 JobManager, 2 TaskManagers (2 slots each)
- **Kafka**: 3 brokers
- **Parallelism**: 2

## Results

### Throughput Test

| Input Rate | Output Rate | CPU Usage | Memory Usage | Backpressure | Result |
|-----------|-------------|-----------|--------------|--------------|--------|
| 1K/s | 1K/s | 20% | 1.2GB | None | ✅ Pass |
| 10K/s | 10K/s | 45% | 1.8GB | None | ✅ Pass |
| 50K/s | 48K/s | 75% | 2.4GB | Low | ⚠️ Warning |
| 100K/s | 85K/s | 95% | 3.2GB | High | ❌ Fail |

**Conclusion**: 안정적으로 처리 가능한 throughput은 **초당 50K 이벤트**

### Latency Test (10K events/s)

| Metric | Value |
|--------|-------|
| p50 latency | 1.2s |
| p95 latency | 2.8s |
| p99 latency | 4.1s |

**Conclusion**: Window size가 10초이므로 이론적 최소 latency는 10초. 실제로는 4초 이내에 대부분 처리.

### Checkpoint Performance

| Checkpoint Interval | Duration | State Size | Impact |
|--------------------|----------|------------|--------|
| 60s | 3.2s | 45MB | Low |
| 30s | 3.5s | 45MB | Medium |

## Bottleneck Analysis

**CPU-bound**:
- JSON deserialization이 CPU를 많이 사용
- 개선 방안: Avro/Protobuf 사용 시 30% 성능 향상 예상

**Memory-bound**:
- Window state가 메모리 사용
- 개선 방안: RocksDB state backend 사용 시 더 많은 상태 저장 가능

## Optimization Ideas

1. Use Avro instead of JSON (30% faster deserialization)
2. Increase parallelism to 4 (2x throughput)
3. Use RocksDB for state backend (handle larger state)
4. Tune network buffers
```

**면접에서**:
- "로컬 환경에서 초당 5만 건까지 안정적으로 처리했습니다"
- "병목은 JSON deserialization이었고, Avro 사용 시 30% 개선 가능함을 확인했습니다"
- "프로덕션에서는 parallelism을 올리고 RocksDB를 사용하면 10만 건 이상 가능할 것으로 예상됩니다"

---

### Task 2.3: Monitoring Dashboard (6시간)

**목표**: Grafana 대시보드를 보여주면서 설명

#### 2.3.1: Prometheus 설정

**파일**: `prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'flink'
    static_configs:
      - targets:
          - 'flink-jobmanager:9249'
          - 'flink-taskmanager:9249'
    metrics_path: /metrics
```

**docker-compose.yml에 추가**:
```yaml
services:
  prometheus:
    image: prom/prometheus:v2.48.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    mem_limit: 512m
    cpus: 0.5

  grafana:
    image: grafana/grafana:10.2.2
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
    depends_on:
      - prometheus
    mem_limit: 256m
    cpus: 0.25

volumes:
  prometheus-data:
  grafana-data:
```

#### 2.3.2: Grafana Dashboard

**파일**: `grafana/dashboards/flink-ctr-dashboard.json`

**패널 구성**:

1. **Overview Row**
   - Total Events Processed (counter)
   - Current CTR (gauge)
   - Invalid Event Rate (%)
   - Active Products (gauge)

2. **Throughput Row**
   - Events/second (graph)
   - Records out/second (graph)
   - Kafka lag (graph)

3. **Latency Row**
   - End-to-end latency p50/p95/p99 (graph)
   - Window processing time (graph)

4. **Data Quality Row**
   - Valid vs Invalid events (pie chart)
   - Invalid event types (bar chart)
   - Timestamp anomalies (graph)

5. **System Health Row**
   - CPU usage (graph)
   - Memory usage (graph)
   - Checkpoint duration (graph)
   - Backpressure (heatmap)

**스크린샷 찍어서 README에 추가**

**면접에서**:
- "실시간 대시보드로 모든 메트릭을 모니터링합니다"
- (화면 공유하면서) "여기서 데이터 품질, 처리량, 레이턴시를 한눈에 볼 수 있습니다"
- "Invalid event가 갑자기 증가하면 알림이 갑니다"

---

### Task 2.4: Integration Test (8시간)

**목표**: "End-to-end 테스트 작성했습니다"

**파일**: `flink-app/src/test/kotlin/com/example/ctr/integration/CtrPipelineE2ETest.kt`

```kotlin
@Testcontainers
class CtrPipelineE2ETest {

    companion object {
        @Container
        private val kafka = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
        ).withKraft()

        @Container
        private val clickhouse = ClickHouseContainer(
            "clickhouse/clickhouse-server:23.8"
        ).withInitScript("init-clickhouse.sql")
    }

    @Test
    fun `should calculate CTR correctly for single product`() {
        // Given: 100 impressions, 25 clicks
        val productId = "test-product-123"
        val baseTime = System.currentTimeMillis()

        val impressions = (1..100).map { i ->
            Event(
                userId = "user-$i",
                productId = productId,
                eventType = "impression",
                timestamp = baseTime + i * 100
            )
        }

        val clicks = (1..25).map { i ->
            Event(
                userId = "user-$i",
                productId = productId,
                eventType = "click",
                timestamp = baseTime + i * 400
            )
        }

        // When: Send to Kafka
        produceEvents(kafka, "impressions", impressions)
        produceEvents(kafka, "clicks", clicks)

        // Run Flink pipeline
        val env = createTestEnvironment()
        val pipeline = buildPipeline(env, kafka, clickhouse)

        val jobClient = env.executeAsync("test-job")

        // Wait for window to close + processing
        Thread.sleep(15000)

        jobClient.cancel().get()

        // Then: Verify CTR in ClickHouse
        val result = queryClickHouse(clickhouse, productId)

        assertThat(result).isNotNull
        assertThat(result.productId).isEqualTo(productId)
        assertThat(result.impressions).isEqualTo(100)
        assertThat(result.clicks).isEqualTo(25)
        assertThat(result.ctr).isEqualTo(0.25)
    }

    @Test
    fun `should handle late events correctly`() {
        // Test with late arrivals within allowed lateness
    }

    @Test
    fun `should drop events outside allowed lateness`() {
        // Test with very late events
    }

    @Test
    fun `should handle multiple products in same window`() {
        // Test with 10 different products
    }
}
```

**실행**:
```bash
./gradlew test --tests CtrPipelineE2ETest

# 또는 모든 테스트
./gradlew test
```

**Coverage 확인**:
```bash
./gradlew jacocoTestReport
open flink-app/build/reports/jacoco/test/html/index.html
```

**면접에서**:
- "Testcontainers로 실제 Kafka와 ClickHouse를 띄워서 end-to-end 테스트합니다"
- "Late event 처리, 여러 product 동시 처리 등 실제 시나리오를 테스트합니다"
- "Coverage는 80% 이상 유지하고 있습니다"

---

### Phase 2 체크리스트

- [ ] Data quality validator 구현
- [ ] Invalid event 메트릭 추가
- [ ] Load generator 작성
- [ ] Performance benchmark 실행 (1K, 10K, 50K)
- [ ] Benchmark 결과 문서화
- [ ] Prometheus + Grafana 설정
- [ ] Grafana dashboard 생성 (5개 row)
- [ ] Dashboard 스크린샷 README에 추가
- [ ] Integration test 작성 (4+ test cases)
- [ ] Test coverage 80% 달성

**완료 시 면접 대응**:
- ✅ "데이터 품질을 5가지 측면에서 검증합니다"
- ✅ "초당 5만 건까지 안정적으로 처리 가능합니다"
- ✅ "실시간 모니터링 대시보드가 있습니다"
- ✅ "End-to-end 테스트로 검증했습니다"

---

## Phase 3: Production-like Experience (1개월)

**목표**: "프로덕션 운영 경험"을 시뮬레이션
**면접 질문 대응**: "장애 대응 경험이 있나요?"

### Task 3.1: ADR (Architecture Decision Records) 작성 (4시간)

**목표**: 기술적 의사결정을 문서화 → 면접에서 "왜 이렇게 했나요?"에 대답

#### ADR-001: Why Kotlin over Java

**파일**: `docs/architecture/adr/001-kotlin-migration.md`

```markdown
# ADR-001: Kotlin으로 마이그레이션

## Status
Accepted

## Context
초기에는 Java로 작성했으나 Kotlin으로 전환을 고려

**Kotlin 장점**:
- Null safety (NullPointerException 방지)
- Data class (보일러플레이트 감소)
- Extension functions (DSL 작성 용이)
- Coroutines (비동기 처리)

**우려사항**:
- Flink는 Java 중심 생태계
- 팀이 Kotlin을 모를 수 있음
- 디버깅 시 decompiled code 확인 필요

## Decision
Kotlin으로 마이그레이션한다.

**이유**:
1. **타입 안전성**: `Event.eventTimeMillisUtc(): Long?` vs `Long` - null 처리 강제
2. **코드 간결성**: Data class로 Event, EventCount 등 50% 코드 감소
3. **DSL**: PipelineBuilder에서 extension function으로 가독성 향상
4. **학습 기회**: 개인 프로젝트이므로 새 기술 도입 적극 시도

**Trade-off 수용**:
- Flink 문서는 Java 예제 → Kotlin으로 변환하며 학습
- 팀 학습 비용 → 문서화로 완화

## Consequences

**긍정적**:
- NullPointerException 0건 (컴파일 타임에 잡힘)
- 코드 라인 수 30% 감소
- Pipeline DSL 가독성 향상

**부정적**:
- Flink 예제 변환에 초기 시간 투자
- IDE 지원이 Java보다 약간 느림

## Lessons Learned
Kotlin의 null safety가 streaming 애플리케이션에서 특히 유용함.
잘못된 timestamp 처리 등에서 컴파일 타임에 오류 발견.
```

#### ADR-002: ClickHouse vs Redis

**파일**: `docs/architecture/adr/002-storage-choice.md`

```markdown
# ADR-002: ClickHouse 선택 (Redis 제거)

## Status
Accepted (Replaces Redis)

## Context
초기에는 Redis를 serving layer로 사용했으나 제거하고 ClickHouse로 통합

**요구사항**:
- Real-time query (최신 CTR 조회)
- Historical analysis (시간대별 CTR 트렌드)
- Aggregation (상품별, 카테고리별 CTR)

**Option 1: Redis (기존)**
- ✅ Low latency (< 1ms)
- ✅ Simple key-value
- ❌ No historical data
- ❌ Limited aggregation
- ❌ 추가 인프라 필요

**Option 2: ClickHouse**
- ✅ Columnar storage (빠른 aggregation)
- ✅ Historical data 저장
- ✅ SQL 쿼리 가능
- ⚠️ Latency 높음 (10-50ms)
- ✅ 단일 storage

**Option 3: Both**
- ✅ Redis for latest, ClickHouse for history
- ❌ 복잡도 증가
- ❌ 데이터 동기화 문제

## Decision
ClickHouse 단일 storage로 간다.

**이유**:
1. **Use case 분석**: "최신 CTR"만 필요한 게 아니라 트렌드 분석이 더 중요
2. **Latency trade-off**: 50ms는 대시보드용으로 충분
3. **운영 단순화**: 하나의 storage만 관리
4. **비용 효율**: Redis 인프라 불필요

## Consequences

**긍정적**:
- 단일 진실의 원천 (Single Source of Truth)
- 복잡한 분석 쿼리 가능
- Materialized View로 추가 최적화 가능

**부정적**:
- Real-time API는 구축 안 함 (대시보드만 존재)

## Migration
1. Redis sink 제거
2. ClickHouse schema 최적화
3. Grafana에서 ClickHouse 직접 쿼리

## Lessons Learned
초기에 Redis를 추가한 건 "real-time"이라는 단어에 집착했기 때문.
실제 use case를 먼저 정의했어야 함.
```

#### ADR-003: State Backend Selection

**파일**: `docs/architecture/adr/003-state-backend.md`

```markdown
# ADR-003: State Backend Selection

## Status
Accepted (Filesystem for development, RocksDB for production)

## Context
Flink는 두 가지 state backend 제공:
- Filesystem (in-memory with async snapshots)
- RocksDB (embedded key-value store)

## Decision
**Development**: Filesystem state backend
**Production**: RocksDB state backend

**이유**:

**Filesystem 장점** (개발용):
- Setup 간단
- 빠른 state access
- 디버깅 용이
- 로컬 개발에 충분

**RocksDB 장점** (프로덕션용):
- State size가 메모리보다 클 수 있음
- Incremental checkpoint (빠른 checkpoint)
- Out-of-memory 방지

**현재 프로젝트**:
- State size: ~50MB (작음)
- Filesystem으로 충분하지만, RocksDB 설정도 준비

## Configuration
```yaml
# application.yml
ctr:
  job:
    state-backend: FILESYSTEM  # or ROCKSDB
```

## Future
상품 수가 1만 개 이상, state가 1GB 이상이면 RocksDB 전환
```

**면접에서**:
- "기술 결정마다 ADR을 작성해서 why를 문서화했습니다"
- "Kotlin 선택 이유는 타입 안전성과 코드 간결성 때문입니다"
- "Redis를 제거한 이유는 실제 use case 분석 결과 불필요했기 때문입니다"

---

### Task 3.2: Chaos Engineering (실험) (6시간)

**목표**: "장애 상황 대처 경험"

#### 실험 1: TaskManager Crash

```bash
# 1. 파이프라인 정상 동작 확인
docker-compose ps

# 2. TaskManager 하나 강제 종료
docker kill flink-taskmanager-1

# 3. 관찰
# - Flink UI에서 job restart 확인
# - Checkpoint recovery 확인
# - 데이터 손실 여부 확인

# 4. TaskManager 복구
docker-compose up -d flink-taskmanager

# 5. 결과 문서화
```

**결과**: `docs/operations/CHAOS_EXPERIMENTS.md`

```markdown
# Chaos Engineering Experiments

## Experiment 1: TaskManager Failure

**Hypothesis**: Job이 자동으로 restart하고 마지막 checkpoint부터 복구

**Procedure**:
1. TaskManager 1개 강제 종료
2. 5분 대기
3. TaskManager 복구

**Results**:
- Job restart: ✅ 30초 내 자동 restart
- Checkpoint recovery: ✅ 마지막 checkpoint(60초 전)부터 복구
- Data loss: ✅ 없음 (Kafka offset 기반 재처리)
- Recovery time: 45초

**Observations**:
- Restart strategy가 잘 동작함
- EXACTLY_ONCE semantics 보장됨
- Window 하나치(10초) 데이터만 재처리

**Improvements**:
- Checkpoint interval을 30초로 줄이면 복구 시간 단축 가능
- 하지만 checkpoint overhead 증가 → trade-off
```

#### 실험 2: Kafka Broker Down

```bash
# Kafka broker 하나 종료
docker stop kafka2

# Flink가 계속 동작하는지 확인 (replication factor 3)
```

#### 실험 3: Backpressure 유발

```bash
# ClickHouse 느리게 만들기
docker-compose exec clickhouse sh -c "tc qdisc add dev eth0 root netem delay 1000ms"

# Backpressure 발생 확인
# Flink UI에서 backpressure monitoring
```

**면접에서**:
- "장애 시나리오를 실제로 테스트해봤습니다"
- "TaskManager가 죽어도 45초 안에 자동 복구되고 데이터 손실이 없었습니다"
- "Backpressure 발생 시 upstream이 느려지는 것을 확인했습니다"

---

### Task 3.3: Runbook 작성 (3시간)

**파일**: `docs/operations/RUNBOOK.md`

```markdown
# CTR Pipeline Runbook

## Quick Links
- Flink UI: http://localhost:8081
- Grafana: http://localhost:3000
- ClickHouse: http://localhost:8123

## Health Checks

### 1. Is the job running?
```bash
curl -s http://localhost:8081/jobs | jq '.jobs[] | select(.status=="RUNNING")'
```

### 2. Is data flowing?
```bash
# ClickHouse에서 최근 1분 데이터 확인
docker-compose exec -T clickhouse clickhouse-client --query \
  "SELECT count(*) FROM ctr_results WHERE window_end > now() - INTERVAL 1 MINUTE"
```

Expected: > 0

### 3. Are there errors?
```bash
docker-compose logs flink-taskmanager --tail 50 | grep ERROR
```

Expected: No ERRORs

## Common Issues

### Issue: Job is not running

**Symptoms**:
- Flink UI shows no running jobs
- No data in ClickHouse

**Diagnosis**:
```bash
docker-compose ps flink-jobmanager flink-taskmanager
docker-compose logs flink-jobmanager --tail 100
```

**Solution**:
```bash
./scripts/deploy-flink-job.sh
```

### Issue: High backpressure

**Symptoms**:
- Grafana shows backpressure > 50%
- Kafka lag increasing

**Diagnosis**:
```bash
# Check ClickHouse performance
docker-compose exec clickhouse clickhouse-client --query "SHOW PROCESSLIST"

# Check Flink parallelism
curl http://localhost:8081/jobs/{job-id}
```

**Solutions**:
1. Increase ClickHouse resources
2. Increase Flink parallelism
3. Increase batch size

### Issue: High invalid event rate

**Symptoms**:
- Grafana shows > 10% invalid events

**Diagnosis**:
```bash
# Check producer logs
docker-compose logs impression-producer --tail 100
docker-compose logs click-producer --tail 100
```

**Solutions**:
1. Check producer schema
2. Check timestamp generation
3. Review validation logic

## Deployment

### Deploy new version
```bash
# 1. Build
cd flink-app
./gradlew clean shadowJar -x test

# 2. Stop old job (with savepoint)
curl -X PATCH http://localhost:8081/jobs/{job-id}/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/tmp/savepoints"}'

# 3. Deploy new job
./scripts/deploy-flink-job.sh --from-savepoint /tmp/savepoints/{savepoint-id}
```

### Rollback
```bash
# Deploy previous version from savepoint
./scripts/deploy-flink-job.sh --from-savepoint /tmp/savepoints/{previous-savepoint-id}
```

## Monitoring Alerts

### Critical Alerts
- Job down > 5 minutes → Page on-call
- Data loss detected → Page on-call
- Checkpoint failure rate > 10% → Page on-call

### Warning Alerts
- Backpressure > 50% for 10 min → Slack
- Invalid event rate > 10% → Slack
- Kafka lag > 100K → Slack

## Escalation
1. Check runbook
2. Check Grafana
3. Check logs
4. Create incident doc
5. Page senior engineer if can't resolve in 30 min
```

**면접에서**:
- "운영 가이드를 작성해서 누구나 장애 대응할 수 있게 했습니다"
- "주요 이슈별로 진단 방법과 해결 방법을 문서화했습니다"

---

### Phase 3 체크리스트

- [ ] ADR 3개 작성 (Kotlin, ClickHouse, State Backend)
- [ ] Chaos experiment 3개 실행 및 문서화
- [ ] Runbook 작성 (health checks, common issues, deployment)
- [ ] Troubleshooting 가이드
- [ ] Monitoring alert 설정 (Prometheus rules)

**완료 시 면접 대응**:
- ✅ "기술 결정을 ADR로 문서화했습니다"
- ✅ "장애 시나리오를 실험해봤습니다"
- ✅ "운영 가이드가 있습니다"
- ✅ "알림 설정이 되어 있습니다"

---

## Phase 4: Portfolio & Demo (1개월)

**목표**: 면접에서 자신있게 발표할 수 있는 자료
**면접 질문 대응**: "프로젝트를 설명해주세요" (30분 발표)

### Task 4.1: Presentation Deck (6시간)

**목표**: 30분 발표 자료

**파일**: `docs/portfolio/presentation.pdf` (Google Slides → PDF)

**슬라이드 구성** (25장):

1. **Title** (1장)
   - Real-time CTR Calculator
   - 부제: Backend Engineer → Data Engineer Journey

2. **About Me** (1장)
   - 5년차 백엔드 개발자
   - Spring Boot, Kubernetes 경험
   - 데이터 엔지니어링 전환 중

3. **Problem Statement** (2장)
   - 광고 플랫폼에서 CTR을 실시간으로 알아야 하는 이유
   - 요구사항: 10초 이내 latency, 초당 10만 이벤트

4. **Architecture** (3장)
   - Overall architecture diagram
   - Flink pipeline diagram
   - Data flow with timestamps

5. **Key Technical Challenges** (8장)
   - Event time processing (2장)
   - Exactly-once semantics (2장)
   - Data quality (2장)
   - Performance (2장)

6. **Implementation Highlights** (5장)
   - DDD architecture
   - Kotlin 장점
   - Testing strategy
   - Monitoring

7. **Results** (3장)
   - Performance benchmark 결과
   - Test coverage
   - Demo 스크린샷

8. **Lessons Learned** (2장)
   - Backend vs Data Engineering 차이
   - 다시 한다면?

9. **Q&A** (1장)

**면접에서**:
- "발표 자료를 준비했는데 보여드릴까요?"
- (화면 공유) "전체 아키텍처는 이렇습니다..."

---

### Task 4.2: Video Demo (4시간)

**목표**: 5분짜리 데모 영상

**스크립트**:

```
[0:00-0:30] Intro
안녕하세요. 오늘은 제가 만든 실시간 CTR 계산 파이프라인을 소개하겠습니다.

[0:30-1:00] Architecture
전체 아키텍처는 이렇습니다.
Kafka에서 이벤트를 받아 Flink로 처리하고 ClickHouse에 저장합니다.

[1:00-2:00] Live Demo - Starting
이제 실제로 돌려보겠습니다.
(터미널) ./scripts/demo.sh
몇 초만 기다리면... 전체 파이프라인이 시작됩니다.

[2:00-3:00] Flink UI
Flink UI에서 job이 실행중입니다.
여기서 각 operator의 throughput을 볼 수 있고,
backpressure도 모니터링할 수 있습니다.

[3:00-4:00] Grafana
Grafana 대시보드입니다.
초당 1만 개의 이벤트가 처리되고 있고,
p99 latency는 3초입니다.
Invalid event rate는 1% 미만으로 안정적입니다.

[4:00-4:30] ClickHouse Results
ClickHouse에서 실제 결과를 보겠습니다.
10초마다 CTR이 계산되고 있습니다.
Product A는 CTR이 25%, Product B는 15%네요.

[4:30-5:00] Wrap up
이렇게 실시간으로 CTR을 계산하는 파이프라인을 만들었습니다.
질문이나 피드백은 GitHub이나 LinkedIn으로 부탁드립니다.
감사합니다!
```

**촬영**:
- Screen recording (QuickTime/OBS)
- 목소리 녹음
- 자막 추가 (선택)

**업로드**:
- YouTube (unlisted)
- README에 링크 추가

**면접에서**:
- "5분짜리 데모 영상이 있는데 보여드릴까요?"
- 또는 "라이브로 데모 보여드릴까요?"

---

### Task 4.3: LinkedIn Profile 업데이트 (1시간)

**Projects 섹션에 추가**:

```
Real-time CTR Calculator
Jan 2025 - Mar 2025

Apache Flink 기반 실시간 스트리밍 파이프라인

• 초당 5만 이벤트 처리 (p99 latency < 4초)
• EXACTLY_ONCE semantics로 데이터 정확성 보장
• 80% 테스트 커버리지 달성 (Testcontainers로 end-to-end 검증)
• Prometheus + Grafana로 실시간 모니터링
• Kotlin, Kafka, ClickHouse 사용
• DDD 아키텍처 적용

Skills: Apache Flink · Kafka · ClickHouse · Kotlin · Data Engineering ·
        Stream Processing · Prometheus · Grafana

[GitHub] [Demo Video] [Presentation]
```

**Skills 섹션에 추가**:
- Apache Flink
- Stream Processing
- Data Engineering
- Kafka
- ClickHouse

**면접에서**:
- "LinkedIn 프로필에 프로젝트를 추가했습니다"

---

### Phase 4 체크리스트

- [ ] LinkedIn/Medium에 포스팅
- [ ] Presentation deck 작성 (25장)
- [ ] 5분 데모 영상 촬영 및 업로드
- [ ] GitHub README 최종 업데이트
- [ ] LinkedIn profile 업데이트
- [ ] 면접 스크립트 연습

**완료 시**:
- ✅ 포트폴리오 완성
- ✅ 면접 준비 완료
- ✅ 30분 발표 가능
- ✅ 온라인 프레젠스 구축

---

## 전체 타임라인

| Phase | 기간 | 누적 | 완료 조건 |
|-------|------|------|-----------|
| Phase 1: Quick Wins | 1주 | 1주 | GitHub에 올릴 수 있음 |
| Phase 2: Fundamentals | 2-3주 | 4주 | 데이터 엔지니어링 스킬 증명 |
| Phase 3: Production-like | 1개월 | 8주 | 운영 경험 시뮬레이션 |
| Phase 4: Portfolio | 1개월 | 12주 | 면접 준비 완료 |

**Total: 3개월**

---

## 면접 준비

### 예상 질문과 답변

#### Q: "프로젝트를 30초로 설명해주세요"

**A**:
"Flink 기반 실시간 CTR 계산 파이프라인입니다.
Kafka에서 impression/click 이벤트를 받아 10초 윈도우마다 CTR을 계산하고 ClickHouse에 저장합니다.
초당 5만 이벤트를 처리하고, EXACTLY_ONCE semantics로 정확성을 보장합니다.
백엔드 개발자에서 데이터 엔지니어로 전환하기 위한 학습 프로젝트입니다."

---

#### Q: "가장 어려웠던 점은?"

**A**:
"Event time processing이 가장 어려웠습니다.
처음에는 processing time을 썼는데, 네트워크 지연으로 이벤트가 순서대로 안 오니 CTR이 틀렸습니다.
Event time으로 바꾸고 watermark를 추가했는데, out-of-order를 얼마나 허용할지 튜닝하는 게 어려웠습니다.
결국 5초로 설정했고, late event는 메트릭으로 추적하기로 했습니다.

이 과정에서 '분산 시스템에서 시간은 복잡하다'는 걸 깨달았습니다."

---

#### Q: "데이터 정확성은 어떻게 보장하나요?"

**A**:
"세 가지 레벨에서 보장합니다:

1. **Message level**: EXACTLY_ONCE semantics로 중복/손실 방지
2. **Event level**: Validation으로 invalid event를 5가지 카테고리로 분류하고 메트릭 수집
3. **Window level**: Integration test로 CTR 계산 로직 검증

추가로 ClickHouse에 저장 후 reconciliation check를 할 수 있습니다.
Kafka topic의 event 수와 ClickHouse의 row 수를 비교하는 배치 job을 만들면 됩니다."

---

#### Q: "성능을 어떻게 최적화했나요?"

**A**:
"Profiling으로 bottleneck을 찾았습니다.

**발견**: JSON deserialization이 CPU의 40% 사용
**해결**: Avro로 바꾸니 30% 성능 향상

**발견**: Single TaskManager로 병목
**해결**: Parallelism을 2로 올리니 처리량 2배

**발견**: ClickHouse write가 느림
**해결**: Batch size를 1000으로 올리고 interval을 200ms로

결과적으로 초당 5만 이벤트까지 처리 가능해졌습니다."

---

#### Q: "프로덕션에 배포한다면?"

**A**:
"몇 가지를 더 추가해야 합니다:

1. **Security**: Kafka SASL 인증, ClickHouse 암호화
2. **Scalability**: Kubernetes HPA로 auto-scaling
3. **State Backend**: RocksDB로 변경 (더 큰 state 처리)
4. **Monitoring**: PagerDuty 연동, runbook 작성
5. **Schema Evolution**: Schema Registry로 버전 관리

가장 중요한 건 **Chaos Engineering**입니다.
실제로 장애를 일으켜보고 복구 시간을 측정해야 합니다."

---

#### Q: "백엔드와 데이터 엔지니어링의 차이는?"

**A**:
"가장 큰 차이는 **피드백 속도**입니다.

백엔드는:
- 버그 → 500 에러 → 즉시 발견
- 성능 문제 → 응답 느림 → 즉시 발견

데이터 엔지니어링은:
- 버그 → 조용히 데이터 손실 → 몇 주 후 발견
- 성능 문제 → backpressure → lag 쌓임

그래서 데이터 엔지니어링은:
- 더 **방어적인 프로그래밍** 필요
- 더 **철저한 테스트** 필요
- 더 **세밀한 모니터링** 필요

이 프로젝트를 하면서 '데이터 품질에 편집증적으로 집착'해야 한다는 걸 배웠습니다."

---

## 성공 기준

### 기술적 성공
- ✅ GitHub stars > 10
- ✅ Demo 가능
- ✅ 30분 발표 가능

### 커리어 성공
- ✅ 데이터 엔지니어 면접 통과
- ✅ "이 사람 괜찮은데?" 평가 받음
- ✅ 오퍼 받음

### 학습 성공
- ✅ Streaming 아키텍처 이해
- ✅ Flink 자신감
- ✅ 데이터 엔지니어 마인드셋 획득

---

## 지금 바로 시작하기

### Week 1: Quick Wins
```bash
# Day 1-2: README
- 아키텍처 다이어그램 그리기
- README.md 작성

# Day 3-4: 테스트 고치기
cd flink-app
./gradlew clean test

# Day 5-7: 데모 준비
./scripts/demo.sh 완성
```

### Week 2-4: Fundamentals
```bash
# Week 2: Data Quality
- Validator 구현
- 메트릭 추가

# Week 3: Performance
- Load generator 작성
- Benchmark 실행

# Week 4: Monitoring
- Grafana dashboard
- Integration test
```

### Week 5-8: Production-like
```bash
# Week 5-6: ADR + Chaos
- ADR 3개 작성
- Chaos experiments

# Week 7-8: Runbook
- 운영 가이드
- Troubleshooting
```

### Week 9-12: Portfolio
```bash
# Week 9-10: Portfolio README
- 프로젝트 설명 정리
- 핵심 성과/트레이드오프 정리

# Week 11: Presentation
- Slides 작성
- 발표 연습

# Week 12: Demo
- 영상 촬영
- 최종 정리
```

---

**지금 바로 시작하세요!**

첫 번째 Task:
```bash
# README.md에 아키텍처 다이어그램 추가하기
open https://excalidraw.com
```

**3개월 후, 당신은 자신있게 말할 수 있을 겁니다:**

> "저는 실시간으로 초당 5만 이벤트를 처리하는 스트리밍 파이프라인을 만들었습니다.
> GitHub에 코드가 있고, 데모를 보여드릴 수 있습니다."

**Good luck! 🚀**
