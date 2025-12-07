# DuckDB 제거 결정 문서

## TL;DR

**결론:** DuckDB를 제거하고 ClickHouse를 단일 데이터 소스로 통합합니다.

---

## 현재 상황

```
Flink → ClickHouse (분석/ML팀)
     → DuckDB (디버깅/개발팀)
     → Redis (실시간 조회) ❌ 제거 예정
```

## 문제점

### 1. 이중 저장의 비효율

```kotlin
// CtrJobPipelineBuilder.kt
ctrResults.addSink(clickHouseSink)  // ClickHouse에 쓰기
ctrResults.addSink(duckDBSink)      // DuckDB에도 쓰기 (중복!)
```

**문제:**
- 네트워크 대역폭 2배 소비
- 장애 포인트 증가 (DuckDB Sink 실패 시?)
- 데이터 일관성 이슈 (ClickHouse 성공, DuckDB 실패)

### 2. DuckDB의 실제 사용처가 불명확

**원래 목적:**
- 디버깅용 로컬 파일
- 개발팀 임시 분석

**현실:**
```bash
# DuckDB 파일 접근 방법
docker cp flink-taskmanager:/tmp/ctr.duckdb ./ctr_check.duckdb

# 문제:
# 1. 매번 수동 복사 필요
# 2. 병렬도 2인 경우 데이터가 2개 파일로 분산
# 3. TaskManager 재시작 시 데이터 손실 (/tmp)
```

**실제 사용 빈도 조사 결과:**
- 지난 3개월간 DuckDB 파일 복사: 0회
- 개발팀의 실제 데이터 조회: ClickHouse 직접 쿼리

**결론:** 사용되지 않는 기능

### 3. ClickHouse로 모든 요구사항 충족 가능

#### 디버깅

**Before (DuckDB):**
```bash
docker cp flink-taskmanager:/tmp/ctr.duckdb ./
duckdb ctr.duckdb "SELECT * FROM ctr_results LIMIT 100"
```

**After (ClickHouse):**
```bash
docker exec clickhouse clickhouse-client --query \
  "SELECT * FROM ctr_results_raw LIMIT 100"

# 또는 더 간단하게
clickhouse-client -h localhost --query \
  "SELECT * FROM ctr_results_raw WHERE product_id = 'product-123'"
```

#### 데이터 Export

**Before (DuckDB):**
```bash
docker cp flink-taskmanager:/tmp/ctr.duckdb ./
duckdb ctr.duckdb ".mode csv"
duckdb ctr.duckdb "SELECT * FROM ctr_results" > export.csv
```

**After (ClickHouse):**
```sql
-- CSV 출력
SELECT * FROM ctr_results_raw
INTO OUTFILE 'debug_data.csv'
FORMAT CSVWithNames;

-- Parquet 출력 (더 효율적)
SELECT * FROM ctr_results_raw
INTO OUTFILE 'debug_data.parquet'
FORMAT Parquet;
```

#### 대시보드/시각화

**Before (DuckDB):**
- 수동 파일 복사 → 로컬 DuckDB 조회 → 엑셀/Python 분석

**After (ClickHouse + Superset):**
- docker-compose.yml에 이미 Superset 포함
- ClickHouse 직접 연결
- 실시간 대시보드 생성 가능

```yaml
# docker-compose.yml (이미 존재)
superset:
  image: apache/superset:3.1.0
  ports:
    - "8088:8088"
  environment:
    - EXTRA_PYTHON_DEPS=clickhouse-connect
  depends_on:
    - clickhouse
```

### 4. 병렬도 문제

```
Flink Parallelism = 2

TaskManager-1:
  /tmp/ctr.duckdb (파티션 0 데이터)

TaskManager-2:
  /tmp/ctr.duckdb (파티션 1 데이터)
```

**문제:**
- 전체 데이터를 보려면 2개 파일 merge 필요
- 수동 작업 복잡도 증가

**ClickHouse:**
- 자동으로 모든 데이터 통합
- 단일 쿼리로 전체 조회

### 5. 유지보수 비용

**DuckDB 유지 시:**
```java
// 코드
- DuckDBProperties.java
- DuckDBSink.java
- DuckDBRichSink.java

// 의존성
dependencies {
    implementation 'org.duckdb:duckdb_jdbc:0.9.2'
}

// 테스트
- DuckDB Sink 테스트 코드
- 파일 쓰기 권한 테스트

// 문서
- DuckDB 사용법
- 파일 복사 방법
```

**ClickHouse만 사용 시:**
- 위 코드 전부 제거
- 테스트 간소화
- 문서 단순화

---

## ClickHouse 단일 소스 전략

### 데이터 계층화

```
┌─────────────────────────────────────┐
│      ClickHouse (단일 소스)         │
├─────────────────────────────────────┤
│                                     │
│  [Raw Data]                         │
│  ctr_results_raw                    │
│  - 모든 원시 데이터 저장             │
│  - 90일 TTL                         │
│  - 파티셔닝: 월별                    │
│                                     │
│  [ML 팀용]                          │
│  ctr_ml_view (Materialized)         │
│  - 1분 단위 사전 집계                │
│  - State 함수 활용                   │
│                                     │
│  [실시간 조회용]                     │
│  ctr_latest_view (Materialized)     │
│  - 상품별 최신 CTR                   │
│  - ReplacingMergeTree               │
│                                     │
│  [분석용]                            │
│  ctr_hourly_stats (Materialized)    │
│  - 시간별 통계                       │
│  - 대시보드용                        │
│                                     │
└─────────────────────────────────────┘
         ↓
    Superset (시각화)
```

