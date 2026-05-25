# Flink App Kotlin 마이그레이션 및 아키텍처 개선 계획

## 📋 개요

이 문서는 flink-app을 Java에서 Kotlin으로 마이그레이션하고, Redis + serving-api를 제거하여 ClickHouse Materialized View로 대체하는 전체 작업 계획을 설명합니다.

**목표:**
- Flink 애플리케이션을 Kotlin으로 완전 전환
- 데이터 파이프라인 체이닝 최적화 강화
- Redis, serving-api, DuckDB 제거로 아키텍처 단순화
- ClickHouse를 단일 데이터 소스로 통합 (Base Table + Materialized Views)

**예상 효과:**
- 코드 가독성 및 생산성 향상 (Kotlin의 간결한 문법)
- null 안전성 및 불변성 보장
- 인프라 복잡도 50% 감소 (Redis, serving-api, DuckDB 제거)
- 유지보수 비용 절감
- 단일 데이터 소스로 데이터 일관성 보장

---

## 🏗️ 아키텍처 변경

### 변경 전 (현재)
```
┌──────────┐   ┌───────┐   ┌───────────┐   ┌───────┐   ┌──────────────┐
│ Producers│──>│ Kafka │──>│ Flink App │──>│ Redis │──>│ Serving API  │
└──────────┘   └───────┘   └───────────┘   └───────┘   └──────────────┘
                                           │
                                           ├──> ClickHouse
                                           └──> DuckDB
```

### 변경 후 (목표)
```
┌──────────┐   ┌───────┐   ┌─────────────────┐   ┌────────────────────┐
│ Producers│──>│ Kafka │──>│ Flink App (Kt)  │──>│ ClickHouse (단일)  │
└──────────┘   └───────┘   └─────────────────┘   └────────────────────┘
                                                   │
                                                   ├──> Base Table (ctr_results_raw)
                                                   ├──> ML View (ctr_ml_view)
                                                   ├──> Latest View (ctr_latest_view)
                                                   └──> Hourly Stats (ctr_hourly_stats)
                                                         ↓
                                                   Superset (대시보드/분석)
```

---

## 📝 작업 단계

### Phase 1: 준비 및 설정 (1-2일)

#### 1.1 Gradle Kotlin 플러그인 설정
- [x] `build.gradle` → `build.gradle.kts` 변환 및 Kotlin DSL 설정
- [x] Kotlin JVM 플러그인 추가
- [x] Kotlin 표준 라이브러리/reflect 의존성 추가
- [x] Java/Kotlin 혼용 컴파일 환경 구성 (`gragle.properties` 도 포함)

**파일:**
- `flink-app/build.gradle.kts` (새로 생성)
- `flink-app/settings.gradle.kts` (새로 생성)

**체크리스트:**
```groovy
plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Flink dependencies...
}

kotlin {
    jvmToolchain(17)
}
```

#### 1.2 ClickHouse Materialized View 설계
- [x] Base table 스키마 설계
- [x] Materialized View 쿼리 작성 (ML/Latest 뷰)
- [x] 파티셔닝/인덱스 전략 적용
- [x] ML 팀 요구사항을 문서화하고 초기화 스크립트에 반영

**파일:**
- `scripts/init-clickhouse.sh` (새로 생성)
- `docs/architecture/CLICKHOUSE_MATERIALIZED_VIEW.md` (새로 생성)

---

### Phase 2: Kotlin 마이그레이션 (5-7일)

#### 2.1 Domain Layer 마이그레이션
도메인 모델은 순수 비즈니스 로직이므로 가장 먼저 변환합니다.

**우선순위 (모두 Kotlin으로 전환 완료):**
- [x] `domain/model/Event.java` → `Event.kt`
- [x] `domain/model/EventCount.java` → `EventCount.kt`
- [x] `domain/model/CTRResult.java` → `CTRResult.kt`
- [x] `domain/service/EventCountAggregator.java` → `EventCountAggregator.kt`
- [x] `domain/service/CTRResultWindowProcessFunction.java` → `CTRResultWindowProcessFunction.kt`

**변환 예시:**
```kotlin
// Before (Java)
@Data
@AllArgsConstructor
public class Event {
    private String eventType;
    private String productId;
    private LocalDateTime timestamp;

    public boolean isValid() {
        return eventType != null && productId != null;
    }
}

// After (Kotlin)
data class Event(
    val eventType: String?,
    val productId: String?,
    val timestamp: LocalDateTime
) {
    fun isValid(): Boolean = eventType != null && productId != null
}
```

