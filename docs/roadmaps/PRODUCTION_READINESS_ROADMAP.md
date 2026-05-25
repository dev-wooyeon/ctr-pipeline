# Production Readiness Roadmap

**프로젝트**: Real-time CTR Pipeline
**목표**: Toy Project → Production-Grade Data Pipeline
**작성일**: 2025-12-07
**대상**: 5년차 백엔드 개발자 → 데이터 엔지니어 전환

---

## 개요

이 문서는 현재 프로젝트를 프로덕션 환경에 배포 가능한 수준으로 만들기 위한 단계별 로드맵입니다.

### 현재 상태
- **아키텍처**: 8/10 (우수)
- **코드 품질**: 7/10 (양호)
- **테스트**: 4/10 (심각하게 부족)
- **운영 성숙도**: 5/10 (미흡)
- **프로덕션 준비도**: **40%**

### 목표 상태
- **테스트 커버리지**: 80%+
- **보안**: 인증/암호화 완료
- **모니터링**: 실시간 메트릭 + 알림
- **CI/CD**: 자동화된 배포 파이프라인
- **프로덕션 준비도**: **90%+**

---

## 우선순위 레벨 정의

| 레벨 | 설명 | 기준 |
|------|------|------|
| **P0 - Critical** | 즉시 수정 필요, 프로덕션 배포 차단 요소 | 데이터 손실/보안 위험 |
| **P1 - High** | 프로덕션 배포 전 필수 | 운영 불가능 |
| **P2 - Medium** | 프로덕션 품질 향상 | 운영 효율성 |
| **P3 - Low** | Nice to have | 최적화/편의성 |

---

## Phase 1: Critical Fixes (1-2주)

**목표**: 심각한 버그 수정 및 기본 테스트 인프라 구축
**완료 조건**: 모든 P0 이슈 해결, 테스트 실행 가능

### P0-1: CTRResultWindowProcessFunction 버그 수정

**파일**: `flink-app/src/main/kotlin/com/example/ctr/domain/service/CTRResultWindowProcessFunction.kt:18`

**현재 문제**:
```kotlin
val counts = elements.iterator().next()  // 💣 첫 번째 요소만 가져옴
```

**수정 방안**:
```kotlin
val counts = elements.singleOrNull()
    ?: throw IllegalStateException(
        "Expected single aggregated result per window for product $key, " +
        "but got ${elements.count()} elements"
    )
```

**검증**:
```kotlin
@Test
fun `should throw exception when multiple elements in window`() {
    val processFunction = CTRResultWindowProcessFunction()
    val elements = listOf(
        EventCount(100, 50),
        EventCount(200, 100)  // 두 개!
    )

    assertThatThrownBy {
        processFunction.process("product-1", ..., elements, ...)
    }.isInstanceOf(IllegalStateException::class.java)
}
```

**예상 시간**: 2시간
**의존성**: 없음
**중요도**: P0 - 데이터 손실 가능성

---

### P0-2: Event.eventTimeMillisUtc() Silent Failure 수정

**파일**: `flink-app/src/main/kotlin/com/example/ctr/domain/model/Event.kt`

**현재 문제**:
```kotlin
fun eventTimeMillisUtc(): Long = timestamp ?: 0L  // epoch 0 반환
```

**수정 방안**:
```kotlin
fun eventTimeMillisUtc(): Long =
    timestamp ?: throw IllegalStateException(
        "Event timestamp is null for event: userId=$userId, productId=$productId, eventType=$eventType"
    )
```

**대안 (nullable 반환)**:
```kotlin
fun eventTimeMillisUtcOrNull(): Long? = timestamp

// 사용처에서:
.filter { it.eventTimeMillisUtcOrNull() != null }
.assignTimestampsAndWatermarks(...)
```

**검증**:
```kotlin
@Test
fun `should throw exception for null timestamp`() {
    val event = Event(
        userId = "user1",
        productId = "prod1",
        eventType = "click",
        timestamp = null
    )

    assertThatThrownBy { event.eventTimeMillisUtc() }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("timestamp is null")
}
```

**예상 시간**: 1시간
**의존성**: 없음
**중요도**: P0 - 데이터 품질 이슈

---

### P0-3: 테스트 인프라 복구

**현재 문제**:
```
> Task :test FAILED
> Could not start Gradle Test Executor 1
```

**원인**: JUnit 버전 미명시

**수정 (build.gradle.kts)**:
```kotlin
dependencies {
    // 기존
    testImplementation("org.junit.jupiter:junit-jupiter")

    // 수정
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")

    // Flink test utilities
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.apache.flink:flink-runtime:$flinkVersion:tests")
    testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion:tests")
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    // Test configuration
    maxHeapSize = "1G"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
```

**검증**:
```bash
cd flink-app
./gradlew clean test

# 모든 테스트 통과 확인
> Task :test
EventCountTest > testInitialState() PASSED
EventCountTest > testIncrements() PASSED
...
BUILD SUCCESSFUL
```

**예상 시간**: 2시간
**의존성**: 없음
**중요도**: P0 - 테스트 불가능

---

### P0-4: Gradle 버전 고정

**현재 문제**: Gradle 9.x 사용 중, Flink는 Gradle 8.x 권장

**수정 (gradle/wrapper/gradle-wrapper.properties)**:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**검증**:
```bash
./gradlew --version

------------------------------------------------------------
Gradle 8.5
------------------------------------------------------------
```

**예상 시간**: 30분
**의존성**: 없음
**중요도**: P0 - 빌드 안정성

---

### Phase 1 체크리스트

- [ ] P0-1: CTRResultWindowProcessFunction 버그 수정 및 테스트 추가
- [ ] P0-2: Event.eventTimeMillisUtc() null 처리 개선
- [ ] P0-3: 테스트 인프라 복구
- [ ] P0-4: Gradle 버전 고정
- [ ] 모든 기존 테스트 통과 확인
- [ ] 코드 리뷰 완료

**Phase 1 완료 시 달성**:
- ✅ Critical 버그 0개
- ✅ 테스트 실행 가능
- ✅ 빌드 안정성 확보

---

## Phase 2: Foundation (2-4주)

**목표**: 테스트 커버리지 80%+, 기본 보안, CI/CD 구축
**완료 조건**: 모든 P1 이슈 해결, 자동화된 테스트/배포

### P1-1: 핵심 컴포넌트 단위 테스트 추가

#### Task 1.1: CtrJobPipelineBuilder 테스트

**파일**: `flink-app/src/test/kotlin/com/example/ctr/infrastructure/flink/CtrJobPipelineBuilderTest.kt` (생성)

