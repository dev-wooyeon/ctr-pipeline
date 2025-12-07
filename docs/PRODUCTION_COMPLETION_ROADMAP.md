# Production-Grade Completion Roadmap

**목표**: 실제 운영 가능한 수준의 완성도 (80-90%)
**기대 평가**: "이 사람 바로 실무 투입 가능하겠다"

**완성 기준**:
- ✅ AWS/GCP 실제 배포
- ✅ 실제 트래픽 처리 (초당 10만 이벤트)
- ✅ 24/7 모니터링 + 자동 알림
- ✅ 99.9% uptime
- ✅ 장애 대응 경험
- ✅ 비용 최적화 (월 $200 이하)

**기간**: 2-3개월 (주말 + 평일 저녁)

---

## 현재 상태 진단

### ✅ 잘 되어 있는 것
- DDD 아키텍처 (도메인 분리)
- Kotlin 코드 품질
- 기본 Flink 파이프라인
- Docker Compose로 로컬 실행

### ❌ 부족한 것 (프로덕션 블로커)
- **보안**: 인증 없음, 암호화 없음
- **테스트**: 테스트가 안 돌아감, 커버리지 28%
- **모니터링**: Prometheus/Grafana 없음
- **배포**: 수동 배포, 롤백 불가
- **운영**: Runbook 없음, 알림 없음
- **확장성**: 로컬 환경만, auto-scaling 없음

---

## Phase 1: Critical Fixes (1-2주)

**목표**: 프로덕션 배포를 막는 치명적 이슈 제거
**완료 조건**: 테스트 통과, 보안 기본 설정, CI/CD 구축

### Week 1: Foundation

#### Task 1.1: 테스트 인프라 복구 (우선순위 1)

**문제**: 테스트가 안 돌아감
**영향**: CI/CD 불가, 배포 불안

**해결**:

```kotlin
// flink-app/build.gradle.kts
dependencies {
    // 명시적 버전 지정
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")

    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.apache.flink:flink-runtime:$flinkVersion:tests")
    testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion:tests")

    // Testcontainers for integration tests
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("org.testcontainers:clickhouse:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2G"

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
# BUILD SUCCESSFUL 확인
```

**예상 시간**: 2시간
**완료 시**: CI에서 테스트 실행 가능

---

#### Task 1.2: GitHub Actions CI 구축

**파일**: `.github/workflows/ci.yml`

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}/flink-ctr-app

jobs:
  test:
    name: Run Tests
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Grant execute permission
        run: chmod +x flink-app/gradlew

      - name: Run tests with coverage
        run: |
          cd flink-app
          ./gradlew clean test jacocoTestReport

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: flink-app/build/reports/jacoco/test/jacocoTestReport.xml
          fail_ci_if_error: false

      - name: Check coverage threshold
        run: |
          cd flink-app
          ./gradlew jacocoTestCoverageVerification

  build:
    name: Build JAR
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

      - name: Build shadowJar
        run: |
          cd flink-app
          ./gradlew clean shadowJar -x test

      - name: Upload JAR artifact
        uses: actions/upload-artifact@v4
        with:
          name: flink-ctr-app-jar
          path: flink-app/build/libs/ctr-calculator-*.jar
          retention-days: 7

  docker:
    name: Build and Push Docker Image
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'

    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Download JAR
        uses: actions/download-artifact@v4
        with:
          name: flink-ctr-app-jar
          path: flink-app/build/libs/

      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix={{branch}}-
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

**Dockerfile 생성**:

```dockerfile
# Dockerfile
FROM flink:1.18-scala_2.12-java17

# Copy application JAR
COPY flink-app/build/libs/ctr-calculator-*.jar /opt/flink/usrlib/

# Copy configuration
COPY flink-app/src/main/resources/*.yml /opt/flink/conf/

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8081/overview || exit 1

# Flink will use entrypoint from base image
```

**예상 시간**: 4시간
**완료 시**:
- 코드 push → 자동 테스트
- Main 브랜치 → Docker 이미지 자동 빌드
- Badge 추가 가능

---

#### Task 1.3: JaCoCo 테스트 커버리지 강제

**build.gradle.kts 수정**:

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    jacoco
}

jacoco {
    toolVersion = "0.8.11"
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
                    "**/CtrApplication*",
                    "**/config/**",
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
                minimum = "0.70".toBigDecimal()  // 70% 최소 (점진적 증가)
            }
        }

        rule {
            enabled = true
            element = "CLASS"

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }

            excludes = listOf(
                "com.example.ctr.CtrApplication",
                "com.example.ctr.config.*"
            )
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

**예상 시간**: 2시간
**완료 시**: 커버리지 70% 이하면 빌드 실패

---

### Week 2: Security & Config

#### Task 1.4: 보안 기본 설정

**Kafka SASL 인증**:

```kotlin
// KafkaProperties.kt에 추가
data class KafkaSecurityProperties(
    val enabled: Boolean = false,
    val protocol: String = "SASL_SSL",
    val saslMechanism: String = "PLAIN",
    val saslUsername: String? = null,
    val saslPassword: String? = null,
    val sslTruststoreLocation: String? = null,
    val sslTruststorePassword: String? = null
)

data class KafkaProperties(
    @field:NotBlank val bootstrapServers: String,
    @field:NotBlank val groupId: String,
    val security: KafkaSecurityProperties = KafkaSecurityProperties()
)
```

**KafkaSourceFactory.kt 수정**:

```kotlin
fun createSource(topic: String, groupId: String): KafkaSource<Event> {
    val sourceBuilder = KafkaSource.builder<Event>()
        .setBootstrapServers(properties.bootstrapServers)
        .setTopics(topic)
        .setGroupId(groupId)
        .setDeserializer(EventDeserializationSchema())
        .setStartingOffsets(OffsetsInitializer.latest())

    // Security configuration
    if (properties.security.enabled) {
        val kafkaProps = Properties().apply {
            put("security.protocol", properties.security.protocol)
            put("sasl.mechanism", properties.security.saslMechanism)

            properties.security.saslUsername?.let { username ->
                properties.security.saslPassword?.let { password ->
                    put("sasl.jaas.config",
                        "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                        "username=\"$username\" " +
                        "password=\"$password\";")
                }
            }

            properties.security.sslTruststoreLocation?.let {
                put("ssl.truststore.location", it)
            }
            properties.security.sslTruststorePassword?.let {
                put("ssl.truststore.password", it)
            }
        }

        sourceBuilder.setProperties(kafkaProps)
    }

    return sourceBuilder.build()
}
```

**ClickHouse 인증**:

```kotlin
// ClickHouseProperties.kt 수정
data class ClickHouseProperties(
    @field:NotBlank val url: String,
    val username: String = "default",
    val password: String? = null,
    @field:Positive val batchSize: Int = 1000,
    @field:Positive val batchIntervalMs: Long = 200,
    @field:Positive val maxRetries: Int = 3
)

// ClickHouseSink.kt 수정
fun createSink(): SinkFunction<CTRResult> {
    val connectionOptions = JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
        .withUrl(properties.url)
        .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
        .withUsername(properties.username)
        .apply {
            properties.password?.let { withPassword(it) }
        }
        .build()

    // ... rest of the code
}
```

**환경변수 기반 설정** (application.yml):

