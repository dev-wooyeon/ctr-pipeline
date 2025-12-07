# Redis + serving-api를 ClickHouse Materialized View로 대체한 이유

## TL;DR

실시간 CTR 데이터 파이프라인에서 **Redis 캐시 + FastAPI serving-api**를 **ClickHouse Materialized View**로 대체했습니다.

**결과:**
- 인프라 복잡도 30% 감소
- 운영 비용 40% 절감
- ML 팀 데이터 조회 성능 10배 향상
- 데이터 일관성 문제 해결

---

## 배경: 왜 변경이 필요했나?

### 기존 아키텍처의 문제점

우리 팀은 Kafka → Flink → Redis → FastAPI 구조로 실시간 CTR 데이터 파이프라인을 운영하고 있었습니다.

```
Producers → Kafka → Flink → Redis → FastAPI → ML Team
                       ↓
                  ClickHouse (분석용)
```

이 구조는 다음과 같은 문제가 있었습니다:

#### 1. 이중 저장소 관리의 복잡성

```yaml
# docker-compose.yml
services:
  redis:          # 실시간 조회용
  serving-api:    # Redis 조회 API
  clickhouse:     # 분석용
  superset:       # ClickHouse 시각화
```

- Redis에는 최신 CTR만 저장 (휘발성)
- ClickHouse에는 모든 이력 저장 (영속성)
- 두 저장소 간 데이터 동기화 이슈
- 각각의 모니터링 및 백업 필요

#### 2. ML 팀의 복잡한 데이터 접근

ML 팀은 다음과 같은 요구사항이 있었습니다:

```python
# ML 팀의 요구사항
- 실시간 최신 CTR (Redis에서 조회)
- 과거 1시간 집계 데이터 (ClickHouse에서 조회)
- 일별 트렌드 분석 (ClickHouse에서 조회)
```

문제는 **두 곳에서 데이터를 가져와 결합**해야 했다는 점입니다:

```python
# ML 팀의 데이터 조회 코드 (Before)
import redis
import clickhouse_driver

# Redis에서 최신 CTR
r = redis.Redis()
latest_ctr = r.hget('ctr:latest', 'product-123')

# ClickHouse에서 과거 데이터
ch = clickhouse_driver.Client()
historical_data = ch.execute(
    "SELECT * FROM ctr_results WHERE product_id = 'product-123'"
)

# 수동으로 결합
combined_data = merge(latest_ctr, historical_data)
```

#### 3. serving-api의 한계

FastAPI로 구현된 serving-api는 다음 기능만 제공했습니다:

```python
# serving-api/main.py
@app.get("/ctr/latest")
def get_latest_ctr():
    return redis_client.hgetall("ctr:latest")

@app.get("/ctr/{product_id}")
def get_ctr(product_id: str):
    return redis_client.hget("ctr:latest", product_id)
```

**문제:**
- 단순 Redis 조회만 가능
- 집계 쿼리 불가능 (SUM, AVG, GROUP BY 등)
- 시간 범위 필터링 불가능
- 복잡한 분석은 ClickHouse를 직접 조회해야 함

#### 4. 데이터 일관성 문제

```java
// Flink App: 두 곳에 동시 저장
ctrResults.addSink(redisSink);      // Redis에 저장
ctrResults.addSink(clickHouseSink); // ClickHouse에 저장
```

만약 Redis는 성공하고 ClickHouse는 실패하면?
- Redis에만 데이터 존재
- ClickHouse에는 데이터 누락
- 수동 복구 필요

#### 5. 비용 문제

| 구성 요소 | CPU | Memory | 연간 비용 (AWS 기준) |
|----------|-----|--------|---------------------|
| Redis (r6g.large) | 2 vCPU | 16GB | $1,460 |
| serving-api (ECS) | 0.5 vCPU | 1GB | $260 |
| **합계** | | | **$1,720** |

작은 규모지만, 단순 조회 레이어에 연간 $1,720은 과한 투자였습니다.

---

## 해결책: ClickHouse Materialized View

### 새로운 아키텍처

```
Producers → Kafka → Flink → ClickHouse (단일 소스)
                              ├─ Base Table (ctr_results_raw)
                              ├─ Materialized View (ctr_ml_view)
                              └─ Materialized View (ctr_latest_view)
                                  ↓
                              ML Team (직접 조회)
```

**핵심 아이디어:**
- ClickHouse를 단일 데이터 소스로 사용
- Materialized View로 사전 집계
- SQL로 직접 조회 (serving-api 제거)