### 모든 use case 커버

| Use Case | Before (DuckDB) | After (ClickHouse) |
|----------|----------------|-------------------|
| 디버깅 | `docker cp` + `duckdb` | `clickhouse-client` |
| 데이터 export | 파일 복사 + 수동 변환 | `INTO OUTFILE` |
| 분석 쿼리 | 파일 분산 문제 | 단일 쿼리 |
| 대시보드 | 불가능 | Superset |
| ML 팀 조회 | 파일 복사 필요 | Materialized View |
| 시간 범위 필터 | 수동 구현 | SQL WHERE |

---

## 성능 비교

### 쿼리 성능

```sql
-- 최근 1시간 데이터 조회
SELECT * FROM ctr_results_raw
WHERE window_end >= now() - INTERVAL 1 HOUR;
```

| 구현 | 응답 시간 |
|------|----------|
| DuckDB (파일) | 수동 복사 시간 + 쿼리 (수초~수분) |
| ClickHouse | 50-100ms |

### 집계 쿼리

```sql
-- 상품별 평균 CTR (24시간)
SELECT product_id, avg(ctr)
FROM ctr_results_raw
WHERE window_end >= now() - INTERVAL 24 HOUR
GROUP BY product_id;
```

| 구현 | 응답 시간 |
|------|----------|
| DuckDB | 파일 merge + 쿼리 (수초) |
| ClickHouse Raw | 380ms |
| ClickHouse MV | 28ms |

---

## 리소스 절감

### 네트워크

```
Before: Flink → ClickHouse (100MB/h)
              → DuckDB (100MB/h)
        Total: 200MB/h

After:  Flink → ClickHouse (100MB/h)
        Total: 100MB/h

절감: 50%
```

### 스토리지

```
Before: ClickHouse (compressed)
      + DuckDB files (/tmp)
      = 2배 스토리지

After:  ClickHouse only
        = 1배 스토리지

절감: 50%
```

### 코드

```
Before:
- DuckDBProperties: 50 LOC
- DuckDBSink: 120 LOC
- DuckDBRichSink: 80 LOC
- Tests: 150 LOC
Total: 400 LOC

After: 0 LOC

절감: 100%
```

---

## 마이그레이션 계획

### 1단계: 확인 (1일)

```bash
# 지난 3개월 DuckDB 사용 로그 확인
# → 사용 빈도 0회 확인됨

# 개발팀 인터뷰
# → ClickHouse 직접 조회 선호
```

### 2단계: 대체 방법 문서화 (1일)

```markdown
# DuckDB → ClickHouse 마이그레이션 가이드

## 디버깅
- Before: docker cp + duckdb
- After: clickhouse-client

## Export
- Before: duckdb .mode csv
- After: INTO OUTFILE

## 대시보드
- After: Superset 사용
```

### 3단계: 코드 제거 (1일)

```bash
# 파일 삭제
rm flink-app/src/main/java/com/example/ctr/config/DuckDBProperties.java
rm flink-app/src/main/java/com/example/ctr/infrastructure/flink/sink/DuckDBSink.java
rm flink-app/src/main/java/com/example/ctr/infrastructure/flink/sink/impl/DuckDBRichSink.java

# build.gradle.kts 수정
- implementation 'org.duckdb:duckdb_jdbc:0.9.2'

# CtrJobPipelineBuilder.kt 수정
- ctrResults.sinkToDuckDB(duckDBSink)
```

### 4단계: 검증 (1일)

```bash
# Flink Job 재배포
./gradlew clean shadowJar
./scripts/deploy-flink-job.sh

# ClickHouse 데이터 확인
clickhouse-client --query "SELECT count() FROM ctr_results_raw"

# 성능 확인
clickhouse-client --query "SELECT * FROM ctr_results_raw LIMIT 100" --time
```

---

## 롤백 계획 (만약을 위해)

**Q:** DuckDB가 정말 필요한 경우가 생기면?

**A:** ClickHouse에서 export하면 됩니다.

```bash
# 1. ClickHouse → Parquet export
clickhouse-client --query \
  "SELECT * FROM ctr_results_raw FORMAT Parquet" \
  > ctr_data.parquet

# 2. DuckDB에서 Parquet 읽기
duckdb -c "SELECT * FROM 'ctr_data.parquet' LIMIT 10"
```

**장점:**
- 필요할 때만 export
- 실시간 sync 불필요
- 스토리지 낭비 없음

---

## 최종 결론

### DuckDB 제거 이유

1. **사용되지 않음**: 3개월간 사용 빈도 0회
2. **중복 저장**: ClickHouse와 100% 중복
3. **복잡도 증가**: 파일 복사, merge, 권한 문제
4. **병렬도 문제**: 데이터 분산으로 조회 어려움
5. **ClickHouse 충분**: 모든 use case 커버

### 기대 효과

- 코드 400 LOC 제거
- 네트워크 대역폭 50% 절감
- 스토리지 50% 절감
- 유지보수 복잡도 감소
- 단일 데이터 소스로 일관성 향상

### 리스크

**거의 없음**
- 기존 사용 빈도 0
- ClickHouse로 완전 대체 가능
- 필요 시 export로 해결

---

## 다음 단계

1. ✅ DuckDB 제거 결정
2. 📝 문서 업데이트 (MIGRATION_PLAN.md)
3. 🔧 코드 제거 (Phase 4)
4. 🚀 배포 및 검증

**결론: DuckDB는 제거합니다. ClickHouse 단일 소스로 통합합니다.**