```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  group-id: ${KAFKA_GROUP_ID:flink-ctr-consumer}
  security:
    enabled: ${KAFKA_SECURITY_ENABLED:false}
    protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_SSL}
    sasl-mechanism: ${KAFKA_SASL_MECHANISM:PLAIN}
    sasl-username: ${KAFKA_USERNAME:}
    sasl-password: ${KAFKA_PASSWORD:}

clickhouse:
  url: ${CLICKHOUSE_URL:jdbc:clickhouse://clickhouse:8123/default}
  username: ${CLICKHOUSE_USERNAME:default}
  password: ${CLICKHOUSE_PASSWORD:}
```

**예상 시간**: 6시간
**완료 시**: AWS MSK, Confluent Cloud 등 managed service 사용 가능

---

#### Task 1.5: Secrets Management (Kubernetes)

**파일**: `k8s/base/secrets.yaml` (예시, 실제로는 sealed-secrets 사용)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: flink-ctr-secrets
type: Opaque
stringData:
  kafka-username: "flink-user"
  kafka-password: "CHANGE_ME"
  clickhouse-password: "CHANGE_ME"
```

**Sealed Secrets 사용** (권장):

```bash
# Install sealed-secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# Create sealed secret
kubectl create secret generic flink-ctr-secrets \
  --from-literal=kafka-username=flink-user \
  --from-literal=kafka-password=actual-password \
  --from-literal=clickhouse-password=actual-password \
  --dry-run=client -o yaml | \
kubeseal -o yaml > k8s/base/sealed-secrets.yaml

# Commit sealed-secrets.yaml to git (안전)
```

**예상 시간**: 3시간
**완료 시**: 비밀번호를 코드에 안 넣어도 됨

---

### Phase 1 완료 기준

- [ ] 모든 테스트 통과 (./gradlew test)
- [ ] GitHub Actions CI 동작 (자동 테스트 + Docker 빌드)
- [ ] 테스트 커버리지 70% 이상
- [ ] Kafka SASL 인증 구현
- [ ] ClickHouse 인증 구현
- [ ] Secrets management (sealed-secrets)
- [ ] Docker 이미지 자동 빌드

**예상 시간**: 20시간 (주말 2주)

**완료 시 포트폴리오**:
- ✅ "CI/CD 파이프라인 구축"
- ✅ "보안 설정 (인증/암호화)"
- ✅ "테스트 커버리지 70%"

---

## Phase 2: Production Foundation (2-3주)

**목표**: 핵심 테스트, 모니터링, 성능 검증
**완료 조건**: Integration test 통과, Monitoring 동작, 성능 벤치마크

### Week 3: Core Testing

#### Task 2.1: Integration Test 작성

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
        private val clickhouse = ClickHouseContainer("clickhouse/clickhouse-server:23.8")
            .withInitScript("init-clickhouse.sql")
            .withUsername("default")
            .withPassword("test-password")
    }

    private lateinit var env: StreamExecutionEnvironment
    private lateinit var jobClient: JobClient

    @BeforeEach
    fun setup() {
        env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
        env.setParallelism(1)

        // Disable checkpointing for faster tests
        env.checkpointConfig.disableCheckpointing()
    }

    @AfterEach
    fun teardown() {
        jobClient?.cancel()?.get(5, TimeUnit.SECONDS)
    }

    @Test
    fun `should calculate CTR correctly for single product within one window`() {
        // Given
        val productId = "test-product-001"
        val impressions = 100L
        val clicks = 25L
        val baseTime = System.currentTimeMillis()

        val impressionEvents = (1..impressions).map { i ->
            createEvent("impression", productId, baseTime + i * 10)
        }

        val clickEvents = (1..clicks).map { i ->
            createEvent("click", productId, baseTime + i * 40)
        }

        // When
        produceToKafka("impressions", impressionEvents)
        produceToKafka("clicks", clickEvents)

        startPipeline()

        // Wait for window to close + processing
        Thread.sleep(15000)

        // Then
        val results = queryClickHouse(productId)

        assertThat(results).hasSize(1)
        val result = results.first()

        assertThat(result.productId).isEqualTo(productId)
        assertThat(result.impressions).isEqualTo(impressions)
        assertThat(result.clicks).isEqualTo(clicks)
        assertThat(result.ctr).isCloseTo(0.25, within(0.001))
    }

    @Test
    fun `should handle late events within allowed lateness`() {
        val productId = "test-product-002"
        val windowStart = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val windowEnd = windowStart.plusSeconds(10)

        // Events within window
        val onTimeEvents = listOf(
            createEvent("impression", productId, windowStart.toEpochMilli() + 1000),
            createEvent("impression", productId, windowStart.toEpochMilli() + 2000),
            createEvent("click", productId, windowStart.toEpochMilli() + 3000)
        )

        produceToKafka("impressions", onTimeEvents.filter { it.eventType == "impression" })
        produceToKafka("clicks", onTimeEvents.filter { it.eventType == "click" })

        startPipeline()
        Thread.sleep(12000)  // Window should close

        // Late event (within allowed lateness of 5s)
        val lateEvent = createEvent("click", productId, windowStart.toEpochMilli() + 4000)
        produceToKafka("clicks", listOf(lateEvent))

        Thread.sleep(6000)  // Wait for late event processing

        val results = queryClickHouse(productId)

        assertThat(results).hasSize(1)
        assertThat(results.first().clicks).isEqualTo(2)  // Late event counted
    }

    @Test
    fun `should drop events beyond allowed lateness`() {
        val productId = "test-product-003"
        val windowStart = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        val onTimeEvent = createEvent("impression", productId, windowStart.toEpochMilli() + 1000)
        produceToKafka("impressions", listOf(onTimeEvent))

        startPipeline()
        Thread.sleep(20000)  // Window closes + allowed lateness expires

        // Very late event (beyond 5s allowed lateness)
        val veryLateEvent = createEvent("click", productId, windowStart.toEpochMilli() + 2000)
        produceToKafka("clicks", listOf(veryLateEvent))

        Thread.sleep(3000)

        val results = queryClickHouse(productId)

        assertThat(results).hasSize(1)
        assertThat(results.first().clicks).isEqualTo(0)  // Late event dropped
    }

    @Test
    fun `should process multiple products in same window`() {
        val products = listOf("prod-A", "prod-B", "prod-C")
        val baseTime = System.currentTimeMillis()

        val allEvents = products.flatMap { productId ->
            (1..50).map { i ->
                createEvent(if (i % 5 == 0) "click" else "impression", productId, baseTime + i * 100)
            }
        }

        val impressions = allEvents.filter { it.eventType == "impression" }
        val clicks = allEvents.filter { it.eventType == "click" }

        produceToKafka("impressions", impressions)
        produceToKafka("clicks", clicks)

        startPipeline()
        Thread.sleep(15000)

        val results = queryClickHouseAll()

        assertThat(results).hasSizeGreaterThanOrEqualTo(3)
        products.forEach { productId ->
            val productResults = results.filter { it.productId == productId }
            assertThat(productResults).isNotEmpty
            assertThat(productResults.first().ctr).isCloseTo(0.2, within(0.05))
        }
    }

    @Test
    fun `should handle high throughput within single window`() {
        val productId = "test-product-high-throughput"
        val impressionCount = 10000L
        val clickCount = 2500L
        val baseTime = System.currentTimeMillis()

        // Generate events
        val impressions = (1..impressionCount).map { i ->
            createEvent("impression", productId, baseTime + i)
        }

        val clicks = (1..clickCount).map { i ->
            createEvent("click", productId, baseTime + i * 4)
        }

        // Produce in batches for performance
        impressions.chunked(1000).forEach { batch ->
            produceToKafka("impressions", batch)
        }

        clicks.chunked(1000).forEach { batch ->
            produceToKafka("clicks", batch)
        }

        startPipeline()
        Thread.sleep(20000)

        val results = queryClickHouse(productId)

        assertThat(results).hasSize(1)
        assertThat(results.first().impressions).isEqualTo(impressionCount)
        assertThat(results.first().clicks).isEqualTo(clickCount)
        assertThat(results.first().ctr).isCloseTo(0.25, within(0.001))
    }

    // Helper methods
    private fun createEvent(eventType: String, productId: String, timestamp: Long): Event {
        return Event(
            userId = "user-${UUID.randomUUID()}",
            productId = productId,
            eventType = eventType,
            timestamp = timestamp
        )
    }

    private fun produceToKafka(topic: String, events: List<Event>) {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        }

        KafkaProducer<String, String>(props).use { producer ->
            events.forEach { event ->
                val json = ObjectMapper()
                    .registerModule(JavaTimeModule())
                    .writeValueAsString(event)

                producer.send(ProducerRecord(topic, event.productId, json)).get()
            }
        }
    }

    private fun startPipeline() {
        val config = createTestConfig()
        val kafkaSourceFactory = KafkaSourceFactory(config.kafka)
        val clickHouseSink = ClickHouseSink(config.clickhouse)
        val pipelineBuilder = CtrJobPipelineBuilder(
            kafkaSourceFactory,
            clickHouseSink,
            properties = config.ctr.job
        )

        pipelineBuilder.build(env)
        jobClient = env.executeAsync("test-job")
    }

    private fun createTestConfig(): AppConfig {
        return AppConfig(
            kafka = KafkaProperties(
                bootstrapServers = kafka.bootstrapServers,
                groupId = "test-group"
            ),
            clickhouse = ClickHouseProperties(
                url = clickhouse.jdbcUrl,
                username = "default",
                password = "test-password"
            ),
            ctr = CtrProperties(
                job = CtrJobProperties(
                    name = "test-job",
                    parallelism = 1,
                    checkpointIntervalMs = 10000,
                    // ... other properties
                ),
                impressionTopic = "impressions",
                clickTopic = "clicks"
            )
        )
    }

    private fun queryClickHouse(productId: String): List<CTRResult> {
        return clickhouse.createConnection("").use { conn ->
            val stmt = conn.prepareStatement(
                """
                SELECT product_id, impressions, clicks, ctr, window_start, window_end
                FROM ctr_results
                WHERE product_id = ?
                ORDER BY window_end DESC
                """.trimIndent()
            )
            stmt.setString(1, productId)

            val rs = stmt.executeQuery()
            val results = mutableListOf<CTRResult>()

            while (rs.next()) {
                results.add(
                    CTRResult(
                        productId = rs.getString("product_id"),
                        impressions = rs.getLong("impressions"),
                        clicks = rs.getLong("clicks"),
                        ctr = rs.getDouble("ctr"),
                        windowStart = Instant.ofEpochMilli(rs.getTimestamp("window_start").time),
                        windowEnd = Instant.ofEpochMilli(rs.getTimestamp("window_end").time)
                    )
                )
            }

            results
        }
    }

    private fun queryClickHouseAll(): List<CTRResult> {
        // Similar to queryClickHouse but without WHERE clause
    }
}
```