**테스트 범위**:
```kotlin
class CtrJobPipelineBuilderTest {

    @Test
    fun `should create Kafka source with correct configuration`() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val builder = CtrJobPipelineBuilder(...)

        // Verify source configuration
        val transformation = builder.build(env).transformations[0]
        assertThat(transformation.name).isEqualTo("Kafka Source: impressions")
    }

    @Test
    fun `should apply watermark strategy with correct parameters`() {
        // Test watermark configuration
    }

    @Test
    fun `should create window with correct size`() {
        // Test window configuration
    }

    @Test
    fun `should assign UIDs for savepoint compatibility`() {
        // Test UID assignment
    }

    @Test
    fun `should create slot sharing groups correctly`() {
        // Test resource management
    }
}
```

**예상 시간**: 8시간
**중요도**: P1

---

#### Task 1.2: FlinkEnvironmentFactory 테스트

**테스트 범위**:
```kotlin
class FlinkEnvironmentFactoryTest {

    @Test
    fun `should configure checkpoint with correct settings`() {
        val properties = CtrJobProperties(
            name = "test-job",
            checkpointIntervalMs = 60000,
            checkpointTimeoutMs = 600000,
            minPauseBetweenCheckpointsMs = 500,
            parallelism = 2,
            restartAttemptsCount = 3,
            restartDelayMs = 10000
        )

        val factory = FlinkEnvironmentFactory(properties)
        val env = factory.create()

        val checkpointConfig = env.checkpointConfig
        assertThat(checkpointConfig.checkpointInterval).isEqualTo(60000)
        assertThat(checkpointConfig.checkpointingMode)
            .isEqualTo(CheckpointingMode.EXACTLY_ONCE)
    }

    @Test
    fun `should configure restart strategy`() {
        // Test restart configuration
    }
}
```

**예상 시간**: 4시간
**중요도**: P1

---

#### Task 1.3: KafkaSourceFactory 테스트

**테스트 범위**:
```kotlin
class KafkaSourceFactoryTest {

    @Test
    fun `should create source with correct bootstrap servers`() {
        val kafkaProps = KafkaProperties(
            bootstrapServers = "localhost:9092",
            groupId = "test-group"
        )

        val factory = KafkaSourceFactory(kafkaProps)
        val source = factory.createSource("test-topic", "group-1")

        // Verify source configuration
        // Note: Flink source는 직접 검증 어려움,
        // 대신 integration test에서 검증
    }
}
```

**예상 시간**: 2시간
**중요도**: P1

---

#### Task 1.4: ClickHouseSink 테스트

**테스트 범위**:
```kotlin
class ClickHouseSinkTest {

    private lateinit var testContainer: ClickHouseContainer

    @BeforeEach
    fun setup() {
        testContainer = ClickHouseContainer("clickhouse/clickhouse-server:23.8")
            .withInitScript("init-schema.sql")
        testContainer.start()
    }

    @Test
    fun `should insert CTR results to ClickHouse`() {
        val properties = ClickHouseProperties(
            url = testContainer.jdbcUrl,
            batchSize = 10,
            batchIntervalMs = 100,
            maxRetries = 3
        )

        val sink = ClickHouseSink(properties)
        val result = CTRResult(
            productId = "prod-1",
            impressions = 100,
            clicks = 50,
            ctr = 0.5,
            windowStart = Instant.now(),
            windowEnd = Instant.now().plusSeconds(10)
        )

        // Execute sink
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.fromElements(result)
            .addSink(sink.createSink())
        env.execute()

        // Verify data in ClickHouse
        val rs = testContainer.createConnection("").use { conn ->
            conn.createStatement().executeQuery(
                "SELECT * FROM ctr_results WHERE product_id = 'prod-1'"
            )
        }

        assertThat(rs.next()).isTrue()
        assertThat(rs.getDouble("ctr")).isEqualTo(0.5)
    }

    @AfterEach
    fun teardown() {
        testContainer.stop()
    }
}
```

**의존성 추가 (build.gradle.kts)**:
```kotlin
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("org.testcontainers:clickhouse:1.19.3")
testImplementation("org.testcontainers:junit-jupiter:1.19.3")
```

**예상 시간**: 6시간
**중요도**: P1

---

### P1-2: Integration Test 추가

**파일**: `flink-app/src/test/kotlin/com/example/ctr/integration/CtrPipelineIntegrationTest.kt` (생성)

```kotlin
class CtrPipelineIntegrationTest {

    companion object {
        @Container
        private val kafka = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
        ).withKraft()

        @Container
        private val clickhouse = ClickHouseContainer(
            "clickhouse/clickhouse-server:23.8"
        )
    }

    @Test
    fun `should process events end-to-end`() {
        // Given: Test events
        val impressions = listOf(
            """{"userId":"user1","productId":"prod1","eventType":"impression","timestamp":${now()}}""",
            """{"userId":"user2","productId":"prod1","eventType":"impression","timestamp":${now()}}"""
        )
        val clicks = listOf(
            """{"userId":"user1","productId":"prod1","eventType":"click","timestamp":${now()}}"""
        )

        // When: Produce to Kafka
        produceToKafka(kafka, "impressions", impressions)
        produceToKafka(kafka, "clicks", clicks)

        // Build and execute pipeline
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.setParallelism(1)
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING)

        val config = AppConfig(
            kafka = KafkaProperties(
                bootstrapServers = kafka.bootstrapServers,
                groupId = "test-group"
            ),
            clickhouse = ClickHouseProperties(
                url = clickhouse.jdbcUrl
            ),
            ctr = CtrProperties(
                job = CtrJobProperties(...)
            )
        )

        val pipelineBuilder = CtrJobPipelineBuilder(...)
        pipelineBuilder.build(env)

        // Execute with timeout
        val jobExecutionResult = env.executeAsync("test-job")

        // Wait for processing
        Thread.sleep(15000)  // Wait for window to close

        jobExecutionResult.cancel()

        // Then: Verify results in ClickHouse
        clickhouse.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                "SELECT * FROM ctr_results WHERE product_id = 'prod1'"
            )

            assertThat(rs.next()).isTrue()
            assertThat(rs.getLong("impressions")).isEqualTo(2)
            assertThat(rs.getLong("clicks")).isEqualTo(1)
            assertThat(rs.getDouble("ctr")).isEqualTo(0.5)
        }
    }

    private fun produceToKafka(kafka: KafkaContainer, topic: String, messages: List<String>) {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        }

        KafkaProducer<String, String>(props).use { producer ->
            messages.forEach { message ->
                producer.send(ProducerRecord(topic, message)).get()
            }
        }
    }
}
```

**예상 시간**: 12시간
**중요도**: P1

---

### P1-3: 보안 기본 설정

#### Task 3.1: Kafka SASL 인증 추가

**파일**: `flink-app/src/main/resources/application.yml`

```yaml
kafka:
  bootstrap-servers: "${KAFKA_BOOTSTRAP_SERVERS}"
  security:
    protocol: "SASL_SSL"
    sasl:
      mechanism: "PLAIN"
      jaas-config: |
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";
  ssl:
    truststore-location: "${KAFKA_TRUSTSTORE_LOCATION:/etc/kafka/truststore.jks}"
    truststore-password: "${KAFKA_TRUSTSTORE_PASSWORD}"
```