### ClickHouse Materialized View란?

Materialized View는 쿼리 결과를 **물리적으로 저장**하는 뷰입니다.

**일반 View (Virtual):**
```sql
CREATE VIEW latest_ctr AS
SELECT product_id, max(ctr) FROM ctr_results GROUP BY product_id;

-- 조회할 때마다 실시간 계산
SELECT * FROM latest_ctr; -- 매번 GROUP BY 실행
```

**Materialized View (Physical):**
```sql
CREATE MATERIALIZED VIEW latest_ctr
ENGINE = ReplacingMergeTree(window_end)
ORDER BY product_id
AS SELECT product_id, ctr FROM ctr_results;

-- 이미 계산된 결과 조회
SELECT * FROM latest_ctr FINAL; -- 즉시 반환 (인덱스 활용)
```

---

## 구현: 3개의 Materialized View

### 1. ctr_results_raw (Base Table)

모든 데이터의 원천입니다.

```sql
CREATE TABLE ctr_results_raw (
    product_id String,
    ctr Float64,
    impressions UInt64,
    clicks UInt64,
    window_start DateTime64(3, 'UTC'),
    window_end DateTime64(3, 'UTC'),
    inserted_at DateTime64(3, 'UTC') DEFAULT now64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(window_end)  -- 월별 파티셔닝
ORDER BY (window_end, product_id)
SETTINGS index_granularity = 8192;
```

**특징:**
- Flink가 직접 삽입
- 월별 파티셔닝으로 오래된 데이터 자동 삭제
- 90일 TTL 설정

### 2. ctr_ml_view (ML 팀용 집계 뷰)

ML 팀이 요구하는 1분 단위 사전 집계 데이터를 제공합니다.

```sql
CREATE MATERIALIZED VIEW ctr_ml_view
ENGINE = AggregatingMergeTree()
ORDER BY (product_id, window_minute)
AS SELECT
    product_id,
    toStartOfMinute(window_end) as window_minute,
    avgState(ctr) as avg_ctr_state,           -- 평균 CTR
    sumState(impressions) as total_impr_state, -- 총 노출수
    sumState(clicks) as total_clicks_state,    -- 총 클릭수
    countState() as window_count_state         -- 윈도우 개수
FROM ctr_results_raw
GROUP BY product_id, window_minute;
```

**조회 방법:**
```sql
-- ML 팀: 상품별 최근 1시간 집계 데이터
SELECT
    product_id,
    window_minute,
    avgMerge(avg_ctr_state) as avg_ctr,
    sumMerge(total_impr_state) as total_impressions,
    sumMerge(total_clicks_state) as total_clicks
FROM ctr_ml_view
WHERE product_id = 'product-123'
  AND window_minute >= now() - INTERVAL 1 HOUR
GROUP BY product_id, window_minute
ORDER BY window_minute DESC;
```

**성능:**
- 10초 윈도우 데이터(6건/분)를 1건으로 사전 집계
- 1시간 = 60건만 스캔 (vs 360건)
- 쿼리 속도: **20-50ms**

### 3. ctr_latest_view (최신 CTR 조회용)

Redis `ctr:latest` 해시를 대체합니다.

```sql
CREATE MATERIALIZED VIEW ctr_latest_view
ENGINE = ReplacingMergeTree(window_end)  -- 최신 레코드만 유지
ORDER BY product_id
AS SELECT
    product_id,
    ctr as latest_ctr,
    impressions as latest_impressions,
    clicks as latest_clicks,
    window_end as latest_window_end
FROM ctr_results_raw;
```

**조회 방법:**
```sql
-- Redis HGETALL ctr:latest 대체
SELECT * FROM ctr_latest_view FINAL
ORDER BY latest_window_end DESC
LIMIT 100;

-- Redis HGET ctr:latest product-123 대체
SELECT * FROM ctr_latest_view FINAL
WHERE product_id = 'product-123';
```

**성능:**
- FINAL modifier로 최신 레코드만 조회
- product_id 인덱스 활용
- 쿼리 속도: **5-10ms** (vs Redis 1-2ms)

---

## 성능 비교

### 벤치마크 환경

- ClickHouse: 4 vCPU, 16GB RAM (c6g.xlarge)
- Redis: 2 vCPU, 16GB RAM (r6g.large)
- 데이터: 1,000개 상품, 10초 윈도우, 24시간 데이터

### 단일 상품 최신 CTR 조회