**init-clickhouse.sql**:

```sql
CREATE TABLE IF NOT EXISTS ctr_results (
    product_id String,
    impressions UInt64,
    clicks UInt64,
    ctr Float64,
    window_start DateTime,
    window_end DateTime
) ENGINE = MergeTree()
ORDER BY (product_id, window_end);
```

**예상 시간**: 12시간
**완료 시**: End-to-end 검증 가능

---

#### Task 2.2: Unit Test 추가 (커버리지 70% 달성)

**주요 테스트 대상**:

1. **EventCountAggregator** - 이미 있음, 개선
2. **CTRResultWindowProcessFunction** - 추가 필요
3. **EventDeserializationSchema** - 이미 있음
4. **DataQualityValidator** (새로 추가 예정) - 테스트 작성
5. **FlinkEnvironmentFactory** - 추가 필요

**CTRResultWindowProcessFunctionTest.kt**:

```kotlin
class CTRResultWindowProcessFunctionTest {

    private lateinit var processFunction: CTRResultWindowProcessFunction
    private lateinit var context: ProcessWindowFunction.Context
    private lateinit var collector: Collector<CTRResult>

    @BeforeEach
    fun setup() {
        processFunction = CTRResultWindowProcessFunction()
        context = mock(ProcessWindowFunction.Context::class.java)
        collector = mock(Collector::class.java)

        val window = mock(TimeWindow::class.java)
        `when`(context.window()).thenReturn(window)
        `when`(window.start).thenReturn(1000L)
        `when`(window.end).thenReturn(11000L)
    }

    @Test
    fun `should calculate CTR correctly`() {
        val productId = "test-product"
        val counts = EventCount(impressions = 100, clicks = 25)

        processFunction.process(productId, context, listOf(counts), collector)

        val captor = argumentCaptor<CTRResult>()
        verify(collector).collect(captor.capture())

        val result = captor.firstValue
        assertThat(result.productId).isEqualTo(productId)
        assertThat(result.impressions).isEqualTo(100)
        assertThat(result.clicks).isEqualTo(25)
        assertThat(result.ctr).isEqualTo(0.25)
    }

    @Test
    fun `should handle zero impressions`() {
        val counts = EventCount(impressions = 0, clicks = 5)

        processFunction.process("prod", context, listOf(counts), collector)

        val captor = argumentCaptor<CTRResult>()
        verify(collector).collect(captor.capture())

        assertThat(captor.firstValue.ctr).isEqualTo(0.0)
    }

    @Test
    fun `should handle null productId with default value`() {
        val counts = EventCount(impressions = 100, clicks = 25)

        processFunction.process(null, context, listOf(counts), collector)

        val captor = argumentCaptor<CTRResult>()
        verify(collector).collect(captor.capture())

        assertThat(captor.firstValue.productId).isEqualTo("<unknown>")
    }

    @Test
    fun `should throw exception when elements is empty`() {
        assertThatThrownBy {
            processFunction.process("prod", context, emptyList(), collector)
        }.isInstanceOf(NoSuchElementException::class.java)
    }
}
```

**예상 시간**: 8시간
**완료 시**: 70% 커버리지 달성

---

### Week 4-5: Monitoring & Observability

#### Task 2.3: Prometheus + Grafana 설정

**docker-compose.yml에 추가**:

```yaml
services:
  # ... existing services

  prometheus:
    image: prom/prometheus:v2.48.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus/rules.yml:/etc/prometheus/rules.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
    mem_limit: 512m
    cpus: 0.5

  grafana:
    image: grafana/grafana:10.2.2
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD:-admin}
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_SERVER_ROOT_URL=http://localhost:3000
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus
    mem_limit: 256m
    cpus: 0.25

volumes:
  prometheus-data:
  grafana-data:
  # ... existing volumes
```

**prometheus/prometheus.yml**:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'local-dev'
    environment: 'development'

# Alerting configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets: []  # Add AlertManager if needed

# Load rules
rule_files:
  - 'rules.yml'

scrape_configs:
  - job_name: 'flink-jobmanager'
    static_configs:
      - targets: ['flink-jobmanager:9249']
        labels:
          component: 'jobmanager'

  - job_name: 'flink-taskmanager'
    static_configs:
      - targets: ['flink-taskmanager:9249']
        labels:
          component: 'taskmanager'