**코드 수정 (KafkaSourceFactory.kt)**:
```kotlin
fun createSource(topic: String, groupId: String): KafkaSource<Event> {
    val sourceBuilder = KafkaSource.builder<Event>()
        .setBootstrapServers(properties.bootstrapServers)
        .setTopics(topic)
        .setGroupId(groupId)
        .setDeserializer(EventDeserializationSchema())
        .setStartingOffsets(OffsetsInitializer.latest())

    // Add security configuration
    properties.security?.let { security ->
        val kafkaProps = Properties()
        kafkaProps["security.protocol"] = security.protocol

        security.sasl?.let { sasl ->
            kafkaProps["sasl.mechanism"] = sasl.mechanism
            kafkaProps["sasl.jaas.config"] = sasl.jaasConfig
        }

        security.ssl?.let { ssl ->
            kafkaProps["ssl.truststore.location"] = ssl.truststoreLocation
            kafkaProps["ssl.truststore.password"] = ssl.truststorePassword
        }

        sourceBuilder.setProperties(kafkaProps)
    }

    return sourceBuilder.build()
}
```

**새 Properties 클래스**:
```kotlin
data class KafkaSecurityProperties(
    val protocol: String,
    val sasl: SaslProperties?,
    val ssl: SslProperties?
)

data class SaslProperties(
    val mechanism: String,
    val jaasConfig: String
)

data class SslProperties(
    val truststoreLocation: String,
    val truststorePassword: String
)
```

**예상 시간**: 6시간
**중요도**: P1

---

#### Task 3.2: ClickHouse 인증 추가

**수정 (application.yml)**:
```yaml
clickhouse:
  url: "jdbc:clickhouse://${CLICKHOUSE_HOST:clickhouse}:${CLICKHOUSE_PORT:8123}/${CLICKHOUSE_DB:default}"
  username: "${CLICKHOUSE_USERNAME:default}"
  password: "${CLICKHOUSE_PASSWORD}"
  ssl:
    enabled: ${CLICKHOUSE_SSL_ENABLED:false}
```

**수정 (ClickHouseSink.kt)**:
```kotlin
fun createSink(): SinkFunction<CTRResult> {
    val connectionOptions = JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
        .withUrl(properties.url)
        .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
        .apply {
            properties.username?.let { withUsername(it) }
            properties.password?.let { withPassword(it) }
        }
        .build()

    // ... rest of the code
}
```

**예상 시간**: 2시간
**중요도**: P1

---

#### Task 3.3: Secrets Management

**옵션 1: Kubernetes Secrets (권장)**

```yaml
# k8s/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: flink-ctr-secrets
type: Opaque
stringData:
  kafka-username: "flink-user"
  kafka-password: "secure-password"
  clickhouse-password: "clickhouse-pass"
```

**Deployment에서 사용**:
```yaml
env:
  - name: KAFKA_USERNAME
    valueFrom:
      secretKeyRef:
        name: flink-ctr-secrets
        key: kafka-username
  - name: KAFKA_PASSWORD
    valueFrom:
      secretKeyRef:
        name: flink-ctr-secrets
        key: kafka-password
```

**옵션 2: HashiCorp Vault (고급)**

**예상 시간**: 4시간 (K8s) / 8시간 (Vault)
**중요도**: P1

---

### P1-4: CI/CD 파이프라인 구축

#### Task 4.1: GitHub Actions 워크플로우

**파일**: `.github/workflows/ci.yml` (생성)

```yaml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Grant execute permission for gradlew
        run: chmod +x flink-app/gradlew

      - name: Run tests
        run: |
          cd flink-app
          ./gradlew clean test --info

      - name: Generate test report
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Test Results
          path: flink-app/build/test-results/test/*.xml
          reporter: java-junit

      - name: Upload test coverage
        uses: codecov/codecov-action@v3
        with:
          files: flink-app/build/reports/jacoco/test/jacocoTestReport.xml

  build:
    needs: test
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Build JAR
        run: |
          cd flink-app
          ./gradlew clean shadowJar -x test

      - name: Upload JAR artifact
        uses: actions/upload-artifact@v4
        with:
          name: flink-app-jar
          path: flink-app/build/libs/ctr-calculator-*.jar

  docker:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'

    steps:
      - uses: actions/checkout@v4

      - name: Download JAR
        uses: actions/download-artifact@v4
        with:
          name: flink-app-jar
          path: flink-app/build/libs/

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ secrets.DOCKER_USERNAME }}/flink-ctr-app:latest
            ${{ secrets.DOCKER_USERNAME }}/flink-ctr-app:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

**예상 시간**: 4시간
**중요도**: P1

---

#### Task 4.2: JaCoCo 테스트 커버리지

**build.gradle.kts 추가**:
```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("jacoco")  // 추가
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/CtrApplication*",
                    "**/deserializer/**"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% 최소 커버리지
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

**예상 시간**: 2시간
**중요도**: P1

---

### P1-5: 설정 파라미터화

#### Task 5.1: 하드코딩된 값을 설정으로 이동

**수정 전 (CtrJobPipelineBuilder.kt)**:
```kotlin
.window(TumblingEventTimeWindows.of(Time.seconds(10)))  // 하드코딩
.assignTimestampsAndWatermarks(
    WatermarkStrategy
        .forBoundedOutOfOrderness<Event>(Duration.ofSeconds(5))  // 하드코딩
)
.allowedLateness(Time.seconds(5))  // 하드코딩
```

**새 Properties 클래스**:
```kotlin
data class CtrJobProperties(
    // 기존 속성들
    @field:NotBlank val name: String,
    @field:Positive val parallelism: Int,
    @field:Positive val checkpointIntervalMs: Long,
    // ... 기타

    // 새로 추가
    @field:Positive
    val windowSizeSeconds: Long = 10,

    @field:Positive
    val watermarkMaxOutOfOrderSeconds: Long = 5,

    @field:Positive
    val allowedLatenessSeconds: Long = 5,

    @field:Positive
    val idleSourceTimeoutSeconds: Long = 30
)
```

**수정 후 (CtrJobPipelineBuilder.kt)**:
```kotlin
private fun buildAggregation(
    stream: SingleOutputStreamOperator<Event>,
    aggregator: EventCountAggregator,
    windowFunction: CTRResultWindowProcessFunction,
    uidPrefix: String
): SingleOutputStreamOperator<CTRResult> {
    return stream
        .assignTimestampsAndWatermarks(
            WatermarkStrategy
                .forBoundedOutOfOrderness<Event>(
                    Duration.ofSeconds(properties.watermarkMaxOutOfOrderSeconds)
                )
                .withIdlenessTimeout(
                    Duration.ofSeconds(properties.idleSourceTimeoutSeconds)
                )
        )
        .uid("$uidPrefix-watermarks")
        .name("Assign Watermarks: $uidPrefix")
        .keyBy(Event::productId)
        .window(
            TumblingEventTimeWindows.of(
                Time.seconds(properties.windowSizeSeconds)
            )
        )
        .allowedLateness(
            Time.seconds(properties.allowedLatenessSeconds)
        )
        .aggregate(aggregator, windowFunction)
        .uid("$uidPrefix-aggregate")
        .name("Aggregate: $uidPrefix")
}
```

