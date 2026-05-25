# Kafka Zookeeper → KRaft 전환 가이드

## 개요

이 문서는 현재 Zookeeper 기반 Kafka 클러스터를 KRaft (Kafka Raft) 모드로 전환하는 단계별 가이드입니다.

### 현재 환경
- **Kafka 버전**: Confluent Platform 7.4.0 (Apache Kafka 3.4.x 기반)
- **Zookeeper 버전**: 7.4.0
- **브로커 수**: 3개 (kafka1, kafka2, kafka3)
- **전환 가능 여부**: ✅ 가능 (Kafka 3.3.0+부터 KRaft 프로덕션 준비 완료)

### KRaft의 장점
- Zookeeper 의존성 제거로 아키텍처 단순화
- 메타데이터 관리 성능 향상
- 클러스터 확장성 개선 (수백만 파티션 지원)
- 운영 및 모니터링 복잡도 감소
- 빠른 컨트롤러 장애 조치

---

## 전환 전 체크리스트

### 1. 현재 상태 백업
```bash
# 토픽 목록 저장
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --list > backup/topics_list.txt

# 토픽 설정 백업
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --describe > backup/topics_config.txt

# 컨슈머 그룹 정보 백업
docker compose exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 --list > backup/consumer_groups.txt
```

### 2. 데이터 백업 (선택사항)
개발 환경이므로 필수는 아니지만, 중요한 데이터가 있다면:
```bash
# 토픽 데이터 백업 (예: kcat 사용)
kcat -b localhost:9092 -t impressions -C -e -o beginning > backup/impressions.json
kcat -b localhost:9092 -t clicks -C -e -o beginning > backup/clicks.json
```

### 3. 애플리케이션 중단
```bash
./scripts/stop-producers.sh
./scripts/stop-flink-job.sh
```

---

## 전환 단계

### Phase 1: 준비 작업

#### 1.1 백업 디렉토리 생성
```bash
mkdir -p backup
date >> backup/migration_timestamp.txt
```

#### 1.2 현재 Kafka 클러스터 중단
```bash
docker compose stop kafka1 kafka2 kafka3 kafka-ui
```

#### 1.3 기존 볼륨 제거 (개발 환경)
```bash
# 주의: 모든 Kafka 데이터가 삭제됩니다
docker compose down -v
# 또는 개별 볼륨만 제거
docker volume rm $(docker volume ls -q | grep kafka)
```

---

### Phase 2: KRaft 모드 설정

#### 2.1 `docker-compose.yml` 수정

기존 Zookeeper 기반 Kafka 설정을 remove한 KRaft 전용 설정으로 교체합니다:

```yaml
services:
  kafka1:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:9093,2@kafka2:9094,3@kafka3:9095'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka1:29092,PLAINTEXT_HOST://localhost:9092'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      KAFKA_CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
    volumes:
      - kafka1-data:/var/lib/kafka/data
    mem_limit: 512m
    cpus: 0.5

  kafka2:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka2
    ports:
      - "9093:9093"
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:9093,2@kafka2:9094,3@kafka3:9095'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:29093,PLAINTEXT_HOST://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka2:29093,PLAINTEXT_HOST://localhost:9093'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      KAFKA_CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
    volumes:
      - kafka2-data:/var/lib/kafka/data
    mem_limit: 512m
    cpus: 0.5

  kafka3:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka3
    ports:
      - "9094:9094"
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:9093,2@kafka2:9094,3@kafka3:9095'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:29094,PLAINTEXT_HOST://0.0.0.0:9094,CONTROLLER://0.0.0.0:9095'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka3:29094,PLAINTEXT_HOST://localhost:9094'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      KAFKA_CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
    volumes:
      - kafka3-data:/var/lib/kafka/data
    mem_limit: 512m
    cpus: 0.5

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    depends_on:
      - kafka1
      - kafka2
      - kafka3
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka1:29092,kafka2:29093,kafka3:29094
    mem_limit: 256m
    cpus: 0.25

volumes:
  kafka1-data:
  kafka2-data:
  kafka3-data:
  clickhouse-data:
  superset-home:
```

#### 2.2 Cluster ID 생성 (선택사항)

위 예시에서는 `MkU3OEVBNTcwNTJENDM2Qk`를 사용했지만, 새로운 ID를 생성하려면:

```bash
# Kafka 컨테이너 임시 실행
docker run --rm confluentinc/cp-kafka:7.4.0 kafka-storage random-uuid

# 출력 예시: MkU3OEVBNTcwNTJENDM2Qk
# 이 값을 모든 브로커의 CLUSTER_ID에 동일하게 설정
```

---

### Phase 3: KRaft 클러스터 시작

#### 3.1 Docker Compose 재시작
```bash
docker compose up -d kafka1 kafka2 kafka3
```

#### 3.2 클러스터 헬스체크
```bash
# 브로커 상태 확인
docker compose logs kafka1 | grep -i "started"
docker compose logs kafka2 | grep -i "started"
docker compose logs kafka3 | grep -i "started"

# 클러스터 메타데이터 확인
docker compose exec kafka1 kafka-metadata --bootstrap-server kafka1:29092 describe --entity-type brokers
```