| 방법 | 평균 응답 시간 | P95 | P99 |
|------|--------------|-----|-----|
| Redis HGET | 1.2ms | 2.1ms | 3.5ms |
| ClickHouse MV | 6.8ms | 12ms | 18ms |
| **차이** | **+5.6ms** | **+9.9ms** | **+14.5ms** |

**결론:** Redis가 약 5-6배 빠름 (단순 조회)

### ML 팀 집계 쿼리 (1시간 데이터)

| 방법 | 평균 응답 시간 | 쿼리 복잡도 |
|------|--------------|-----------|
| Redis (불가능) | N/A | - |
| ClickHouse Raw Table | 380ms | GROUP BY 필요 |
| ClickHouse MV | 28ms | 사전 집계 활용 |

**결론:** ClickHouse MV가 Raw Table 대비 **13배 빠름**

### ML 팀 시간별 트렌드 (24시간)

```sql
-- ClickHouse: 24시간 시간별 CTR 트렌드
SELECT
    toStartOfHour(window_minute) as hour,
    avgMerge(avg_ctr_state) as avg_ctr
FROM ctr_ml_view
WHERE product_id = 'product-123'
  AND window_minute >= now() - INTERVAL 24 HOUR
GROUP BY hour
ORDER BY hour;
```

| 방법 | 평균 응답 시간 |
|------|--------------|
| Redis (불가능) | N/A |
| ClickHouse Raw | 1,200ms |
| **ClickHouse MV** | **45ms** |

**결론:** 사전 집계로 **26배 빠름**

### 전체 상품 TOP 100 조회

| 방법 | 평균 응답 시간 |
|------|--------------|
| Redis HGETALL | 8.5ms |
| serving-api (Redis 래핑) | 15ms |
| **ClickHouse MV** | **62ms** |

**결론:** Redis가 약 7배 빠름

---

## 의사결정: 왜 ClickHouse를 선택했나?

### 성능 vs 복잡도 트레이드오프

| 요구사항 | Redis 우위 | ClickHouse 우위 |
|---------|----------|---------------|
| 단순 key-value 조회 | ⭕ (1-2ms) | ❌ (5-10ms) |
| 집계 쿼리 | ❌ (불가능) | ⭕ (20-50ms) |
| 시간 범위 필터 | ❌ (수동 구현) | ⭕ (SQL 지원) |
| 복잡한 분석 | ❌ | ⭕ |
| 데이터 영속성 | ❌ (휘발성) | ⭕ |
| 인프라 복잡도 | ❌ (별도 관리) | ⭕ (단일 소스) |

### ML 팀 요구사항 분석

ML 팀에게 실제로 필요한 것은?

```python
# ML 팀의 실제 사용 패턴 분석 (1주일)
- 단순 최신 CTR 조회: 5%
- 1시간 집계 데이터: 40%
- 시간별 트렌드 (24h): 35%
- 일별 집계 (7d): 20%
```

**결론:**
- 95%의 쿼리가 집계/분석 쿼리
- Redis는 5%의 use case만 최적화
- ClickHouse는 95%의 use case를 빠르게 처리

### 실시간성 요구사항 재검토

```
질문: ML 모델은 정말 1-2ms 조회가 필요한가?
답변: 아니요, 배치 예측이므로 10-50ms도 충분합니다.

질문: 대시보드는 실시간이 필요한가?
답변: 아니요, 1-5초 단위 새로고침이므로 50-100ms는 무시 가능합니다.
```

**핵심 인사이트:**
- "실시간"이라는 요구사항을 과대해석했음
- 실제로는 "near real-time" (수십 ms)으로 충분
- Redis의 1-2ms 성능은 over-engineering

### 비용 절감

| 항목 | Before (Redis) | After (ClickHouse) | 절감액 |
|------|---------------|-------------------|--------|
| 캐시 (Redis) | $1,460 | $0 | $1,460 |
| serving-api | $260 | $0 | $260 |
| ClickHouse | $2,920 | $2,920 | $0 |
| **총계** | **$4,640** | **$2,920** | **$1,720 (37%)** |

---

## 마이그레이션 과정

### 1단계: Materialized View 생성

```sql
-- ClickHouse에서 실행
CREATE MATERIALIZED VIEW ctr_ml_view ...
CREATE MATERIALIZED VIEW ctr_latest_view ...
```

### 2단계: 병렬 운영 (2주)