**application.yml 업데이트**:
```yaml
ctr:
  job:
    name: "ctr-calculator"
    parallelism: 2
    checkpoint-interval-ms: 60000
    # 새로 추가
    window-size-seconds: 10
    watermark-max-out-of-order-seconds: 5
    allowed-lateness-seconds: 5
    idle-source-timeout-seconds: 30
```

**예상 시간**: 3시간
**중요도**: P1

---

#### Task 5.2: Kafka Offset 초기화 전략 설정

**Properties 추가**:
```kotlin
data class KafkaProperties(
    @field:NotBlank val bootstrapServers: String,
    @field:NotBlank val groupId: String,
    val security: KafkaSecurityProperties? = null,

    // 새로 추가
    val offsetResetStrategy: OffsetResetStrategy = OffsetResetStrategy.LATEST
)

enum class OffsetResetStrategy {
    EARLIEST,
    LATEST,
    TIMESTAMP
}
```

**KafkaSourceFactory 수정**:
```kotlin
fun createSource(topic: String, groupId: String): KafkaSource<Event> {
    val offsetsInitializer = when (properties.offsetResetStrategy) {
        OffsetResetStrategy.EARLIEST -> OffsetsInitializer.earliest()
        OffsetResetStrategy.LATEST -> OffsetsInitializer.latest()
        OffsetResetStrategy.TIMESTAMP ->
            OffsetsInitializer.timestamp(properties.offsetTimestamp ?: 0L)
    }

    return KafkaSource.builder<Event>()
        .setBootstrapServers(properties.bootstrapServers)
        .setTopics(topic)
        .setGroupId(groupId)
        .setStartingOffsets(offsetsInitializer)
        .setDeserializer(EventDeserializationSchema())
        .build()
}
```

**예상 시간**: 2시간
**중요도**: P2

---

### P1-6: Configuration Validation 강제

**AppConfig.kt 수정**:
```kotlin
object AppConfig {
    private val logger = LoggerFactory.getLogger(AppConfig::class.java)
    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    fun load(): AppConfig {
        val yamlFile = findConfigFile()
        logger.info("Loading configuration from: ${yamlFile.absolutePath}")

        val config = try {
            mapper.readValue(yamlFile, AppConfig::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load configuration from $yamlFile", e)
        }

        // Validation 강제
        validateConfiguration(config)

        logger.info("Configuration loaded successfully")
        return config
    }

    private fun validateConfiguration(config: AppConfig) {
        val violations = validator.validate(config)

        if (violations.isNotEmpty()) {
            val errors = violations.joinToString("\n") { violation ->
                "${violation.propertyPath}: ${violation.message}"
            }
            throw IllegalStateException(
                "Configuration validation failed:\n$errors"
            )
        }

        // Custom validation
        require(config.ctr.job.parallelism > 0) {
            "Parallelism must be positive, got: ${config.ctr.job.parallelism}"
        }
        require(config.ctr.job.windowSizeSeconds > 0) {
            "Window size must be positive, got: ${config.ctr.job.windowSizeSeconds}"
        }
        // ... 추가 검증
    }
}
```

**테스트**:
```kotlin
class AppConfigTest {

    @Test
    fun `should throw exception for invalid parallelism`() {
        // Given: Invalid config file
        val invalidYaml = """
            ctr:
              job:
                parallelism: -1
        """.trimIndent()

        File("test-invalid.yml").writeText(invalidYaml)

        // When/Then
        assertThatThrownBy {
            AppConfig.load()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Parallelism must be positive")
    }
}
```

**예상 시간**: 4시간
**중요도**: P1

---

### Phase 2 체크리스트

#### 테스트
- [ ] CtrJobPipelineBuilder 단위 테스트 (5+ test cases)
- [ ] FlinkEnvironmentFactory 테스트 (3+ test cases)
- [ ] KafkaSourceFactory 테스트
- [ ] ClickHouseSink 테스트 (Testcontainers)
- [ ] Integration test (end-to-end)
- [ ] 테스트 커버리지 80% 달성
- [ ] JaCoCo 리포트 생성

#### 보안
- [ ] Kafka SASL 인증 구현
- [ ] ClickHouse 인증 구현
- [ ] Secrets management (K8s Secrets or Vault)
- [ ] SSL/TLS 설정 (선택사항)

#### CI/CD
- [ ] GitHub Actions 워크플로우 구성
- [ ] 자동화된 테스트 실행
- [ ] Docker 이미지 빌드 자동화
- [ ] Test coverage reporting

#### 설정
- [ ] 하드코딩 값 제거 (window, watermark, lateness)
- [ ] Kafka offset 전략 설정 가능
- [ ] ClickHouse batch 설정 가능
- [ ] Configuration validation 강제

**Phase 2 완료 시 달성**:
- ✅ 테스트 커버리지 80%+
- ✅ 기본 보안 구현
- ✅ CI/CD 파이프라인
- ✅ 설정 파라미터화

---

## Phase 3: Production Ready (1-2개월)

**목표**: 모니터링, 로깅, 운영 도구 구축
**완료 조건**: 프로덕션 배포 가능 수준

### P2-1: Custom Metrics 추가

#### Task 1.1: Flink Metrics 구현

**새 파일**: `flink-app/src/main/kotlin/com/example/ctr/infrastructure/flink/metrics/CtrMetrics.kt`

```kotlin
class CtrMetrics(runtimeContext: RuntimeContext) {
    private val metricGroup = runtimeContext.metricGroup

    // Counters
    val eventsProcessed: Counter = metricGroup.counter("events_processed")
    val eventsInvalid: Counter = metricGroup.counter("events_invalid")
    val clicksTotal: Counter = metricGroup.counter("clicks_total")
    val impressionsTotal: Counter = metricGroup.counter("impressions_total")

    // Gauges
    @Volatile private var currentCtr: Double = 0.0
    val ctrGauge: Gauge<Double> = metricGroup.gauge("current_ctr") { currentCtr }

    @Volatile private var activeProducts: Long = 0
    val activeProductsGauge: Gauge<Long> =
        metricGroup.gauge("active_products") { activeProducts }

    // Histograms
    val ctrDistribution: Histogram = metricGroup.histogram(
        "ctr_distribution",
        DescriptiveStatisticsHistogram(1000)
    )

    val windowProcessingTime: Histogram = metricGroup.histogram(
        "window_processing_time_ms",
        DescriptiveStatisticsHistogram(1000)
    )

    fun recordCtr(ctr: Double) {
        currentCtr = ctr
        ctrDistribution.update((ctr * 1000).toLong())  // Convert to basis points
    }

    fun recordWindowProcessingTime(durationMs: Long) {
        windowProcessingTime.update(durationMs)
    }

    fun updateActiveProducts(count: Long) {
        activeProducts = count
    }
}
```