#### 2.2 Infrastructure Layer 마이그레이션

**순서 (완료):**
1. `config/` 패키지
   - [x] `KafkaProperties.kt`
   - [x] `CtrJobProperties.kt`
   - [x] `ClickHouseProperties.kt`
   - [x] `DuckDBProperties.kt`
   - [x] `RedisProperties.kt`

2. `infrastructure/flink/source/` 패키지
   - [x] `KafkaSourceFactory.kt`
   - [x] `EventDeserializationSchema.kt`

3. `infrastructure/flink/sink/` 패키지
   - [x] `ClickHouseSink.kt`
   - [x] `DuckDBSink.kt`
   - [x] `RedisSink.kt`
   - [x] `DuckDBRichSink.kt`
   - [x] `RedisRichSink.kt`

4. `infrastructure/flink/` 패키지
   - [x] `FlinkEnvironmentFactory.kt`
   - [x] `CtrJobPipelineBuilder.kt` ⭐ (체이닝 최적화 강화)

#### 2.3 Application Layer 마이그레이션

**순서:**
1. `application/CtrJobService.kt`
2. `CtrApplication.kt` (Main 클래스)

#### 2.4 Test 마이그레이션

**순서:**
1. Domain 테스트 우선
   - `domain/model/CTRResultTest.kt`
   - `domain/model/EventCountTest.kt`
   - `domain/service/EventCountAggregatorTest.kt`

2. Infrastructure 테스트
   - `infrastructure/flink/source/deserializer/EventDeserializationSchemaTest.kt`

---

### Phase 3: 체이닝 최적화 강화 (2-3일)

#### 3.1 CtrJobPipelineBuilder 개선

**상태:**
- [x] Kotlin DSL 스타일로 `CtrJobPipelineBuilder` 재작성
- [x] 소스/필터 체이닝을 Kotlin 확장 함수로 구성 (`kafkaPipeline` helper)
- [x] Aggregator+ProcessWindowFunction을 명시적으로 주입하며 타입 안전성 확보
- [x] Redis/DuckDB/ClickHouse 싱크에 `slotSharingGroup`/`disableChaining` 적용

**개선 사항:**
- Kotlin의 확장 함수 활용 (`StreamExecutionEnvironment.kafkaPipeline`, `SingleOutputStreamOperator.chainSink`)
- DSL 스타일 파이프라인 구성으로 코드 중복 제거
- 타입 안전성 강화 (제네릭을 명시적 인터페이스로 주입)

**예시:**
```kotlin
fun build(env: StreamExecutionEnvironment): DataStream<CTRResult> {
    // Source Pipeline with Chaining
    val impressionStream = env
        .fromKafkaSource(
            topic = properties.impressionTopic,
            groupId = properties.groupId
        ) {
            watermarkStrategy = WatermarkStrategy
                .forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner { event, _ -> event.eventTimeMillisUtc }
            name = "Impression Kafka Source"
            uid = "impression-source"
            slotSharingGroup = "source-group"
        }
        .filterNotNull("Filter Null Impressions", "filter-null-impressions")
        .filter({ it.isValid() }, "Validate Impressions", "validate-impressions")

    val clickStream = env
        .fromKafkaSource(
            topic = properties.clickTopic,
            groupId = properties.groupId
        ) {
            watermarkStrategy = WatermarkStrategy
                .forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner { event, _ -> event.eventTimeMillisUtc }
            name = "Click Kafka Source"
            uid = "click-source"
            slotSharingGroup = "source-group"
        }
        .filterNotNull("Filter Null Clicks", "filter-null-clicks")
        .filter({ it.isValid() }, "Validate Clicks", "validate-clicks")

    // Aggregation Pipeline
    val ctrResults = impressionStream
        .union(clickStream)
        .filter({ it.hasProductId() }, "Filter by ProductId", "filter-product-id")
        .slotSharingGroup("processing-group")
        .keyBy { it.productId }
        .window(TumblingEventTimeWindows.of(Time.seconds(10)))
        .allowedLateness(Time.seconds(5))
        .aggregate(aggregator, windowFunction)
        .name("CTR Aggregation")
        .uid("ctr-aggregation")

    // Sink (독립 실행)
    ctrResults.sinkToClickHouse(clickHouseSink)

    return ctrResults
}
```