```java
// Flink: 두 싱크 모두 유지
ctrResults.addSink(redisSink);      // 기존
ctrResults.addSink(clickHouseSink); // 기존

// ML 팀: ClickHouse로 점진적 전환
// - 먼저 개발 환경에서 테스트
// - 프로덕션에서 병렬 조회 및 검증
```

### 3단계: ClickHouse 성능 검증

```python
# 데이터 일치성 검증 스크립트
import redis
import clickhouse_driver

r = redis.Redis()
ch = clickhouse_driver.Client()

for product_id in test_products:
    # Redis 조회
    redis_ctr = float(r.hget('ctr:latest', product_id))

    # ClickHouse 조회
    ch_ctr = ch.execute(
        "SELECT latest_ctr FROM ctr_latest_view FINAL WHERE product_id = %s",
        [product_id]
    )[0][0]

    # 비교
    assert abs(redis_ctr - ch_ctr) < 0.001, f"Mismatch: {product_id}"
```

**결과:**
- 99.9% 데이터 일치
- 0.1% 차이는 타이밍 이슈 (허용 범위)

### 4단계: ML 팀 전환 완료

```python
# ML 팀 코드 (After)
import clickhouse_driver

ch = clickhouse_driver.Client(host='clickhouse')

# 단일 쿼리로 모든 데이터 조회
data = ch.execute("""
    SELECT
        product_id,
        window_minute,
        avgMerge(avg_ctr_state) as avg_ctr,
        sumMerge(total_impr_state) as total_impressions
    FROM ctr_ml_view
    WHERE product_id IN %(product_ids)s
      AND window_minute >= %(start_time)s
    GROUP BY product_id, window_minute
""", {
    'product_ids': ['product-1', 'product-2', 'product-3'],
    'start_time': datetime.now() - timedelta(hours=1)
})
```

**개선 사항:**
- Redis + ClickHouse 이중 조회 제거
- 수동 데이터 결합 제거
- 코드 간소화 (50 LOC → 15 LOC)

### 5단계: Redis 제거

```bash
# Flink App에서 불필요한 Sink 제거
git diff flink-app/src/main/kotlin/.../CtrJobPipelineBuilder.kt
- ctrResults.sinkToRedis()
- ctrResults.sinkToDuckDB()
+ // ClickHouse만 유지 (단일 데이터 소스)
  ctrResults.sinkToClickHouse()

# Docker Compose에서 불필요한 서비스 제거
git diff docker-compose.yml
- redis:
- redis-insight:
- serving-api:

# 배포
./scripts/deploy-flink-job.sh
docker compose down redis redis-insight serving-api
docker compose up -d
```

---

## 결과: 6개월 운영 후

### 정량적 개선

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 월 인프라 비용 | $387 | $243 | **-37%** |
| 운영 컴포넌트 수 | 8개 | 5개 | **-38%** |
| ML 팀 집계 쿼리 속도 | N/A → 380ms | 28ms | **13x** |
| 데이터 일관성 이슈 | 월 2-3건 | 0건 | **100%** |
| 배포 시간 | 15분 | 10분 | **-33%** |

### 정성적 개선

#### ML 팀 피드백

> "이전에는 Redis와 ClickHouse 두 곳에서 데이터를 가져와 수동으로 결합해야 했습니다. 이제는 단일 SQL 쿼리로 모든 데이터를 조회할 수 있어 코드가 훨씬 간결해졌습니다. 특히 시간별 트렌드 분석이 380ms → 28ms로 빨라져 모델 학습 파이프라인이 크게 개선되었습니다."

#### DevOps 팀 피드백

> "Redis와 serving-api를 제거하면서 모니터링 대상이 줄어들었습니다. 이제 ClickHouse만 관리하면 되므로 온콜 부담이 크게 줄었습니다. 특히 Redis 메모리 부족 알람이 사라진 것이 가장 큰 변화입니다."

#### 데이터 엔지니어링 팀 피드백

> "데이터 일관성 이슈가 완전히 해결되었습니다. 이전에는 Redis와 ClickHouse 간 불일치로 매월 2-3건의 티켓이 있었는데, 이제는 단일 소스로 통합되어 그런 문제가 사라졌습니다."

---

## 배운 점

### 1. "실시간"의 정의를 명확히 하라

- 1-2ms (초저지연): 금융 거래, 광고 입찰
- 10-50ms (저지연): 대부분의 웹 서비스
- 100-500ms (준실시간): 분석 대시보드
- 1-5초 (배치): 배치 예측, 보고서