**CTRResultWindowProcessFunction에 적용**:
```kotlin
class CTRResultWindowProcessFunction : ProcessWindowFunction<EventCount, CTRResult, String, TimeWindow>() {

    @Transient
    private lateinit var metrics: CtrMetrics

    override fun open(parameters: Configuration) {
        super.open(parameters)
        metrics = CtrMetrics(runtimeContext)
    }

    override fun process(
        key: String,
        context: Context,
        elements: Iterable<EventCount>,
        out: Collector<CTRResult>
    ) {
        val startTime = System.currentTimeMillis()

        val counts = elements.singleOrNull()
            ?: throw IllegalStateException("Expected single aggregated result")

        val result = CTRResult.from(
            productId = key,
            counts = counts,
            windowStart = Instant.ofEpochMilli(context.window().start),
            windowEnd = Instant.ofEpochMilli(context.window().end)
        )

        // Record metrics
        metrics.recordCtr(result.ctr)
        metrics.recordWindowProcessingTime(System.currentTimeMillis() - startTime)

        out.collect(result)
    }
}
```

**EventDeserializationSchema에 적용**:
```kotlin
class EventDeserializationSchema : DeserializationSchema<Event> {

    @Transient
    private lateinit var metrics: CtrMetrics

    override fun open(context: DeserializationSchema.InitializationContext) {
        super.open(context)
        metrics = CtrMetrics(context.metricGroup)
    }

    override fun deserialize(message: ByteArray): Event? {
        return try {
            val event = objectMapper.readValue(message, Event::class.java)

            if (event.isValid()) {
                metrics.eventsProcessed.inc()

                when (event.eventType) {
                    "click" -> metrics.clicksTotal.inc()
                    "impression", "view" -> metrics.impressionsTotal.inc()
                }

                event
            } else {
                metrics.eventsInvalid.inc()
                logger.warn("Dropping invalid event: $event")
                null
            }
        } catch (e: Exception) {
            metrics.eventsInvalid.inc()
            logger.warn("Failed to deserialize event", e)
            null
        }
    }
}
```

**예상 시간**: 8시간
**중요도**: P2

---

#### Task 1.2: Prometheus Metrics Reporter 설정

**flink-conf.yaml 추가** (docker-compose.yml 또는 K8s ConfigMap):
```yaml
metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
metrics.reporter.prom.port: 9249
```

**Prometheus scrape config**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'flink'
    static_configs:
      - targets: ['flink-jobmanager:9249', 'flink-taskmanager:9249']
    metrics_path: /metrics
```

**예상 시간**: 4시간
**중요도**: P2

---

### P2-2: Structured Logging

**새 파일**: `flink-app/src/main/kotlin/com/example/ctr/infrastructure/logging/StructuredLogger.kt`

```kotlin
object StructuredLogger {
    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    fun info(
        logger: Logger,
        message: String,
        vararg fields: Pair<String, Any?>
    ) {
        val logEntry = buildLogEntry("INFO", message, fields.toMap())
        logger.info(objectMapper.writeValueAsString(logEntry))
    }

    fun warn(
        logger: Logger,
        message: String,
        throwable: Throwable? = null,
        vararg fields: Pair<String, Any?>
    ) {
        val logEntry = buildLogEntry("WARN", message, fields.toMap(), throwable)
        logger.warn(objectMapper.writeValueAsString(logEntry))
    }

    fun error(
        logger: Logger,
        message: String,
        throwable: Throwable,
        vararg fields: Pair<String, Any?>
    ) {
        val logEntry = buildLogEntry("ERROR", message, fields.toMap(), throwable)
        logger.error(objectMapper.writeValueAsString(logEntry))
    }

    private fun buildLogEntry(
        level: String,
        message: String,
        fields: Map<String, Any?>,
        throwable: Throwable? = null
    ): Map<String, Any?> {
        return mutableMapOf(
            "timestamp" to Instant.now().toString(),
            "level" to level,
            "message" to message,
            "thread" to Thread.currentThread().name
        ).apply {
            putAll(fields)
            throwable?.let {
                put("exception", mapOf(
                    "class" to it.javaClass.name,
                    "message" to it.message,
                    "stackTrace" to it.stackTraceToString()
                ))
            }
        }
    }
}
```

**사용 예시**:
```kotlin
class CTRResultWindowProcessFunction : ProcessWindowFunction<...> {

    override fun process(...) {
        StructuredLogger.info(
            logger,
            "Processing CTR window",
            "productId" to key,
            "windowStart" to context.window().start,
            "windowEnd" to context.window().end,
            "impressions" to counts.impressions,
            "clicks" to counts.clicks,
            "ctr" to result.ctr
        )
    }
}
```

**logback.xml 설정**:
```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"application":"flink-ctr-calculator"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

**의존성 추가**:
```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
```

**예상 시간**: 6시간
**중요도**: P2

---

### P2-3: Alerting 구현

**새 파일**: `k8s/prometheus-rules.yaml`

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: flink-ctr-alerts
  labels:
    prometheus: kube-prometheus
spec:
  groups:
    - name: flink-ctr
      interval: 30s
      rules:
        # Job 상태 알림
        - alert: FlinkJobDown
          expr: up{job="flink"} == 0
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "Flink job is down"
            description: "Flink job {{ $labels.instance }} has been down for more than 5 minutes"

        # Checkpoint 실패 알림
        - alert: CheckpointFailureRate
          expr: rate(flink_jobmanager_job_numberOfFailedCheckpoints[5m]) > 0.1
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "High checkpoint failure rate"
            description: "Checkpoint failure rate is {{ $value }} per second"

        # Backpressure 알림
        - alert: HighBackpressure
          expr: flink_taskmanager_job_task_backPressuredTimeMsPerSecond > 500
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "High backpressure detected"
            description: "Task {{ $labels.task_name }} is backpressured {{ $value }}ms/s"

        # CTR 이상 알림
        - alert: CTRAnomalyDetected
          expr: |
            (
              avg_over_time(current_ctr[1h])
              - avg_over_time(current_ctr[24h] offset 1d)
            ) / avg_over_time(current_ctr[24h] offset 1d) > 0.5
          for: 30m
          labels:
            severity: warning
          annotations:
            summary: "CTR anomaly detected"
            description: "CTR has changed by {{ $value }}% compared to yesterday"

        # 데이터 처리량 급감 알림
        - alert: LowEventThroughput
          expr: rate(events_processed[5m]) < 100
          for: 15m
          labels:
            severity: warning
          annotations:
            summary: "Low event processing rate"
            description: "Processing only {{ $value }} events/s (expected > 100)"

        # Invalid event 비율 알림
        - alert: HighInvalidEventRate
          expr: |
            rate(events_invalid[5m])
            / (rate(events_processed[5m]) + rate(events_invalid[5m]))
            > 0.1
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "High invalid event rate"
            description: "{{ $value }}% of events are invalid"