```

**prometheus/rules.yml**:

```yaml
groups:
  - name: flink_alerts
    interval: 30s
    rules:
      # Job Health
      - alert: FlinkJobDown
        expr: up{job=~"flink-.*"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Flink component {{ $labels.job }} is down"
          description: "{{ $labels.job }} on {{ $labels.instance }} has been down for more than 2 minutes"

      # Checkpoint Alerts
      - alert: CheckpointFailureRate
        expr: rate(flink_jobmanager_job_numberOfFailedCheckpoints[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High checkpoint failure rate"
          description: "Checkpoint failure rate is {{ $value | humanize }} per second"

      - alert: CheckpointDurationHigh
        expr: flink_jobmanager_job_lastCheckpointDuration > 60000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Checkpoint duration is high"
          description: "Last checkpoint took {{ $value | humanizeDuration }}"

      # Backpressure
      - alert: HighBackpressure
        expr: flink_taskmanager_job_task_backPressuredTimeMsPerSecond > 500
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High backpressure detected"
          description: "Task {{ $labels.task_name }} is backpressured {{ $value }}ms/s"

      # Kafka Lag (if exposed)
      - alert: KafkaLagHigh
        expr: flink_taskmanager_job_task_operator_KafkaSourceReader_currentFetchEventTimeLag > 60000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Kafka lag is high"
          description: "Kafka lag is {{ $value | humanizeDuration }}"
```

**Grafana Dashboard JSON** (`grafana/provisioning/dashboards/flink-ctr.json`):

대시보드 구성:
1. **Overview Row**
   - Job Status (up/down)
   - Total Events Processed
   - Current Throughput (events/s)
   - Average CTR

2. **Throughput Row**
   - Events Ingested (graph, per source)
   - Records Emitted (graph, per operator)
   - Processing Rate (graph)

3. **Latency Row**
   - Event Time Lag (graph)
   - Processing Latency (graph, p50/p95/p99)
   - Window Delay (graph)

4. **State & Checkpointing Row**
   - Checkpoint Duration (graph)
   - Checkpoint Size (graph)
   - State Size (graph)
   - Checkpoint Failure Rate (graph)

5. **System Resources Row**
   - CPU Usage (graph, per container)
   - Memory Usage (graph, per container)
   - Network I/O (graph)
   - Disk I/O (graph)

6. **Data Quality Row**
   - Valid vs Invalid Events (pie chart)
   - Invalid Event Rate (graph)
   - Late Events Dropped (counter)

**예상 시간**: 10시간
**완료 시**: 실시간 모니터링 가능

---

#### Task 2.4: Custom Metrics 구현

**파일**: `flink-app/src/main/kotlin/com/example/ctr/infrastructure/metrics/CtrMetrics.kt`

```kotlin
class CtrMetrics(runtimeContext: RuntimeContext) {
    private val metricGroup = runtimeContext.metricGroup

    // Counters
    val eventsProcessed: Counter = metricGroup.counter("events_processed")
    val eventsInvalid: Counter = metricGroup.counter("events_invalid")
    val eventsLate: Counter = metricGroup.counter("events_late")
    val clicksTotal: Counter = metricGroup.counter("clicks_total")
    val impressionsTotal: Counter = metricGroup.counter("impressions_total")

    // Gauges
    @Volatile private var currentCtr: Double = 0.0
    @Volatile private var activeProducts: Long = 0
    @Volatile private var avgWindowSize: Long = 0

    val ctrGauge: Gauge<Double> = metricGroup.gauge("current_ctr") { currentCtr }
    val activeProductsGauge: Gauge<Long> = metricGroup.gauge("active_products") { activeProducts }
    val avgWindowSizeGauge: Gauge<Long> = metricGroup.gauge("avg_window_size") { avgWindowSize }

    // Histograms
    val ctrDistribution: Histogram = metricGroup.histogram(
        "ctr_distribution",
        DescriptiveStatisticsHistogram(1000)
    )

    val windowProcessingTime: Histogram = metricGroup.histogram(
        "window_processing_time_ms",
        DescriptiveStatisticsHistogram(1000)
    )

    val eventLatency: Histogram = metricGroup.histogram(
        "event_latency_ms",
        DescriptiveStatisticsHistogram(10000)
    )

    fun recordCtr(ctr: Double) {
        currentCtr = ctr
        ctrDistribution.update((ctr * 10000).toLong())  // Store as basis points
    }

    fun recordWindowProcessingTime(startTime: Long) {
        val duration = System.currentTimeMillis() - startTime
        windowProcessingTime.update(duration)
    }

    fun recordEventLatency(eventTime: Long) {
        val latency = System.currentTimeMillis() - eventTime
        eventLatency.update(latency)
    }

    fun updateActiveProducts(count: Long) {
        activeProducts = count
    }

    fun updateAvgWindowSize(size: Long) {
        avgWindowSize = size
    }
}
```

**적용** (EventDeserializationSchema, CTRResultWindowProcessFunction 등):

```kotlin
// EventDeserializationSchema.kt
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

                event.timestamp?.let {
                    metrics.recordEventLatency(it)
                }

                when (event.eventType) {
                    "click" -> metrics.clicksTotal.inc()
                    "impression", "view" -> metrics.impressionsTotal.inc()
                }

                event
            } else {
                metrics.eventsInvalid.inc()
                logger.warn("Invalid event: $event")
                null
            }
        } catch (e: Exception) {
            metrics.eventsInvalid.inc()
            logger.error("Deserialization failed", e)
            null
        }
    }
}
```

**예상 시간**: 6시간
**완료 시**: 커스텀 비즈니스 메트릭 수집

---

### Phase 2 완료 기준

- [ ] Integration test 5개 이상 작성 및 통과
- [ ] Unit test 추가로 커버리지 70% 달성
- [ ] Prometheus + Grafana 설정 완료
- [ ] Grafana dashboard 6개 row 구성
- [ ] Custom metrics 구현 (10개 이상)
- [ ] Alert rules 5개 이상 설정
- [ ] 모든 테스트 CI에서 자동 실행

**예상 시간**: 40시간 (주말 3-4주)

**완료 시 포트폴리오**:
- ✅ "Integration test로 end-to-end 검증"
- ✅ "Prometheus + Grafana 모니터링"
- ✅ "Custom metrics 10+ 구현"
- ✅ "자동 알림 설정"

---

## Phase 3: Kubernetes Deployment (3-4주)

**목표**: 실제 클라우드 환경에 배포
**완료 조건**: AWS EKS 또는 GCP GKE에서 실행 중

### Week 6-7: Kubernetes Setup

#### Task 3.1: Helm Chart 작성

**디렉토리 구조**:

```
k8s/
├── helm/
│   └── flink-ctr/
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── values-dev.yaml
│       ├── values-prod.yaml
│       └── templates/
│           ├── _helpers.tpl
│           ├── configmap.yaml
│           ├── secret.yaml
│           ├── jobmanager-deployment.yaml
│           ├── taskmanager-deployment.yaml
│           ├── jobmanager-service.yaml
│           ├── taskmanager-service.yaml
│           ├── hpa.yaml
│           ├── servicemonitor.yaml
│           └── ingress.yaml
```

**Chart.yaml**:

```yaml
apiVersion: v2
name: flink-ctr
description: Real-time CTR calculation pipeline with Apache Flink
type: application
version: 1.0.0
appVersion: "1.0.0"
keywords:
  - flink
  - streaming
  - ctr
  - real-time
