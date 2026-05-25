# Docs Index

이 디렉터리는 개인 프로젝트를 학습하며 남긴 운영 문서, 설계 결정, 실험 기록, 로드맵을 분류해 둔 공간이다. 현재 구현을 확인할 때는 코드와 루트 `README.md`를 먼저 본다.

## 읽는 순서

1. 프로젝트 현재 상태: `../README.md`
2. 로컬 실행과 장애 대응: `operations/RUNBOOK.md`
3. 현재 저장소/분석 설계: `architecture/CLICKHOUSE_MATERIALIZED_VIEW.md`
4. Flink 성능 학습: `flink/operator-chaining/OPERATOR_CHAINING.md`부터 시작
5. 과거 결정 배경: `architecture/WHY_REMOVE_DUCKDB.md`, `migrations/KAFKA_KRAFT_MIGRATION.md`, `migrations/KOTLIN_MIGRATION_GUIDE.md`
6. 향후 작업과 포트폴리오 정리: `roadmaps/`, `portfolio/`

## 분류

| 분류 | 디렉터리/문서 | 상태 | 용도 |
| --- | --- | --- | --- |
| 운영 | `operations/RUNBOOK.md` | 현재 | 로컬/운영 중 알람과 장애 대응 절차 |
| 현재 설계 | `architecture/CLICKHOUSE_MATERIALIZED_VIEW.md` | 현재 | ClickHouse base table, materialized view 설계 |
| 설계 결정 | `architecture/WHY_REMOVE_DUCKDB.md` | 결정 기록 | DuckDB 제거와 ClickHouse 단일화 근거 |
| 마이그레이션 | `migrations/KAFKA_KRAFT_MIGRATION.md` | 참고 | ZooKeeper 기반 Kafka에서 KRaft로 전환한 기록 |
| 마이그레이션 | `migrations/KOTLIN_MIGRATION_GUIDE.md` | 완료 기록 | Java to Kotlin 전환 과정과 패턴 |
| 마이그레이션 | `migrations/MIGRATION_PLAN.md` | 일부 대체됨 | Kotlin/ClickHouse 전환 계획. 현재 코드와 다를 수 있음 |
| Flink 성능 | `flink/operator-chaining/OPERATOR_CHAINING.md` | 학습 요약 | Operator chaining 적용 방법을 빠르게 보는 가이드 |
| Flink 성능 | `flink/operator-chaining/OPERATOR_CHAINING_REFERENCE.md` | 레퍼런스 | Flink chaining 개념/API/트러블슈팅 정리 |
| Flink 성능 | `flink/operator-chaining/OPERATOR_CHAINING_DEEP_DIVE.md` | 학습 노트 | 내부 동작과 성능 영향 심층 분석 |
| Flink 성능 | `flink/operator-chaining/OPERATOR_CHAINING_REPORT.md` | 실험 보고서 | 프로젝트 적용 결과와 성능 개선 주장 정리 |
| Flink 성능 | `flink/operator-chaining/WHY_CHAINING_MATTERS.md` | 학습 노트 | 데이터 엔지니어 관점의 중요성 설명 |
| Flink 성능 | `flink/operator-chaining/CHAINING_ALTERNATIVES_AND_FUTURE.md` | 탐색 노트 | Spark/Ray/GPU/serverless 등 대안 비교 |
| 로드맵 | `roadmaps/PRODUCTION_READINESS_ROADMAP.md` | 계획 | 운영 준비를 위한 테스트/설정/관측성 개선 계획 |
| 로드맵 | `roadmaps/PRODUCTION_COMPLETION_ROADMAP.md` | 계획 | 배포, 보안, Kubernetes, 모니터링까지 확장한 계획 |
| 포트폴리오 | `portfolio/PORTFOLIO_ROADMAP.md` | 초안/백로그 | 포트폴리오 구성, ADR, 데모, 인터뷰 답변 후보 |
| 이미지 | `images/performance.png` | 자산 | 성능 문서에서 사용할 이미지 |

## 현재성 기준

- **현재 구현 확인**: 코드, `../README.md`, `operations/RUNBOOK.md`, `architecture/CLICKHOUSE_MATERIALIZED_VIEW.md`를 우선한다.
- **왜 그렇게 했는지 설명**: `architecture/WHY_REMOVE_DUCKDB.md`, `migrations/KAFKA_KRAFT_MIGRATION.md`, `migrations/KOTLIN_MIGRATION_GUIDE.md`, operator chaining 문서를 근거로 삼는다.
- **면접/포트폴리오 재료**: roadmap/portfolio 문서는 그대로 실행 문서로 보지 말고, 현재 코드와 맞는지 확인한 뒤 사용한다.
- **주의 대상**: `migrations/MIGRATION_PLAN.md`, production roadmap, portfolio roadmap은 계획 문서라서 실제 구현과 어긋난 내용이 있을 수 있다.

## 디렉터리 구조

```text
docs/
  README.md
  operations/
    RUNBOOK.md
  architecture/
    CLICKHOUSE_MATERIALIZED_VIEW.md
    WHY_REMOVE_DUCKDB.md
  migrations/
    KAFKA_KRAFT_MIGRATION.md
    KOTLIN_MIGRATION_GUIDE.md
    MIGRATION_PLAN.md
  flink/
    operator-chaining/
      OPERATOR_CHAINING.md
      OPERATOR_CHAINING_REFERENCE.md
      OPERATOR_CHAINING_DEEP_DIVE.md
      OPERATOR_CHAINING_REPORT.md
      WHY_CHAINING_MATTERS.md
      CHAINING_ALTERNATIVES_AND_FUTURE.md
  roadmaps/
    PRODUCTION_READINESS_ROADMAP.md
    PRODUCTION_COMPLETION_ROADMAP.md
  portfolio/
    PORTFOLIO_ROADMAP.md
  images/
    performance.png
```

블로그 초안은 제거했다. 다음 정리 대상은 operator chaining 문서 병합과 portfolio roadmap 분리다.