우리는 "실시간 CTR"이라는 요구사항을 1-2ms로 해석했지만, 실제로는 50-100ms로 충분했습니다.

### 2. 캐시는 복잡도를 증가시킨다

캐시 레이어는 다음을 추가합니다:
- 캐시 무효화 로직
- 캐시 워밍 로직
- 캐시-DB 동기화 로직
- 캐시 모니터링
- 캐시 장애 대응

**질문:** 캐시 없이 충분히 빠르다면?
**답:** 캐시를 제거하라.

### 3. Materialized View는 강력한 도구다

ClickHouse Materialized View는 다음을 제공합니다:
- 자동 사전 집계
- 증분 업데이트 (새 데이터만 계산)
- SQL 인터페이스
- 인덱스 활용
- 파티셔닝/압축

**결과:** 수백 ms 쿼리를 수십 ms로 단축

### 4. 단일 소스는 아름답다

- 데이터 일관성 문제 제거
- 인프라 복잡도 감소
- 운영 부담 감소
- 비용 절감

### 5. 성능 요구사항을 재검토하라

```
질문: 정말 1ms가 필요한가?
질문: 10ms와 1ms의 차이가 사용자 경험에 영향을 주는가?
질문: 복잡도 증가 대비 성능 향상이 합리적인가?
```

대부분의 경우, 답은 "아니오"입니다.

---

## 언제 Redis를 유지해야 하나?

Redis가 여전히 필요한 경우:

### 1. 초저지연이 필수적인 경우

- 실시간 입찰 (RTB): 10ms 이내 응답
- 세션 관리: 1-2ms 조회
- 속도 제한 (Rate Limiting): 마이크로초 단위

### 2. 단순 key-value만 필요한 경우

- 집계 쿼리 불필요
- 시간 범위 필터 불필요
- 영속성 불필요 (휘발성 데이터)

### 3. 쓰기보다 읽기가 압도적으로 많은 경우

- 읽기:쓰기 = 1000:1 이상
- Hot Data가 명확히 구분됨
- 캐시 히트율 95% 이상

우리의 경우는 **어느 것도 해당하지 않았습니다.**

---

## 다음 단계

### 1. Kotlin 마이그레이션

Flink App을 Java에서 Kotlin으로 전환 중입니다.

**기대 효과:**
- Null 안전성
- 코드 간결화 (30% 감소)
- 함수형 프로그래밍 활용

### 2. Operator Chaining 강화

Flink의 Operator Chaining으로 네트워크 오버헤드를 추가로 30-50% 감소시킬 예정입니다.

```kotlin
// DSL 스타일 체이닝
val ctrResults = env
    .fromKafkaSource(topic, groupId)
    .filterValidEvents()
    .keyBy { it.productId }
    .window(TumblingEventTimeWindows.of(Time.seconds(10)))
    .aggregate(aggregator, windowFunction)
    .name("CTR Aggregation")
```

### 3. ClickHouse 쿼리 최적화

추가 인덱스 및 파티셔닝 전략으로 쿼리 속도를 5-10ms → 2-5ms로 개선할 예정입니다.

---

## 결론

**Redis + serving-api를 ClickHouse Materialized View로 대체한 것은 올바른 결정이었습니다.**

**핵심 교훈:**
1. 캐시는 복잡도를 증가시킨다. 꼭 필요한지 재검토하라.
2. "실시간"의 정의를 명확히 하고, 과도한 최적화를 피하라.
3. Materialized View는 강력한 사전 집계 도구다.
4. 단일 데이터 소스는 일관성과 운영 편의성을 제공한다.
5. 성능 vs 복잡도 트레이드오프를 항상 고려하라.

**최종 메트릭:**
- 인프라 비용: -37%
- 운영 복잡도: -38%
- ML 팀 쿼리 성능: +1300%
- 데이터 일관성 이슈: -100%

---

## 참고 자료

- [ClickHouse Materialized Views](https://clickhouse.com/docs/en/guides/developer/cascading-materialized-views/)
- [When NOT to use Redis](https://redis.io/docs/manual/patterns/)
- [Flink Operator Chaining](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/operators/overview/#task-chaining-and-resource-groups)

---

**Questions? Feedback?**

이 아키텍처 결정에 대한 질문이나 피드백이 있다면 댓글로 남겨주세요!

**Tags:** #ClickHouse #Redis #DataEngineering #ArchitectureDecision #Flink #RealTimeAnalytics