maintainers:
  - name: Your Name
    email: your.email@example.com
```

**values.yaml** (기본값):

```yaml
# Image configuration
image:
  repository: ghcr.io/your-username/flink-ctr-app
  tag: latest
  pullPolicy: IfNotPresent
  pullSecrets: []

# Flink configuration
flink:
  version: "1.18"

  jobmanager:
    replicas: 1
    resources:
      requests:
        memory: "1Gi"
        cpu: "500m"
      limits:
        memory: "2Gi"
        cpu: "1000m"

    service:
      type: ClusterIP
      ports:
        rpc: 6123
        blob: 6124
        ui: 8081
        metrics: 9249

    # Java options
    javaOptions: "-Xms1024m -Xmx1024m"

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
    javaOptions: "-Xms2048m -Xmx2048m"

# Job configuration
job:
  name: "ctr-calculator"
  parallelism: 4

  # Checkpointing
  checkpointing:
    enabled: true
    interval: 60000  # 60s
    timeout: 600000  # 10min
    minPause: 500
    mode: "EXACTLY_ONCE"
    storage: "s3://your-bucket/flink-checkpoints"

  # Restart strategy
  restart:
    attempts: 3
    delayMs: 10000

  # State backend
  stateBackend: "rocksdb"  # or "filesystem"

  # Window configuration
  windowSizeSeconds: 10
  watermarkMaxOutOfOrderSeconds: 5
  allowedLatenessSeconds: 5

# Kafka configuration
kafka:
  bootstrapServers: "kafka-broker-1:9092,kafka-broker-2:9092"
  groupId: "flink-ctr-consumer"

  topics:
    impressions: "impressions"
    clicks: "clicks"

  security:
    enabled: true
    protocol: "SASL_SSL"
    saslMechanism: "PLAIN"
    # Credentials from secret
    usernameSecret:
      name: kafka-credentials
      key: username
    passwordSecret:
      name: kafka-credentials
      key: password

# ClickHouse configuration
clickhouse:
  host: "clickhouse.default.svc.cluster.local"
  port: 8123
  database: "default"
  # Credentials from secret
  passwordSecret:
    name: clickhouse-credentials
    key: password

# Monitoring
monitoring:
  prometheus:
    enabled: true
    port: 9249

  serviceMonitor:
    enabled: true
    interval: 15s
    scrapeTimeout: 10s

# Autoscaling
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

# Ingress (optional, for Flink UI)
ingress:
  enabled: false
  className: nginx
  annotations: {}
  hosts:
    - host: flink-ctr.example.com
      paths:
        - path: /
          pathType: Prefix
  tls: []

# Persistence (for checkpoints/savepoints)
persistence:
  enabled: true
  storageClass: "gp3"  # AWS
  accessMode: ReadWriteOnce
  size: 10Gi

# Pod annotations
podAnnotations: {}

# Node selector
nodeSelector: {}

# Tolerations
tolerations: []

# Affinity
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
              - key: app
                operator: In
                values:
                  - flink-ctr
          topologyKey: kubernetes.io/hostname
```

**values-prod.yaml** (프로덕션 override):

```yaml
image:
  tag: "v1.0.0"  # Specific version
  pullPolicy: IfNotPresent

flink:
  jobmanager:
    replicas: 2  # HA
    resources:
      requests:
        memory: "2Gi"
        cpu: "1000m"
      limits:
        memory: "4Gi"
        cpu: "2000m"

  taskmanager:
    replicas: 4
    resources:
      requests:
        memory: "4Gi"
        cpu: "2000m"
      limits:
        memory: "8Gi"
        cpu: "4000m"

job:
  parallelism: 8
  checkpointing:
    storage: "s3://prod-bucket/flink-checkpoints"
  stateBackend: "rocksdb"

kafka:
  bootstrapServers: "prod-kafka-1:9092,prod-kafka-2:9092,prod-kafka-3:9092"

autoscaling:
  enabled: true
  minReplicas: 4
  maxReplicas: 20

ingress:
  enabled: true
  hosts:
    - host: flink-ctr.prod.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: flink-ctr-tls
      hosts:
        - flink-ctr.prod.example.com
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
  replicas: {{ .Values.flink.jobmanager.replicas }}
  selector:
    matchLabels:
      {{- include "flink-ctr.selectorLabels" . | nindent 6 }}
      component: jobmanager
  template:
    metadata:
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
        {{- with .Values.podAnnotations }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      labels:
        {{- include "flink-ctr.selectorLabels" . | nindent 8 }}
        component: jobmanager
    spec:
      {{- with .Values.image.pullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}

      containers:
        - name: jobmanager
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}

          args: ["jobmanager"]

          ports:
            - name: rpc
              containerPort: 6123
            - name: blob
              containerPort: 6124
            - name: ui
              containerPort: 8081
            - name: metrics
              containerPort: 9249

          env:
            - name: FLINK_PROPERTIES
              value: |
                jobmanager.rpc.address: {{ include "flink-ctr.fullname" . }}-jobmanager
                jobmanager.rpc.port: 6123
                taskmanager.numberOfTaskSlots: {{ .Values.flink.taskmanager.taskSlots }}
                parallelism.default: {{ .Values.job.parallelism }}

                # Checkpointing
                execution.checkpointing.interval: {{ .Values.job.checkpointing.interval }}
                execution.checkpointing.timeout: {{ .Values.job.checkpointing.timeout }}
                execution.checkpointing.min-pause: {{ .Values.job.checkpointing.minPause }}
                execution.checkpointing.mode: {{ .Values.job.checkpointing.mode }}
                state.checkpoints.dir: {{ .Values.job.checkpointing.storage }}

                # State backend
                state.backend: {{ .Values.job.stateBackend }}

                # Metrics
                metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
                metrics.reporter.prom.port: 9249

            # Application config from ConfigMap
            - name: KAFKA_BOOTSTRAP_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: {{ include "flink-ctr.fullname" . }}-config
                  key: kafka.bootstrap-servers

            - name: KAFKA_GROUP_ID
              valueFrom:
                configMapKeyRef:
                  name: {{ include "flink-ctr.fullname" . }}-config
                  key: kafka.group-id

            # Secrets
            - name: KAFKA_USERNAME
              valueFrom:
                secretKeyRef:
                  name: {{ .Values.kafka.security.usernameSecret.name }}
                  key: {{ .Values.kafka.security.usernameSecret.key }}

            - name: KAFKA_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ .Values.kafka.security.passwordSecret.name }}
                  key: {{ .Values.kafka.security.passwordSecret.key }}

            - name: CLICKHOUSE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ .Values.clickhouse.passwordSecret.name }}
                  key: {{ .Values.clickhouse.passwordSecret.key }}

          resources:
            {{- toYaml .Values.flink.jobmanager.resources | nindent 12 }}

          livenessProbe:
            httpGet:
              path: /overview
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          readinessProbe:
            httpGet:
              path: /overview
              port: 8081
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3

      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}

      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}

      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

**templates/hpa.yaml** (Horizontal Pod Autoscaler):

```yaml
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "flink-ctr.fullname" . }}-taskmanager
  labels:
    {{- include "flink-ctr.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "flink-ctr.fullname" . }}-taskmanager
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetMemoryUtilizationPercentage }}
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 15
        - type: Pods
          value: 2
          periodSeconds: 15
      selectPolicy: Max
{{- end }}
```

**예상 시간**: 16시간
**완료 시**: Helm으로 배포 가능

---

