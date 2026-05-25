# CTR Pipeline Runbook

## FlinkJobNotRunning

### 증상
- Prometheus alert: `FlinkJobNotRunning`

### 대응
1. Flink UI에서 JobManager/TaskManager 상태 확인
   - `http://localhost:8081`
2. 컨테이너 상태 확인
   - `docker compose ps`
   - `docker logs flink-jobmanager --tail 200`
3. 재시작 필요 시
   - `./scripts/deploy-flink-job.sh`
4. 지속 시 확인
   - 포트 충돌, JVM OOM, 네트워크 바인딩 충돌

---

## LowCTRIngestionRate

### 증상
- 이벤트 입력량이 지속적으로 낮음

### 대응
1. 프로듀서 상태 확인
   - `./scripts/start-producers.sh`
   - `ps aux | grep producer`
2. Kafka 토픽 확인
   - Kafka UI: `http://localhost:8080`
   - CLI: `docker exec kafka2 kafka-topics --bootstrap-server kafka2:29093 --describe --topic impressions`
3. 최근 이벤트 생성 코드 변경 사항 점검

---

## HighTaskException

### 증상
- 작업에서 예외 발생률이 급증

### 대응
1. DLQ 토픽 확인
   - `docker exec kafka2 kafka-console-consumer --bootstrap-server kafka2:29093 --topic ${DLQ_TOPIC:-dlq-events} --from-beginning`
2. CTR 파이프라인 예외 로그 확인
   - `docker logs flink-taskmanager --tail 200`
3. 파싱 실패가 많은지 확인 (timestamp/event_type, invalid schema)
4. 필요 시 일시 중단 후 재실행
   - `./scripts/stop-flink-job.sh`
   - `./scripts/deploy-flink-job.sh`