```

**AlertManager 설정** (`k8s/alertmanager-config.yaml`):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: alertmanager-config
data:
  alertmanager.yml: |
    global:
      resolve_timeout: 5m

    route:
      group_by: ['alertname', 'severity']
      group_wait: 10s
      group_interval: 10s
      repeat_interval: 12h
      receiver: 'slack'

      routes:
        - match:
            severity: critical
          receiver: 'pagerduty'
          continue: true

        - match:
            severity: warning
          receiver: 'slack'

    receivers:
      - name: 'slack'
        slack_configs:
          - api_url: 'https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK'
            channel: '#flink-alerts'
            title: '{{ .CommonAnnotations.summary }}'
            text: '{{ .CommonAnnotations.description }}'

      - name: 'pagerduty'
        pagerduty_configs:
          - service_key: 'YOUR_PAGERDUTY_KEY'
```

**예상 시간**: 8시간
**중요도**: P2

---

### P2-4: Graceful Shutdown 구현

**CtrJobService.kt 수정**:
```kotlin
class CtrJobService(
    private val environmentFactory: FlinkEnvironmentFactory,
    private val pipelineBuilder: CtrJobPipelineBuilder
) {
    private val logger = LoggerFactory.getLogger(CtrJobService::class.java)

    @Volatile
    private var env: StreamExecutionEnvironment? = null

    @Volatile
    private var jobExecutionResult: JobClient? = null

    @Volatile
    private var isShuttingDown = false

    fun execute() {
        try {
            env = environmentFactory.create()
            pipelineBuilder.build(env!!)

            // Register shutdown hook BEFORE starting job
            registerShutdownHook()

            logger.info("Starting Flink job...")
            jobExecutionResult = env!!.executeAsync(env!!.configuration.getString("ctr.job.name", "ctr-calculator"))

            // Wait for job completion
            jobExecutionResult?.jobExecutionResult?.get()

        } catch (e: Exception) {
            if (!isShuttingDown) {
                logger.error("Flink job execution failed", e)
                throw RuntimeException("Failed to execute Flink job", e)
            }
        }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread({
            logger.info("Shutdown signal received, initiating graceful shutdown...")
            isShuttingDown = true

            try {
                jobExecutionResult?.let { client ->
                    logger.info("Triggering savepoint before shutdown...")

                    // Trigger savepoint
                    val savepointPath = client.stopWithSavepoint(
                        false,  // don't drain
                        "/tmp/savepoints",
                        SavepointFormatType.CANONICAL
                    ).get(5, TimeUnit.MINUTES)

                    logger.info("Savepoint created at: $savepointPath")

                    // Cancel job gracefully
                    client.cancel().get(1, TimeUnit.MINUTES)
                    logger.info("Job cancelled successfully")
                }

                env?.close()
                logger.info("Flink environment closed successfully")

            } catch (e: TimeoutException) {
                logger.error("Shutdown timeout - forcing termination", e)
                jobExecutionResult?.cancel()
            } catch (e: Exception) {
                logger.error("Error during graceful shutdown", e)
            }
        }, "shutdown-hook"))
    }
}
```

**Kubernetes Deployment에서 사용**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flink-jobmanager
spec:
  template:
    spec:
      containers:
        - name: jobmanager
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 15"]  # Grace period
          terminationGracePeriodSeconds: 60
```

**예상 시간**: 4시간
**중요도**: P2

---

### P2-5: State Backend 최적화

**FlinkEnvironmentFactory.kt 수정**:
```kotlin
class FlinkEnvironmentFactory(private val properties: CtrJobProperties) {

    fun create(): StreamExecutionEnvironment {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()

        // Basic configuration
        env.setParallelism(properties.parallelism)
        env.setRestartStrategy(...)

        // State backend configuration
        configureStateBackend(env)

        // Checkpoint configuration
        configureCheckpointing(env)

        return env
    }

    private fun configureStateBackend(env: StreamExecutionEnvironment) {
        when (properties.stateBackend) {
            StateBackendType.FILESYSTEM -> {
                // Default - already configured
                logger.info("Using FileSystem state backend")
            }

            StateBackendType.ROCKSDB -> {
                val rocksDB = EmbeddedRocksDBStateBackend(true)  // incremental checkpoints

                // Performance tuning
                rocksDB.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED)

                // Custom RocksDB options
                val options = DefaultConfigurableOptionsFactory()
                options.setMaxBackgroundThreads(4)
                options.setMaxOpenFiles(-1)
                rocksDB.setRocksDBOptions(options)

                env.setStateBackend(rocksDB)
                logger.info("Using RocksDB state backend with incremental checkpoints")
            }
        }

        // State TTL for preventing unbounded state growth
        if (properties.stateTtlHours > 0) {
            logger.info("State TTL configured: ${properties.stateTtlHours} hours")
            // TTL은 개별 state에서 설정 (다음 phase에서 구현)
        }
    }
}

enum class StateBackendType {
    FILESYSTEM,
    ROCKSDB
}
```

**Properties 추가**:
```kotlin
data class CtrJobProperties(
    // ... 기존 속성들

    val stateBackend: StateBackendType = StateBackendType.FILESYSTEM,
    val stateTtlHours: Long = 0,  // 0 = disabled
    val checkpointStorage: String = "file:///tmp/flink-checkpoints"
)
```

**application.yml**:
```yaml
ctr:
  job:
    state-backend: ROCKSDB
    state-ttl-hours: 24
    checkpoint-storage: "s3://my-bucket/flink-checkpoints"  # For production
```

**의존성 추가**:
```kotlin
// build.gradle.kts
implementation("org.apache.flink:flink-statebackend-rocksdb:$flinkVersion")
```

**예상 시간**: 6시간
**중요도**: P2

---

### P2-6: Kubernetes 배포 준비

#### Task 6.1: Helm Chart 작성

**디렉토리 구조**:
```
k8s/
├── helm/
│   └── flink-ctr/
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── templates/
│       │   ├── jobmanager-deployment.yaml
│       │   ├── taskmanager-deployment.yaml
│       │   ├── jobmanager-service.yaml
│       │   ├── configmap.yaml
│       │   ├── secrets.yaml
│       │   └── servicemonitor.yaml
│       └── README.md
```

**Chart.yaml**:
```yaml
apiVersion: v2
name: flink-ctr
description: Real-time CTR calculation pipeline
type: application
version: 1.0.0
appVersion: "1.0.0"
```

**values.yaml**:
```yaml
image:
  repository: your-docker-repo/flink-ctr-app
  tag: latest
  pullPolicy: IfNotPresent

jobmanager:
  replicas: 1
  resources:
    requests:
      memory: "1Gi"
      cpu: "500m"
    limits:
      memory: "2Gi"
      cpu: "1000m"

taskmanager:
  replicas: 2
  resources:
    requests:
      memory: "2Gi"
      cpu: "1000m"
    limits:
      memory: "4Gi"
      cpu: "2000m"
  taskSlots: 2