#### Task 3.2: AWS EKS 또는 GCP GKE 클러스터 생성

**AWS EKS 옵션** (Terraform):

```hcl
# terraform/main.tf
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = "flink-ctr-cluster"
  cluster_version = "1.28"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Managed node groups
  eks_managed_node_groups = {
    general = {
      min_size     = 2
      max_size     = 10
      desired_size = 3

      instance_types = ["t3.large"]
      capacity_type  = "SPOT"  # Cost optimization

      labels = {
        role = "general"
      }

      tags = {
        Environment = "production"
        Project     = "flink-ctr"
      }
    }
  }

  # Enable IRSA for S3 access
  enable_irsa = true

  tags = {
    Environment = "production"
    Project     = "flink-ctr"
  }
}

# S3 bucket for checkpoints
resource "aws_s3_bucket" "checkpoints" {
  bucket = "flink-ctr-checkpoints-${random_id.suffix.hex}"

  tags = {
    Name    = "flink-checkpoints"
    Project = "flink-ctr"
  }
}

resource "aws_s3_bucket_versioning" "checkpoints" {
  bucket = aws_s3_bucket.checkpoints.id

  versioning_configuration {
    status = "Enabled"
  }
}
```

**GCP GKE 옵션** (Terraform):

```hcl
# terraform/main.tf
resource "google_container_cluster" "primary" {
  name     = "flink-ctr-cluster"
  location = "us-central1"

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.
  remove_default_node_pool = true
  initial_node_count       = 1

  network    = google_compute_network.vpc.name
  subnetwork = google_compute_subnetwork.subnet.name
}

resource "google_container_node_pool" "primary_nodes" {
  name       = "flink-node-pool"
  location   = "us-central1"
  cluster    = google_container_cluster.primary.name
  node_count = 3

  autoscaling {
    min_node_count = 2
    max_node_count = 10
  }

  node_config {
    preemptible  = true  # Cost optimization
    machine_type = "n1-standard-2"

    labels = {
      role = "flink"
    }

    tags = ["flink-ctr"]

    metadata = {
      disable-legacy-endpoints = "true"
    }

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
  }
}

# GCS bucket for checkpoints
resource "google_storage_bucket" "checkpoints" {
  name          = "flink-ctr-checkpoints-${random_id.suffix.hex}"
  location      = "US"
  force_destroy = false

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }
}
```

**비용 최적화 팁**:
- Spot instances 사용 (AWS) / Preemptible VMs (GCP)
- 적절한 node size (t3.large 또는 n1-standard-2)
- Auto-scaling 설정
- 개발 환경은 퇴근 후 자동 shutdown

**예상 비용** (월):
- EKS 클러스터: $73
- EC2 instances (3x t3.large spot): ~$50-70
- S3/EBS: ~$20
- **Total: ~$150-170/month**

**예상 시간**: 8시간 (Terraform 학습 포함)
**완료 시**: 클러스터 ready

---

#### Task 3.3: 배포 및 검증

**배포 스크립트** (`scripts/deploy-k8s.sh`):

```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-dev}
NAMESPACE=${2:-flink-ctr}

echo "=== Deploying Flink CTR to Kubernetes ==="
echo "Environment: $ENVIRONMENT"
echo "Namespace: $NAMESPACE"

# Create namespace if not exists
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Create secrets (from environment or vault)
kubectl create secret generic kafka-credentials \
  --from-literal=username=$KAFKA_USERNAME \
  --from-literal=password=$KAFKA_PASSWORD \
  --namespace=$NAMESPACE \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic clickhouse-credentials \
  --from-literal=password=$CLICKHOUSE_PASSWORD \
  --namespace=$NAMESPACE \
  --dry-run=client -o yaml | kubectl apply -f -

# Deploy using Helm
helm upgrade --install flink-ctr ./k8s/helm/flink-ctr \
  --namespace=$NAMESPACE \
  --values=./k8s/helm/flink-ctr/values-${ENVIRONMENT}.yaml \
  --wait \
  --timeout=5m

echo ""
echo "=== Deployment Complete ==="
kubectl get pods -n $NAMESPACE

echo ""
echo "=== Access Flink UI ==="
echo "kubectl port-forward -n $NAMESPACE svc/flink-ctr-jobmanager 8081:8081"

echo ""
echo "=== View Logs ==="
echo "kubectl logs -n $NAMESPACE -l component=jobmanager --tail=100 -f"
```

**검증 스크립트** (`scripts/verify-deployment.sh`):

```bash
#!/bin/bash

NAMESPACE=${1:-flink-ctr}

echo "=== Verifying Deployment ==="

# Check pods
echo "Checking pods..."
kubectl get pods -n $NAMESPACE

# Check if jobmanager is running
JM_POD=$(kubectl get pods -n $NAMESPACE -l component=jobmanager -o jsonpath='{.items[0].metadata.name}')
if [ -z "$JM_POD" ]; then
  echo "❌ JobManager pod not found"
  exit 1
fi

echo "✅ JobManager pod: $JM_POD"

# Check if job is running
echo "Checking Flink job status..."
kubectl exec -n $NAMESPACE $JM_POD -- curl -s http://localhost:8081/jobs | jq '.jobs[] | select(.status=="RUNNING")'

# Check metrics
echo "Checking metrics endpoint..."
kubectl exec -n $NAMESPACE $JM_POD -- curl -s http://localhost:9249/metrics | head -20

echo ""
echo "=== Verification Complete ==="
```

**예상 시간**: 4시간
**완료 시**: K8s에서 실행 중

---

### Week 8-9: Production Operations

#### Task 3.4: 실제 트래픽 생성 (AWS/GCP에서)

**Producer Deployment** (`k8s/producers/deployment.yaml`):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: impression-producer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: impression-producer
  template:
    metadata:
      labels:
        app: impression-producer
    spec:
      containers:
        - name: producer
          image: python:3.11-slim
          command: ["python", "/app/impression_producer.py"]
          env:
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: "kafka-broker:9092"
            - name: EVENTS_PER_SECOND
              value: "5000"  # 각 replica당 5K, 총 10K
          volumeMounts:
            - name: producer-code
              mountPath: /app
      volumes:
        - name: producer-code
          configMap:
            name: producer-code

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: click-producer
spec:
  replicas: 1
  selector:
    matchLabels:
      app: click-producer
  template:
    metadata:
      labels:
        app: click-producer
    spec:
      containers:
        - name: producer
          image: python:3.11-slim
          command: ["python", "/app/click_producer.py"]
          env:
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: "kafka-broker:9092"
            - name: EVENTS_PER_SECOND
              value: "2500"  # CTR 20% 가정
          volumeMounts:
            - name: producer-code
              mountPath: /app
      volumes:
        - name: producer-code
          configMap:
            name: producer-code
```

**실제 트래픽 시뮬레이션**:
- 10K events/s → 30분 = 1,800만 이벤트
- 100K events/s → 1시간 = 3억 6천만 이벤트

**예상 시간**: 6시간
**완료 시**: 실제 부하 테스트 가능

---

#### Task 3.5: Chaos Engineering (실전)

**Chaos Mesh 설치**:

```bash
helm repo add chaos-mesh https://charts.chaos-mesh.org
helm install chaos-mesh chaos-mesh/chaos-mesh --namespace=chaos-mesh --create-namespace

# Web UI
kubectl port-forward -n chaos-mesh svc/chaos-dashboard 2333:2333
```

**Experiment 1: Pod Kill**:

```yaml
# chaos/pod-kill.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: taskmanager-pod-kill
  namespace: flink-ctr
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - flink-ctr
    labelSelectors:
      'component': 'taskmanager'
  scheduler:
    cron: '@every 10m'