#### 3.2 체이닝 검증 및 성능 측정

- [ ] Flink Web UI에서 체이닝 시각화 확인
- [ ] 메트릭 수집 및 분석
- [ ] 네트워크 오버헤드 측정

---

### Phase 4: Redis, serving-api, DuckDB 제거 (1-2일)

#### 4.1 코드 제거

**삭제할 파일:**
```
flink-app/src/main/java/com/example/ctr/config/RedisProperties.java
flink-app/src/main/java/com/example/ctr/config/DuckDBProperties.java
flink-app/src/main/java/com/example/ctr/infrastructure/flink/sink/RedisSink.java
flink-app/src/main/java/com/example/ctr/infrastructure/flink/sink/DuckDBSink.java
flink-app/src/main/java/com/example/ctr/infrastructure/flink/sink/impl/RedisRichSink.java
flink-app/src/main/java/com/example/ctr/infrastructure/flink/sink/impl/DuckDBRichSink.java
serving-api/ (전체 디렉토리)
```

**수정할 파일:**
- `flink-app/build.gradle.kts`: Jedis, DuckDB JDBC 의존성 제거
- `flink-app/src/main/kotlin/com/example/ctr/infrastructure/flink/CtrJobPipelineBuilder.kt`: Redis, DuckDB Sink 제거
- `flink-app/src/main/kotlin/com/example/ctr/CtrApplication.kt`: Redis, DuckDB 설정 제거

#### 4.2 Docker Compose 수정

**docker-compose.yml 변경:**
```yaml
# 삭제할 서비스:
# - redis
# - redis-insight
# - serving-api

# 삭제할 볼륨:
# - redis-data
# - redisinsight
# - duckdb-data

# 유지할 서비스:
# - kafka1, kafka2, kafka3
# - kafka-ui
# - flink-jobmanager
# - flink-taskmanager
# - clickhouse (단일 데이터 소스)
# - superset (대시보드/분석)
```

#### 4.3 스크립트 수정

**scripts/setup.sh:**
- Redis 관련 섹션 제거
- serving-api 빌드/배포 단계 제거
- ClickHouse Materialized View 초기화 추가

**scripts/deploy-flink-job.sh:**
- Redis 연결 확인 로직 제거

---

### Phase 5: ClickHouse Materialized View 구축 (2-3일)

#### 5.1 Base Table 생성

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

#### 5.2 Materialized View 생성

**실시간 집계 뷰 (ML 팀용):**
```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS default.ctr_ml_view
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(window_end)
ORDER BY (product_id, window_end)
AS SELECT
    product_id,
    toStartOfInterval(window_end, INTERVAL 1 MINUTE) as window_minute,
    avgState(ctr) as avg_ctr,
    sumState(impressions) as total_impressions,
    sumState(clicks) as total_clicks,
    maxState(window_end) as latest_window
FROM default.ctr_results_raw
GROUP BY product_id, window_minute;
```

**최신 CTR 조회 뷰:**
```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS default.ctr_latest_view
ENGINE = ReplacingMergeTree(window_end)
ORDER BY product_id
AS SELECT
    product_id,
    argMax(ctr, window_end) as latest_ctr,
    argMax(impressions, window_end) as latest_impressions,
    argMax(clicks, window_end) as latest_clicks,
    max(window_end) as window_end
FROM default.ctr_results_raw
GROUP BY product_id;
```

#### 5.3 쿼리 성능 최적화

- [ ] 인덱스 튜닝
- [ ] 파티셔닝 전략 검증
- [ ] 쿼리 프로파일링
- [ ] TTL 정책 설정

---

### Phase 6: 문서 및 테스트 업데이트 (2일)

#### 6.1 문서 업데이트

**수정할 문서:**
- `README.md`: 아키텍처 다이어그램, 기술 스택, API 엔드포인트 섹션 수정
- `docs/flink/operator-chaining/OPERATOR_CHAINING.md`: Kotlin 코드 예시로 업데이트
- 새 문서 생성:
  - `docs/migrations/KOTLIN_MIGRATION_GUIDE.md`
  - `docs/architecture/CLICKHOUSE_MATERIALIZED_VIEW.md`
  - `docs/architecture/PERFORMANCE_COMPARISON.md` (Redis vs Materialized View)

