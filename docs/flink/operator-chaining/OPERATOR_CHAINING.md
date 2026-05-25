# Flink Operator Chaining 최적화 가이드

## 📌 개요

이 문서는 CTR 데이터 파이프라인에 적용된 **Operator Chaining** 최적화 기법을 설명합니다.

## 🎯 Operator Chaining이란?

Operator Chaining은 Flink가 여러 연산자(Operator)를 하나의 태스크(Task)로 묶어서 실행하는 최적화 기법입니다.

### 장점

1. **네트워크 오버헤드 감소**: 연산자 간 데이터 전송이 메모리 내에서 이루어짐
2. **직렬화/역직렬화 비용 절감**: 체인된 연산자 간에는 직렬화가 불필요
3. **스레드 컨텍스트 스위칭 감소**: 하나의 스레드에서 여러 연산 수행
4. **리소스 효율성 향상**: 더 적은 태스크로 동일한 작업 수행

### 체이닝 조건

다음 조건을 모두 만족하면 연산자들이 체이닝됩니다:

- 같은 병렬도(Parallelism)를 가짐
- 같은 슬롯 공유 그룹(Slot Sharing Group)에 속함
- 체이닝이 비활성화되지 않음
- 연산자가 체이닝 가능한 타입임 (대부분의 연산자가 해당)

## 🔧 적용된 최적화 전략

### 1. Slot Sharing Groups

```java
// 소스 연산자들을 같은 그룹으로 묶음
.slotSharingGroup("source-group")

// 처리 연산자들을 같은 그룹으로 묶음
.slotSharingGroup("processing-group")

// 싱크 연산자들을 같은 그룹으로 묶음
.slotSharingGroup("sink-group")
```

**효과**: 관련된 연산자들이 같은 슬롯에서 실행되어 리소스 활용도 향상

### 2. 명시적 UID 설정

```java
.uid("impression-source")
.uid("filter-null-impressions")
.uid("validate-impressions")
```

**효과**: 
- Savepoint/Checkpoint 호환성 보장
- 작업 재시작 시 상태 복구 가능
- 운영 환경에서 안전한 업그레이드 지원

### 3. 체이닝 제어

```java
// 싱크는 독립적으로 실행 (체이닝 비활성화)
.disableChaining()
```

**효과**: 싱크 연산자가 독립적으로 실행되어 백프레셔 관리 개선

## 📊 파이프라인 구조

### Before (체이닝 없음)
```
[Source] → [Filter] → [Filter] → [Union] → [Filter] → [KeyBy] → [Window] → [Aggregate] → [Sink1]
                                                                                        → [Sink2]
                                                                                        → [Sink3]
```
각 연산자가 별도의 태스크로 실행 = 높은 오버헤드

### After (체이닝 적용)
```
[Source→Filter→Filter] → [Union→Filter→KeyBy→Window→Aggregate] → [Sink1]
                                                                  → [Sink2]
                                                                  → [Sink3]
```
관련 연산자들이 하나의 태스크로 묶임 = 낮은 오버헤드

## 🎨 체이닝 그룹 구성

### Source Group
- Impression Kafka Source
- Filter Null Impressions
- Validate Impressions
- Click Kafka Source
- Filter Null Clicks
- Validate Clicks

**특징**: 소스와 초기 필터링을 하나의 체인으로 구성

### Processing Group
- Union Streams
- Filter by ProductId
- KeyBy
- Window
- CTR Aggregation

**특징**: 핵심 비즈니스 로직을 하나의 체인으로 구성

### Sink Group
- Redis Sink (독립)
- DuckDB Sink (독립, 병렬도=1)
- ClickHouse Sink (독립)

**특징**: 각 싱크는 `disableChaining()`으로 독립 실행

## 📈 성능 개선 효과

### 예상 개선 사항

1. **네트워크 트래픽**: 30-50% 감소
2. **CPU 사용률**: 10-20% 감소
3. **처리 지연시간**: 20-30% 감소
4. **메모리 효율성**: 향상

### 모니터링 지표

Flink Web UI에서 확인 가능:
- Task 수 감소 확인
- 네트워크 I/O 감소 확인
- CPU 사용률 감소 확인

## 🔍 Flink Web UI에서 확인하기

1. **http://localhost:8081** 접속
2. **Running Jobs** → 작업 선택
3. **Task Managers** 탭에서 슬롯 사용 현황 확인
4. **Job Graph**에서 체이닝된 연산자 확인 (박스로 묶여 표시됨)

## ⚙️ 추가 최적화 옵션

### 환경 레벨 설정

```java
// 전역적으로 체이닝 비활성화 (필요시)
env.disableOperatorChaining();

// 전역적으로 체이닝 활성화 (기본값)
env.enableOperatorChaining();
```

### 연산자 레벨 설정

```java
// 새로운 체인 시작
.startNewChain()

// 체이닝 비활성화
.disableChaining()
```

## 📝 베스트 프랙티스

1. **UID 항상 설정**: 프로덕션 환경에서는 모든 연산자에 UID 설정 필수
2. **슬롯 그룹 논리적 분리**: 소스/처리/싱크를 별도 그룹으로 관리
3. **싱크는 독립 실행**: 백프레셔 관리를 위해 싱크는 체이닝 비활성화
4. **병렬도 일관성**: 체이닝하려는 연산자들은 같은 병렬도 유지
5. **모니터링**: Flink Web UI로 체이닝 효과 지속적 확인

## 🚨 주의사항

1. **과도한 체이닝**: 너무 많은 연산자를 체이닝하면 단일 태스크 부하 증가
2. **디버깅 어려움**: 체이닝된 연산자는 개별 메트릭 확인이 어려울 수 있음
3. **백프레셔**: 싱크는 체이닝하지 않는 것이 백프레셔 관리에 유리

## 📚 참고 자료

- [Flink Operator Chaining 공식 문서](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/overview/#task-chaining-and-resource-groups)
- [Flink Performance Tuning](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/production_ready/)