```

**Experiment 2: Network Delay**:

```yaml
# chaos/network-delay.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: kafka-network-delay
  namespace: kafka
spec:
  action: delay
  mode: all
  selector:
    namespaces:
      - kafka
    labelSelectors:
      'app': 'kafka'
  delay:
    latency: "500ms"
    jitter: "100ms"
  duration: "5m"
```

**결과 문서화** (`docs/CHAOS_EXPERIMENTS_PROD.md`):

```markdown
# Chaos Engineering - Production Environment

## Experiment 1: TaskManager Pod Kill

**Date**: 2025-01-15
**Duration**: 1 hour

**Hypothesis**: Job recovers within 60 seconds with no data loss

**Procedure**:
1. Kill 1 TaskManager pod every 10 minutes
2. Monitor job status, checkpoint recovery
3. Verify data in ClickHouse

**Results**:
- Average recovery time: 38 seconds
- Data loss: 0 events
- Checkpoint recovery: Success (from S3)
- Cost impact: None (spare capacity available)

**Conclusion**: ✅ System is resilient to single pod failure

---

## Experiment 2: Kafka Network Latency

**Date**: 2025-01-16

**Hypothesis**: Backpressure handles network delay gracefully

**Procedure**:
1. Inject 500ms latency to Kafka
2. Monitor backpressure metrics
3. Check processing lag

**Results**:
- Backpressure: 60% (high but stable)
- Processing lag: Increased to 8 seconds (from 3s)
- No data loss
- Recovery: 2 minutes after latency removed

**Conclusion**: ✅ Backpressure mechanism works as expected

---

## Lessons Learned

1. **Checkpoint S3 latency matters**: Average checkpoint time increased from 3s to 5s on AWS
2. **Auto-scaling works**: HPA scaled from 4 to 7 TaskManagers during peak
3. **Monitoring is critical**: Without Grafana, we wouldn't have noticed the recovery
```

**예상 시간**: 8시간
**완료 시**: 실제 장애 대응 경험

---

### Phase 3 완료 기준

- [ ] Helm chart 완성 (10+ templates)
- [ ] AWS EKS 또는 GCP GKE 클러스터 생성
- [ ] 실제 배포 성공
- [ ] S3/GCS에 checkpoint 저장 확인
- [ ] 실제 트래픽 10K+ events/s 처리
- [ ] HPA auto-scaling 동작 확인
- [ ] Chaos engineering 3+ experiments
- [ ] 장애 대응 경험 문서화

**예상 시간**: 60시간 (주말 4-5주)

**예상 비용**: $150-200/month (AWS/GCP)

**완료 시 포트폴리오**:
- ✅ "AWS EKS에 실제 배포 및 운영"
- ✅ "초당 10만 이벤트 실전 처리"
- ✅ "Kubernetes auto-scaling 구현"
- ✅ "Chaos engineering으로 장애 대응"
- ✅ "월 $200 이하 비용 최적화"

---

## Phase 4: Real-world Operation (4주+)

**목표**: 1개월 이상 실제 운영
**완료 조건**: 99.9% uptime, 장애 대응, 비용 최적화

### Week 10-13: Continuous Operation

#### Task 4.1: 24/7 모니터링 및 알림

**AlertManager 설정**:

```yaml
# alertmanager/config.yml
global:
  resolve_timeout: 5m
  slack_api_url: 'YOUR_SLACK_WEBHOOK'

route:
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'slack-notifications'

  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true

    - match:
        severity: warning
      receiver: 'slack-notifications'

receivers:
  - name: 'slack-notifications'
    slack_configs:
      - channel: '#flink-alerts'
        title: '{{ .CommonAnnotations.summary }}'
        text: '{{ .CommonAnnotations.description }}'
        send_resolved: true

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_KEY'
        description: '{{ .CommonAnnotations.summary }}'
```

**PagerDuty 무료 대안**: Uptime Robot + Email

**예상 시간**: 4시간

---

#### Task 4.2: Cost Optimization

**비용 분석** (AWS):

```bash
# 현재 비용 확인
aws ce get-cost-and-usage \
  --time-period Start=2025-01-01,End=2025-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --group-by Type=SERVICE

# 상세 분석
kubectl top nodes
kubectl top pods -n flink-ctr
```

**최적화 전략**:

1. **Spot Instances 사용** (70% 비용 절감)
   ```hcl
   capacity_type = "SPOT"
   ```

2. **Right-sizing**
   - TaskManager: 4Gi → 2Gi (메트릭 기반)
   - JobManager: 2Gi → 1Gi

3. **개발 환경 자동 shutdown**
   ```yaml
   # k8s/dev/cronjob-shutdown.yaml
   apiVersion: batch/v1
   kind: CronJob
   metadata:
     name: shutdown-dev
   spec:
     schedule: "0 19 * * 1-5"  # 평일 19시
     jobTemplate:
       spec:
         template:
           spec:
             containers:
               - name: shutdown
                 image: bitnami/kubectl
                 command:
                   - /bin/sh
                   - -c
                   - kubectl scale deployment --all --replicas=0 -n flink-ctr-dev
   ```

4. **S3 Lifecycle Policy**
   ```hcl
   resource "aws_s3_bucket_lifecycle_configuration" "checkpoints" {
     bucket = aws_s3_bucket.checkpoints.id

     rule {
       id     = "cleanup-old-checkpoints"
       status = "Enabled"

       expiration {
         days = 7
       }
     }
   }
   ```

**목표 비용**:
- Before: $200/month
- After: $150/month (25% 절감)

**예상 시간**: 6시간

---

#### Task 4.3: 운영 일지 작성

**템플릿** (`docs/OPERATIONS_LOG.md`):

```markdown
# Operations Log

## Week 1 (2025-01-01 ~ 2025-01-07)

### Metrics
- Uptime: 99.8%
- Events processed: 60M
- Average throughput: 9,500 events/s
- Peak throughput: 15,000 events/s
- Average latency (p99): 3.2s
- Checkpoint success rate: 100%
- Cost: $180

### Incidents
**2025-01-03 14:23 - High Backpressure**
- **Severity**: Warning
- **Duration**: 15 minutes
- **Root Cause**: Kafka broker restart
- **Resolution**: Auto-recovered after broker came back
- **Action Items**: None (expected behavior)

**2025-01-05 02:15 - TaskManager OOM**
- **Severity**: Critical
- **Duration**: 2 minutes
- **Root Cause**: Memory leak in custom deserializer
- **Resolution**: Pod restarted, recovered from checkpoint
- **Action Items**:
  - [x] Fixed memory leak in EventDeserializationSchema
  - [x] Added memory profiling
  - [ ] Increase heap size to 3Gi

### Optimizations
- Reduced TaskManager memory from 4Gi to 3Gi (metrics showed 60% usage)
- Enabled S3 lifecycle policy (save $10/month)

### Learnings
- Checkpoint recovery from S3 takes 5-8 seconds
- HPA scales up within 30 seconds under load
- Spot instance interruption rate: 2% (acceptable)

---

## Week 2 (2025-01-08 ~ 2025-01-14)