#### 6.2 테스트 업데이트

**단위 테스트:**
- [ ] 모든 Kotlin 코드 단위 테스트 작성/업데이트
- [ ] Materialized View 쿼리 테스트

**통합 테스트:**
- [ ] 전체 파이프라인 E2E 테스트
- [ ] ClickHouse 데이터 검증

**성능 테스트:**
- [ ] k6 스크립트 업데이트 (serving-api 제거 반영)
- [ ] ClickHouse 쿼리 성능 벤치마크

---

### Phase 7: 배포 및 검증 (1-2일)

#### 7.1 배포 준비

- [ ] 모든 변경사항 검토
- [ ] 배포 체크리스트 확인
- [ ] 롤백 계획 수립

#### 7.2 배포 실행

```bash
# 1. 기존 환경 정리
docker compose down -v

# 2. 코드 빌드
cd flink-app
./gradlew clean shadowJar

# 3. 전체 환경 재시작
cd ..
./scripts/setup.sh

# 4. 검증
./scripts/verify-deployment.sh
```

#### 7.3 검증 항목

- [ ] Flink Job이 정상적으로 실행되는가?
- [ ] ClickHouse Base Table에 데이터가 정상적으로 저장되는가?
- [ ] Materialized View들이 자동으로 업데이트되는가?
- [ ] ML 팀이 Materialized View를 조회할 수 있는가?
- [ ] Superset 대시보드가 정상 작동하는가?
- [ ] 쿼리 성능이 기대치(서브초)를 충족하는가?

---

## 🔍 체크리스트

### 코드 품질
- [ ] 모든 Kotlin 코드가 Kotlin 컨벤션을 따르는가?
- [ ] null 안전성이 보장되는가?
- [ ] 불변성이 적절히 활용되는가?
- [ ] 테스트 커버리지가 80% 이상인가?

### 성능
- [ ] 체이닝이 올바르게 적용되었는가?
- [ ] 슬롯 공유 그룹이 적절히 설정되었는가?
- [ ] Materialized View 쿼리 성능이 서브초(sub-second) 수준인가?

### 문서
- [ ] 모든 변경사항이 문서에 반영되었는가?
- [ ] 아키�ecture 다이어그램이 업데이트되었는가?
- [ ] 마이그레이션 가이드가 작성되었는가?

### 배포
- [ ] Docker Compose가 정상 작동하는가?
- [ ] 스크립트들이 올바르게 수정되었는가?
- [ ] 롤백 계획이 수립되었는가?

---

## 📊 예상 일정

| Phase | 작업 내용 | 예상 기간 |
|-------|----------|----------|
| Phase 1 | 준비 및 설정 | 1-2일 |
| Phase 2 | Kotlin 마이그레이션 | 5-7일 |
| Phase 3 | 체이닝 최적화 | 2-3일 |
| Phase 4 | Redis/serving-api/DuckDB 제거 | 1-2일 |
| Phase 5 | Materialized View 구축 | 2-3일 |
| Phase 6 | 문서 및 테스트 | 2일 |
| Phase 7 | 배포 및 검증 | 1-2일 |
| **합계** | | **14-21일** |

---

## 🚨 위험 요소 및 대응 방안

### 위험 1: Kotlin 마이그레이션 중 버그 발생
**대응:** 단계별 마이그레이션 + 각 단계마다 테스트 실행

### 위험 2: Materialized View 성능 미달
**대응:** 사전 벤치마크 수행, 인덱스 및 파티셔닝 최적화

### 위험 3: 데이터 손실 및 복구 전략
**대응:**
- ClickHouse 자체 백업 설정 (Incremental backup)
- 병렬 운영 기간 동안 데이터 일치성 검증
- 롤백 계획 수립 (스냅샷 활용)

---

## 📚 참고 자료

- [Kotlin for Apache Flink](https://kotlinlang.org/docs/jvm-get-started.html)
- [ClickHouse Materialized Views](https://clickhouse.com/docs/en/guides/developer/cascading-materialized-views/)
- [Flink Operator Chaining](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/operators/overview/#task-chaining-and-resource-groups)
- [Gradle Kotlin DSL](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