flink:
  parallelism: 2
  checkpointInterval: 60000
  checkpointStorage: "s3://your-bucket/checkpoints"
  savepointDirectory: "s3://your-bucket/savepoints"

  stateBackend: "rocksdb"
  stateTtlHours: 24

kafka:
  bootstrapServers: "kafka-broker-1:9092,kafka-broker-2:9092"
  groupId: "flink-ctr-consumer"
  security:
    enabled: true
    protocol: "SASL_SSL"

clickhouse:
  host: "clickhouse.default.svc.cluster.local"
  port: 8123
  database: "default"

monitoring:
  prometheus:
    enabled: true
    port: 9249

  metrics:
    reporters: "prom"

secrets:
  kafka:
    username: "flink-user"
    password: "changeme"
  clickhouse:
    password: "changeme"
```

**templates/jobmanager-deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "flink-ctr.fullname" . }}-jobmanager
  labels:
    {{- include "flink-ctr.labels" . | nindent 4 }}
    component: jobmanager
spec:
  replicas: {{ .Values.jobmanager.replicas }}
  selector:
    matchLabels:
      {{- include "flink-ctr.selectorLabels" . | nindent 6 }}
      component: jobmanager
  template:
    metadata:
      labels:
        {{- include "flink-ctr.selectorLabels" . | nindent 8 }}
        component: jobmanager
    spec:
      containers:
        - name: jobmanager
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          args: ["jobmanager"]
          ports:
            - containerPort: 6123
              name: rpc
            - containerPort: 6124
              name: blob-server
            - containerPort: 8081
              name: webui
            - containerPort: 9249
              name: metrics
          env:
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: {{ .Values.kafka.bootstrapServers }}
            - name: KAFKA_USERNAME
              valueFrom:
                secretKeyRef:
                  name: {{ include "flink-ctr.fullname" . }}-secrets
                  key: kafka-username
            - name: KAFKA_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "flink-ctr.fullname" . }}-secrets
                  key: kafka-password
            - name: CLICKHOUSE_HOST
              value: {{ .Values.clickhouse.host }}
            - name: CLICKHOUSE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "flink-ctr.fullname" . }}-secrets
                  key: clickhouse-password
          resources:
            {{- toYaml .Values.jobmanager.resources | nindent 12 }}
          volumeMounts:
            - name: flink-config
              mountPath: /opt/flink/conf
      volumes:
        - name: flink-config
          configMap:
            name: {{ include "flink-ctr.fullname" . }}-config
```

**예상 시간**: 12시간
**중요도**: P2

---

### Phase 3 체크리스트

#### 모니터링
- [ ] Custom Flink metrics 구현
- [ ] Prometheus metrics reporter 설정
- [ ] Grafana dashboard 생성
- [ ] Alerting rules 구성
- [ ] AlertManager 설정

#### 로깅
- [ ] Structured logging 구현
- [ ] JSON log format
- [ ] Log aggregation (ELK/Loki)
- [ ] 주요 이벤트 로깅

#### 운영
- [ ] Graceful shutdown 구현
- [ ] State backend 최적화 (RocksDB)
- [ ] Checkpoint storage (S3/GCS)
- [ ] Kubernetes Helm chart
- [ ] Horizontal Pod Autoscaler

**Phase 3 완료 시 달성**:
- ✅ 실시간 모니터링
- ✅ 자동 알림
- ✅ 안정적인 shutdown
- ✅ K8s 배포 가능

---

## Phase 4: Scale & Optimize (2-3개월)

**목표**: 대규모 트래픽 처리, 성능 최적화
**완료 조건**: 초당 100만 이벤트 처리 가능

### P3-1: 성능 벤치마크

#### Task 1.1: 부하 테스트 도구 구축

**새 파일**: `performance-test/load-generator.py`

```python
#!/usr/bin/env python3
import asyncio
import json
import time
from datetime import datetime
from kafka import KafkaProducer
from faker import Faker
import random

fake = Faker()

class LoadGenerator:
    def __init__(self, bootstrap_servers, events_per_second):
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        self.events_per_second = events_per_second
        self.product_ids = [f"product-{i}" for i in range(1000)]

    def generate_event(self, event_type):
        return {
            "userId": fake.uuid4(),
            "productId": random.choice(self.product_ids),
            "eventType": event_type,
            "timestamp": int(datetime.now().timestamp() * 1000)
        }

    async def produce_events(self):
        interval = 1.0 / self.events_per_second

        while True:
            # 80% impressions, 20% clicks
            event_type = "impression" if random.random() < 0.8 else "click"
            topic = "impressions" if event_type == "impression" else "clicks"

            event = self.generate_event(event_type)
            self.producer.send(topic, value=event)

            await asyncio.sleep(interval)

    async def run(self, duration_seconds=None):
        tasks = [self.produce_events() for _ in range(10)]  # 10 concurrent producers

        if duration_seconds:
            await asyncio.wait_for(
                asyncio.gather(*tasks),
                timeout=duration_seconds
            )
        else:
            await asyncio.gather(*tasks)

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--bootstrap-servers", default="localhost:9092")
    parser.add_argument("--events-per-second", type=int, default=1000)
    parser.add_argument("--duration", type=int, help="Duration in seconds")

    args = parser.parse_args()

    generator = LoadGenerator(args.bootstrap_servers, args.events_per_second)

    asyncio.run(generator.run(args.duration))
```

**실행**:
```bash
# 초당 1,000 이벤트
python load-generator.py --events-per-second 1000 --duration 300

# 초당 10,000 이벤트
python load-generator.py --events-per-second 10000 --duration 300

# 초당 100,000 이벤트
python load-generator.py --events-per-second 100000 --duration 300
```

**예상 시간**: 8시간
**중요도**: P3

---

#### Task 1.2: 성능 메트릭 수집

**Grafana Dashboard JSON** (`k8s/grafana-dashboard-performance.json`):

주요 메트릭:
- Events/second (input rate)
- Records/second (output rate)
- End-to-end latency (p50, p95, p99)
- Checkpoint duration
- State size
- CPU/Memory usage
- Backpressure

**예상 시간**: 4시간
**중요도**: P3

---

### P3-2: Operator Chaining 최적화

**Task**: 이미 작성된 문서 (`docs/flink/operator-chaining/OPERATOR_CHAINING_*.md`) 기반으로 최적화 적용

**현재 chaining 상태 분석**:
```bash
# Flink Web UI에서 확인
# 또는 REST API:
curl http://localhost:8081/jobs/{job-id}/plan
```

**최적화 적용 (CtrJobPipelineBuilder.kt)**:
```kotlin
// 필요한 경우에만 chaining 중단
.filter(Event::isValid)
.disableChaining()  // 필터 후 chaining 중단
.keyBy(Event::productId)
```

**예상 시간**: 6시간
**중요도**: P3

---

### P3-3: Network Buffer 튜닝