...
```

**예상 시간**: 2시간/week

---

#### Task 4.4: 성능 튜닝 (실전)

**Before/After 비교**:

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Throughput | 10K/s | 50K/s | 5x |
| p99 Latency | 5s | 3s | 40% |
| Checkpoint Time | 8s | 4s | 50% |
| Memory Usage | 80% | 60% | 25% |
| Cost/M events | $3 | $1.2 | 60% |

**적용한 최적화**:

1. **RocksDB Tuning**
   ```yaml
   state.backend.rocksdb.predefined-options: SPINNING_DISK_OPTIMIZED
   state.backend.rocksdb.block.cache-size: 256mb
   ```

2. **Network Buffer**
   ```yaml
   taskmanager.network.memory.fraction: 0.2
   taskmanager.network.memory.max: 1gb
   ```

3. **Operator Chaining**
   ```kotlin
   // 불필요한 disableChaining 제거
   .filter(Event::isValid)
   .name("Validate Events")
   // .disableChaining()  ← 제거
   ```

4. **ClickHouse Batch Size**
   ```yaml
   clickhouse.batch-size: 5000  # 1000 → 5000
   clickhouse.batch-interval-ms: 500  # 200 → 500
   ```

**문서화** (`docs/PERFORMANCE_TUNING.md`)

**예상 시간**: 12시간

---

### Phase 4 완료 기준

- [ ] 1개월 이상 실제 운영
- [ ] 99.9% uptime 달성
- [ ] 장애 3회 이상 경험 및 해결
- [ ] 비용 $150 이하로 최적화
- [ ] 성능 튜닝으로 2x 이상 개선
- [ ] 운영 일지 4주분 작성
- [ ] 알림 시스템 동작 (Slack/PagerDuty)

**예상 시간**: 40시간 (주말 4주 + 평일 모니터링)

**완료 시 포트폴리오**:
- ✅ "1개월 실제 운영 (99.9% uptime)"
- ✅ "장애 대응 3회 (평균 복구시간 5분)"
- ✅ "비용 25% 절감 ($200 → $150)"
- ✅ "성능 튜닝으로 5배 개선"

---

## 전체 완성 기준

### Technical Excellence

- [x] **코드 품질**
  - [ ] 테스트 커버리지 70%+
  - [ ] CI/CD 파이프라인
  - [ ] Linting/Formatting 통과
  - [ ] 보안 스캔 통과

- [x] **아키텍처**
  - [ ] DDD 레이어 분리
  - [ ] SOLID 원칙 준수
  - [ ] 확장 가능한 설계
  - [ ] 문서화된 ADR

- [x] **테스트**
  - [ ] Unit tests 30+ cases
  - [ ] Integration tests 5+ scenarios
  - [ ] Load testing (10K → 100K events/s)
  - [ ] Chaos engineering experiments

### Operational Excellence

- [x] **배포**
  - [ ] Kubernetes Helm chart
  - [ ] Blue-green deployment
  - [ ] Automated rollback
  - [ ] Zero-downtime deployment

- [x] **모니터링**
  - [ ] Prometheus metrics (20+)
  - [ ] Grafana dashboards (6 rows)
  - [ ] Alerts (10+)
  - [ ] Logging (structured JSON)

- [x] **보안**
  - [ ] Authentication (Kafka SASL)
  - [ ] Secrets management
  - [ ] Network policies
  - [ ] TLS encryption

- [x] **운영**
  - [ ] Runbook
  - [ ] Troubleshooting guide
  - [ ] On-call playbook
  - [ ] Incident post-mortems

### Real-world Experience

- [x] **성능**
  - [ ] 초당 10만 이벤트 처리
  - [ ] p99 latency < 5초
  - [ ] 99.9% uptime
  - [ ] Auto-scaling 검증

- [x] **비용**
  - [ ] 월 $150 이하
  - [ ] 비용 대비 효율 최적화
  - [ ] 리소스 right-sizing

- [x] **장애 대응**
  - [ ] 3회 이상 장애 경험
  - [ ] 평균 복구시간 < 10분
  - [ ] 데이터 손실 0건
  - [ ] Post-mortem 문서

---

## 이력서에 쓸 수 있는 것

### Project: Real-time CTR Calculator
**기간**: 2025.01 - 2025.03 (3개월)
**역할**: Data Engineer (Individual Project)

**주요 성과**:
- Apache Flink 기반 실시간 스트리밍 파이프라인 설계 및 구축
- **초당 10만 이벤트 처리** (p99 latency < 5초)
- **99.9% uptime** 달성 (1개월 운영)
- Kubernetes (AWS EKS) 기반 배포 및 운영
- **비용 최적화** 25% 달성 (월 $200 → $150)
- Chaos Engineering으로 시스템 resilience 검증

**기술 스택**:
- **Stream Processing**: Apache Flink 1.18, Kotlin
- **Infrastructure**: Kubernetes, Helm, Terraform
- **Data**: Kafka, ClickHouse
- **Monitoring**: Prometheus, Grafana, AlertManager
- **CI/CD**: GitHub Actions, Docker
- **Cloud**: AWS EKS, S3

**주요 기술**:
- DDD 아키텍처 적용 (도메인-애플리케이션-인프라 분리)
- EXACTLY_ONCE semantics로 데이터 정확성 보장
- Testcontainers로 end-to-end 검증 (테스트 커버리지 70%)
- Custom Prometheus metrics 20개 구현
- HPA (Horizontal Pod Autoscaler)로 auto-scaling
- RocksDB state backend 튜닝으로 성능 5배 개선

**장애 대응**:
- TaskManager OOM 해결 (메모리 프로파일링)
- Kafka network latency 대응 (backpressure 튜닝)
- Pod failure 복구 (평균 복구시간 5분)

**문서화**:
- ADR (Architecture Decision Records) 3개
- Runbook, Troubleshooting guide
- Operations log (4주분)

---

## 다음 단계 (선택사항)

프로젝트 완성 후 추가 확장:

1. **ML Integration**
   - CTR prediction model
   - Anomaly detection

2. **Advanced Features**
   - Session windowing
   - Side outputs
   - Custom state

3. **Multi-region**
   - Global deployment
   - Cross-region replication

4. **Enterprise Features**
   - Multi-tenancy
   - Data lineage
   - GDPR compliance

---

## 지금 시작하세요!

### Week 1 Task (이번 주):

```bash
# Day 1: 테스트 고치기
cd flink-app
vim build.gradle.kts  # JUnit 버전 명시
./gradlew clean test

# Day 2-3: GitHub Actions CI
mkdir -p .github/workflows
vim .github/workflows/ci.yml
git add .
git commit -m "ci: add GitHub Actions workflow"
git push

# Day 4-5: 보안 설정
vim flink-app/src/main/kotlin/com/example/ctr/config/KafkaProperties.kt
# Security 설정 추가

# Weekend: Integration test 작성
vim flink-app/src/test/kotlin/com/example/ctr/integration/CtrPipelineE2ETest.kt
```

**3개월 후, 이력서에 이렇게 쓸 수 있습니다:**

> "Apache Flink 기반 실시간 데이터 파이프라인을 설계하고 AWS EKS에 배포하여 1개월간 운영했습니다.
> 초당 10만 이벤트를 처리하며 99.9% uptime을 달성했고,
> 비용 최적화를 통해 월 운영비를 25% 절감했습니다."

**그리고 면접에서 이렇게 말할 수 있습니다:**

> "실제로 AWS에서 한 달 동안 돌려봤는데요,
> TaskManager가 죽었을 때 38초 안에 자동으로 복구되는 걸 확인했습니다.
> Grafana 대시보드 보여드릴까요?"

**바로 이겁니다! 🚀**

**지금 시작하세요!**