#### 3.3 Kafka UI 시작
```bash
docker compose up -d kafka-ui
```

브라우저에서 http://localhost:8080 접속하여 3개 브로커가 모두 표시되는지 확인

---

### Phase 4: 토픽 및 데이터 복원

#### 4.1 토픽 재생성
```bash
./scripts/create-topics.sh
```

또는 수동으로:
```bash
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 \
  --create --topic impressions --partitions 3 --replication-factor 3

docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 \
  --create --topic clicks --partitions 3 --replication-factor 3
```

#### 4.2 토픽 확인
```bash
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --list
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --describe
```

#### 4.3 데이터 복원 (백업했을 경우)
```bash
kcat -b localhost:9092 -t impressions -P < backup/impressions.json
kcat -b localhost:9092 -t clicks -P < backup/clicks.json
```

---

### Phase 5: 애플리케이션 재시작

#### 5.1 Flink 인프라 시작
```bash
docker compose up -d flink-jobmanager flink-taskmanager
```

#### 5.2 Flink Job 배포
```bash
./scripts/deploy-flink-job.sh
```

#### 5.3 Producer 시작
```bash
./scripts/start-producers.sh
```

#### 5.4 통합 테스트
```bash
# Kafka 메시지 확인
docker compose exec kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:29092 \
  --topic impressions \
  --from-beginning \
  --max-messages 10

# Redis 데이터 확인
docker compose exec redis redis-cli HGETALL ctr:latest

# ClickHouse 데이터 확인
docker compose exec clickhouse clickhouse-client \
  --query "SELECT * FROM ctr_results ORDER BY window_end DESC LIMIT 10"
```

---

## 검증 체크리스트

전환 완료 후 다음 항목들을 확인하세요:

- [ ] 3개 브로커 모두 정상 동작 (`docker compose ps`)
- [ ] Kafka UI에서 클러스터 상태 정상
- [ ] 토픽 생성 및 파티션 분산 확인
- [ ] Producer가 메시지 정상 발행
- [ ] Flink Job이 메시지 소비 및 처리
- [ ] Redis에 CTR 결과 업데이트 확인
- [ ] ClickHouse에 데이터 적재 확인

---

---

## 주의사항

### 1. Cluster ID 일관성
- 모든 브로커에 **동일한** `CLUSTER_ID` 사용 필수
- ID 불일치 시 브로커가 클러스터에 참여하지 못함

### 2. Controller Quorum 설정
- `KAFKA_CONTROLLER_QUORUM_VOTERS`에 모든 컨트롤러 노드 명시
- 형식: `{id}@{hostname}:{port}`
- 과반수(Quorum)가 유지되어야 클러스터 정상 동작

### 3. 포트 충돌 방지
- Controller 전용 포트 (9093, 9094, 9095) 추가 필요
- 기존 PLAINTEXT 포트 (9092, 9093, 9094)와 혼동 주의

### 4. 로그 디렉토리
- `KAFKA_LOG_DIRS` 명시적 설정 권장
- 볼륨 마운트로 데이터 영속성 보장

### 5. 개발 vs 프로덕션
- 현재는 개발 환경이므로 Combined 모드 (broker+controller) 사용
- 프로덕션에서는 Controller 전용 노드 분리 권장

---

## 참고 자료

- [KIP-500: Replace ZooKeeper with a Self-Managed Metadata Quorum](https://cwiki.apache.org/confluence/display/KAFKA/KIP-500%3A+Replace+ZooKeeper+with+a+Self-Managed+Metadata+Quorum)
- [Confluent KRaft Overview](https://docs.confluent.io/platform/current/kafka-metadata/kraft.html)
- [Apache Kafka KRaft Documentation](https://kafka.apache.org/documentation/#kraft)

---

## 트러블슈팅

### 브로커가 시작되지 않음
```bash
# 로그 확인
docker compose logs kafka1 --tail 100

# 일반적인 원인:
# - Cluster ID 불일치
# - Controller Quorum Voters 설정 오류
# - 포트 충돌
```

### 메타데이터 손상
```bash
# 볼륨 완전 삭제 후 재시작
docker compose down -v
docker compose up -d kafka1 kafka2 kafka3
```

### 성능 이슈
```bash
# 컨트롤러 메트릭 확인
docker compose exec kafka1 kafka-metadata --bootstrap-server kafka1:29092 \
  describe --entity-type broker-loggers
```

---

## 다음 단계

전환 완료 후 고려사항:

1. **모니터링 강화**: KRaft 특화 메트릭 수집 (controller lag, metadata log size)
2. **문서 업데이트**: `CLAUDE.md` 및 `README.md`에 KRaft 전환 사실 반영
3. **CI/CD 업데이트**: 테스트 환경에서도 KRaft 사용하도록 수정
4. **성능 벤치마킹**: 기존 Zookeeper 대비 레이턴시 및 처리량 비교

---

**작성일**: 2025-12-07
**대상 Kafka 버전**: Confluent Platform 7.4.0 (Apache Kafka 3.4.x)