**flink-conf.yaml 튜닝**:
```yaml
taskmanager.network.memory.fraction: 0.2
taskmanager.network.memory.min: 256mb
taskmanager.network.memory.max: 1gb

taskmanager.network.numberOfBuffers: 8192
taskmanager.network.bufferSize: 32768

# Backpressure 관련
taskmanager.network.request-backoff.initial: 100
taskmanager.network.request-backoff.max: 10000
```

**예상 시간**: 4시간
**중요도**: P3

---

### P3-4: State TTL 구현

**EventCountAggregator에 TTL 추가**:
```kotlin
class EventCountAggregator : AggregateFunction<Event, EventCount, EventCount> {

    private lateinit var stateDescriptor: AggregatingStateDescriptor<Event, EventCount, EventCount>

    fun createStateDescriptor(ttlHours: Long): AggregatingStateDescriptor<Event, EventCount, EventCount> {
        stateDescriptor = AggregatingStateDescriptor(
            "event-count-agg",
            this,
            EventCount::class.java
        )

        if (ttlHours > 0) {
            val ttlConfig = StateTtlConfig
                .newBuilder(Time.hours(ttlHours))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .cleanupFullSnapshot()
                .build()

            stateDescriptor.enableTimeToLive(ttlConfig)
        }

        return stateDescriptor
    }

    // ... 기존 메서드들
}
```

**예상 시간**: 4시간
**중요도**: P3

---

### Phase 4 체크리스트

- [ ] 부하 테스트 도구 구축
- [ ] 성능 벤치마크 (1K, 10K, 100K, 1M events/s)
- [ ] Operator chaining 최적화
- [ ] Network buffer 튜닝
- [ ] State TTL 구현
- [ ] Incremental checkpoint 검증
- [ ] Auto-scaling 구성 (HPA)
- [ ] Cost optimization

**Phase 4 완료 시 달성**:
- ✅ 초당 100만 이벤트 처리
- ✅ p99 latency < 5초
- ✅ Checkpoint 시간 < 30초
- ✅ 리소스 최적화

---

## Phase 5: Excellence (지속적)

**목표**: 데이터 거버넌스, 고급 기능
**완료 조건**: 엔터프라이즈급 품질

### P3-1: Data Quality Framework

```kotlin
class DataQualityValidator {
    fun validate(event: Event): ValidationResult {
        val violations = mutableListOf<String>()

        // Schema validation
        if (event.userId.isNullOrBlank()) violations.add("userId is required")
        if (event.productId.isNullOrBlank()) violations.add("productId is required")

        // Business rules
        if (event.timestamp != null && event.timestamp > System.currentTimeMillis()) {
            violations.add("timestamp cannot be in the future")
        }

        // Data quality metrics
        recordValidationMetrics(violations)

        return if (violations.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(violations)
        }
    }
}
```

---

### P3-2: Schema Registry 통합

**Avro Schema 정의**:
```json
{
  "type": "record",
  "name": "Event",
  "namespace": "com.example.ctr.avro",
  "fields": [
    {"name": "userId", "type": "string"},
    {"name": "productId", "type": "string"},
    {"name": "eventType", "type": {"type": "enum", "name": "EventType", "symbols": ["CLICK", "IMPRESSION"]}},
    {"name": "timestamp", "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

---

### P3-3: Data Lineage Tracking

**OpenLineage 통합**:
```kotlin
class LineageTracker {
    fun recordDataset(name: String, namespace: String) {
        // Record dataset in OpenLineage
    }

    fun recordJob(jobName: String, inputs: List<Dataset>, outputs: List<Dataset>) {
        // Record job lineage
    }
}
```

---

### Phase 5 체크리스트

- [ ] Data quality framework
- [ ] Schema registry (Confluent/AWS Glue)
- [ ] Data lineage tracking
- [ ] PII data masking
- [ ] GDPR compliance (data deletion)
- [ ] Audit logging
- [ ] Multi-tenancy support
- [ ] Advanced windowing (session, sliding)

---

## Production Readiness Checklist

### Infrastructure
- [ ] Kubernetes cluster ready
- [ ] S3/GCS for checkpoint storage
- [ ] Kafka cluster (3+ brokers)
- [ ] ClickHouse cluster (2+ replicas)
- [ ] Prometheus + Grafana
- [ ] ELK/Loki for logs

### Security
- [ ] Kafka SASL authentication
- [ ] ClickHouse authentication
- [ ] SSL/TLS encryption
- [ ] Network policies (K8s)
- [ ] Secrets management (Vault/K8s Secrets)
- [ ] RBAC configured

### Monitoring
- [ ] Custom metrics implemented
- [ ] Dashboards created
- [ ] Alerts configured
- [ ] On-call rotation setup
- [ ] Runbook documentation

### Testing
- [ ] Unit test coverage > 80%
- [ ] Integration tests pass
- [ ] Load testing completed
- [ ] Chaos engineering (optional)
- [ ] Disaster recovery tested

### Documentation
- [ ] Architecture diagrams
- [ ] API documentation
- [ ] Runbook
- [ ] Troubleshooting guide
- [ ] ADRs (Architecture Decision Records)

### CI/CD
- [ ] Automated testing
- [ ] Docker image build
- [ ] Helm chart deployment
- [ ] Blue-green deployment
- [ ] Rollback procedure

---

## 예상 타임라인

| Phase | Duration | FTE | Completion |
|-------|----------|-----|------------|
| Phase 1: Critical Fixes | 1-2주 | 1.0 | 2주 |
| Phase 2: Foundation | 2-4주 | 1.0 | 6주 |
| Phase 3: Production Ready | 1-2개월 | 1.0 | 3개월 |
| Phase 4: Scale & Optimize | 2-3개월 | 1.0 | 6개월 |
| Phase 5: Excellence | 지속적 | 0.5 | Ongoing |

**Total: 6개월 full-time work**

---

## 성공 기준

### 기술적 성공
- ✅ 테스트 커버리지 80%+
- ✅ 초당 100만 이벤트 처리
- ✅ p99 latency < 5초
- ✅ 99.9% uptime
- ✅ Zero data loss

### 운영적 성공
- ✅ MTTR (Mean Time To Recovery) < 15분
- ✅ Deployment frequency: daily
- ✅ Change failure rate < 5%
- ✅ On-call incidents < 2/month

### 비즈니스 성공
- ✅ Real-time CTR insights
- ✅ Cost < $500/month (AWS)
- ✅ Scalable to 10x traffic
- ✅ Audit-ready compliance

---

## 다음 단계

1. **이 문서를 팀과 공유**하고 피드백 받기
2. **Phase 1부터 시작** - Critical 버그 수정
3. **주간 체크포인트** 설정 - 진행 상황 추적
4. **블로커 식별** - 막히는 부분 빠르게 해결
5. **학습 문서화** - 배운 내용 ADR로 기록

**지금 시작하세요!** Phase 1의 첫 번째 task (CTRResultWindowProcessFunction 버그 수정)부터 시작하면 됩니다.

Good luck! 🚀
