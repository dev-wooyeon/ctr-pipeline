# Flink App Java to Kotlin 마이그레이션 가이드

## 📖 개요

이 가이드는 flink-app을 Java에서 Kotlin으로 마이그레이션하는 구체적인 방법을 설명합니다.

**마이그레이션 전략:**
- 점진적 마이그레이션 (Java/Kotlin 혼용)
- Domain Layer부터 시작하여 외부로 확장
- 각 단계마다 테스트 실행으로 안정성 확보

---

## 🔧 Phase 1: Gradle 설정

### 1.1 build.gradle.kts 변환

**기존 build.gradle 삭제 후 build.gradle.kts 생성:**

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

val flinkVersion = "1.18.1"
val jacksonVersion = "2.15.2"

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Flink
    implementation("org.apache.flink:flink-java:$flinkVersion")
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-clients:$flinkVersion")

    // Connectors
    implementation("org.apache.flink:flink-connector-kafka:3.0.2-1.18")
    implementation("org.apache.flink:flink-connector-jdbc:3.1.2-1.18")

    // Drivers
    implementation("com.clickhouse:clickhouse-jdbc:0.4.6")
    implementation("org.duckdb:duckdb_jdbc:0.9.2")
    // redis.clients:jedis 제거 (Redis 제거에 따라)

    // Utils
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")

    // Jakarta Annotations
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")

    // Metrics
    implementation("org.apache.flink:flink-metrics-prometheus:$flinkVersion")

    // Testing
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")

    mergeServiceFiles()

    manifest {
        attributes["Main-Class"] = "com.example.ctr.CtrApplicationKt"
    }

    dependencies {
        // Flink 런타임 제공 라이브러리 제외
        exclude(dependency("org.apache.flink:flink-java:.*"))
        exclude(dependency("org.apache.flink:flink-streaming-java:.*"))
        exclude(dependency("org.apache.flink:flink-clients:.*"))
        exclude(dependency("org.apache.flink:flink-core:.*"))
        exclude(dependency("org.apache.flink:flink-runtime:.*"))
        exclude(dependency("org.apache.flink:flink-optimizer:.*"))
        exclude(dependency("org.apache.flink:flink-annotations:.*"))
        exclude(dependency("org.apache.flink:flink-queryable-state-client-java:.*"))
        exclude(dependency("org.apache.flink:flink-shaded-.*:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("log4j:.*:.*"))
        exclude(dependency("ch.qos.logback:.*:.*"))
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.ctr.CtrApplicationKt"
    }
}

// Kotlin 컴파일 옵션
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}
```

### 1.2 settings.gradle.kts

```kotlin
rootProject.name = "flink-ctr-app"
```

### 1.3 디렉토리 구조 조정

```bash
# Kotlin 소스 디렉토리 생성
mkdir -p src/main/kotlin/com/example/ctr
mkdir -p src/test/kotlin/com/example/ctr

# 점진적 마이그레이션을 위해 Java 디렉토리는 유지
# src/main/java/com/example/ctr (기존 유지)
```

---

## 📦 Phase 2: Domain Layer 마이그레이션

Domain Layer는 외부 의존성이 없는 순수 비즈니스 로직이므로 가장 먼저 마이그레이션합니다.

### 2.1 Event.java → Event.kt

**Before (Java):**
```java
package com.example.ctr.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    public boolean isValid() {
        return eventType != null && productId != null && timestamp != null;
    }

    public boolean hasProductId() {
        return productId != null && !productId.isEmpty();
    }

    public long eventTimeMillisUtc() {
        return timestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.time.ZoneOffset

data class Event(
    @JsonProperty("event_type")
    val eventType: String?,

    @JsonProperty("product_id")
    val productId: String?,

    @JsonProperty("timestamp")
    val timestamp: LocalDateTime?
) {
    fun isValid(): Boolean =
        eventType != null && productId != null && timestamp != null

    fun hasProductId(): Boolean =
        !productId.isNullOrEmpty()

    fun eventTimeMillisUtc(): Long =
        timestamp?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: 0L
}
```

**주요 변경사항:**
- `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` → `data class`
- `private` 필드 → `val` (불변성)
- null 가능 타입 명시 (`String?`)
- 메서드 → 함수 (간결한 표현식)

### 2.2 EventCount.java → EventCount.kt

**Before (Java):**
```java
package com.example.ctr.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventCount {
    private long impressions;
    private long clicks;

    public void addImpression() {
        this.impressions++;
    }

    public void addClick() {
        this.clicks++;
    }

    public EventCount merge(EventCount other) {
        return new EventCount(
            this.impressions + other.impressions,
            this.clicks + other.clicks
        );
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.domain.model

data class EventCount(
    var impressions: Long = 0L,
    var clicks: Long = 0L
) {
    fun addImpression() {
        impressions++
    }

    fun addClick() {
        clicks++
    }

    fun merge(other: EventCount): EventCount =
        EventCount(
            impressions = this.impressions + other.impressions,
            clicks = this.clicks + other.clicks
        )

    companion object {
        fun empty(): EventCount = EventCount(0L, 0L)
    }
}
```

**주요 변경사항:**
- 기본값 제공 (`= 0L`)
- `var` 사용 (가변 필드)
- `companion object`로 팩토리 메서드 추가
- Named arguments 활용

### 2.3 CTRResult.java → CTRResult.kt

**Before (Java):**
```java
package com.example.ctr.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CTRResult {
    @JsonProperty("product_id")
    private String productId;

    private double ctr;
    private long impressions;
    private long clicks;

    @JsonProperty("window_start")
    private long windowStart;

    @JsonProperty("window_end")
    private long windowEnd;

    public static CTRResult from(String productId, EventCount count,
                                  long windowStart, long windowEnd) {
        double ctr = count.getImpressions() > 0
            ? (double) count.getClicks() / count.getImpressions()
            : 0.0;

        return new CTRResult(
            productId,
            ctr,
            count.getImpressions(),
            count.getClicks(),
            windowStart,
            windowEnd
        );
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

data class CTRResult(
    @JsonProperty("product_id")
    val productId: String,

    val ctr: Double,
    val impressions: Long,
    val clicks: Long,

    @JsonProperty("window_start")
    val windowStart: Long,

    @JsonProperty("window_end")
    val windowEnd: Long
) {
    companion object {
        fun from(
            productId: String,
            count: EventCount,
            windowStart: Long,
            windowEnd: Long
        ): CTRResult {
            val ctr = if (count.impressions > 0) {
                count.clicks.toDouble() / count.impressions
            } else {
                0.0
            }

            return CTRResult(
                productId = productId,
                ctr = ctr,
                impressions = count.impressions,
                clicks = count.clicks,
                windowStart = windowStart,
                windowEnd = windowEnd
            )
        }
    }
}
```

**주요 변경사항:**
- Static factory method → `companion object`
- Ternary operator → `if` expression
- Named arguments로 가독성 향상

### 2.4 EventCountAggregator.kt

**Before (Java):**
```java
package com.example.ctr.domain.service;

import com.example.ctr.domain.model.Event;
import com.example.ctr.domain.model.EventCount;
import org.apache.flink.api.common.functions.AggregateFunction;

public class EventCountAggregator
    implements AggregateFunction<Event, EventCount, EventCount> {

    @Override
    public EventCount createAccumulator() {
        return new EventCount(0L, 0L);
    }

    @Override
    public EventCount add(Event event, EventCount accumulator) {
        if ("impression".equalsIgnoreCase(event.getEventType())) {
            accumulator.addImpression();
        } else if ("click".equalsIgnoreCase(event.getEventType())) {
            accumulator.addClick();
        }
        return accumulator;
    }

    @Override
    public EventCount getResult(EventCount accumulator) {
        return accumulator;
    }

    @Override
    public EventCount merge(EventCount a, EventCount b) {
        return a.merge(b);
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.domain.service

import com.example.ctr.domain.model.Event
import com.example.ctr.domain.model.EventCount
import org.apache.flink.api.common.functions.AggregateFunction

class EventCountAggregator : AggregateFunction<Event, EventCount, EventCount> {

    override fun createAccumulator(): EventCount =
        EventCount.empty()

    override fun add(event: Event, accumulator: EventCount): EventCount {
        when (event.eventType?.lowercase()) {
            "impression" -> accumulator.addImpression()
            "click" -> accumulator.addClick()
        }
        return accumulator
    }

    override fun getResult(accumulator: EventCount): EventCount =
        accumulator

    override fun merge(a: EventCount, b: EventCount): EventCount =
        a.merge(b)
}
```

**주요 변경사항:**
- `implements` → `:` (상속 구문)
- `if-else` → `when` (패턴 매칭)
- `equalsIgnoreCase` → `lowercase()`
- Safe call operator (`?.`)

### 2.5 CTRResultWindowProcessFunction.kt

**Before (Java):**
```java
package com.example.ctr.domain.service;

import com.example.ctr.domain.model.CTRResult;
import com.example.ctr.domain.model.EventCount;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class CTRResultWindowProcessFunction
    extends ProcessWindowFunction<EventCount, CTRResult, String, TimeWindow> {

    @Override
    public void process(String productId,
                       Context context,
                       Iterable<EventCount> elements,
                       Collector<CTRResult> out) {
        EventCount count = elements.iterator().next();
        TimeWindow window = context.window();

        CTRResult result = CTRResult.from(
            productId,
            count,
            window.getStart(),
            window.getEnd()
        );

        out.collect(result);
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.domain.service

import com.example.ctr.domain.model.CTRResult
import com.example.ctr.domain.model.EventCount
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

class CTRResultWindowProcessFunction :
    ProcessWindowFunction<EventCount, CTRResult, String, TimeWindow>() {

    override fun process(
        productId: String,
        context: Context,
        elements: Iterable<EventCount>,
        out: Collector<CTRResult>
    ) {
        val count = elements.first()
        val window = context.window()

        val result = CTRResult.from(
            productId = productId,
            count = count,
            windowStart = window.start,
            windowEnd = window.end
        )

        out.collect(result)
    }
}
```

**주요 변경사항:**
- `extends` → `:`
- `iterator().next()` → `first()`
- `getStart()` → `start` (프로퍼티)
- Named arguments

---

## 🏗️ Phase 3: Infrastructure Layer 마이그레이션

### 3.1 Config 패키지

#### KafkaProperties.kt

**Before (Java):**
```java
package com.example.ctr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KafkaProperties {
    @JsonProperty("bootstrap-servers")
    private String bootstrapServers;

    @JsonProperty("group-id")
    private String groupId;
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.config

import com.fasterxml.jackson.annotation.JsonProperty

data class KafkaProperties(
    @JsonProperty("bootstrap-servers")
    val bootstrapServers: String,

    @JsonProperty("group-id")
    val groupId: String
)
```

#### CtrJobProperties.kt

**Before (Java):**
```java
package com.example.ctr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CtrJobProperties {
    @JsonProperty("impression-topic")
    private String impressionTopic;

    @JsonProperty("click-topic")
    private String clickTopic;

    @JsonProperty("group-id")
    private String groupId;

    @JsonProperty("parallelism")
    private int parallelism = 2;

    @JsonProperty("checkpoint-interval-ms")
    private long checkpointIntervalMs = 10000L;

    // ... other properties
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.config

import com.fasterxml.jackson.annotation.JsonProperty

data class CtrJobProperties(
    @JsonProperty("impression-topic")
    val impressionTopic: String,

    @JsonProperty("click-topic")
    val clickTopic: String,

    @JsonProperty("group-id")
    val groupId: String,

    @JsonProperty("parallelism")
    val parallelism: Int = 2,

    @JsonProperty("checkpoint-interval-ms")
    val checkpointIntervalMs: Long = 10000L

    // ... other properties
)
```

### 3.2 Flink Source/Sink

#### KafkaSourceFactory.kt

**Before (Java):**
```java
package com.example.ctr.infrastructure.flink.source;

import com.example.ctr.config.KafkaProperties;
import com.example.ctr.domain.model.Event;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;

public class KafkaSourceFactory {
    private final KafkaProperties properties;

    public KafkaSourceFactory(KafkaProperties properties) {
        this.properties = properties;
    }

    public KafkaSource<Event> createSource(String topic, String groupId) {
        return KafkaSource.<Event>builder()
            .setBootstrapServers(properties.getBootstrapServers())
            .setTopics(topic)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new EventDeserializationSchema())
            .build();
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.infrastructure.flink.source

import com.example.ctr.config.KafkaProperties
import com.example.ctr.domain.model.Event
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer

class KafkaSourceFactory(
    private val properties: KafkaProperties
) {
    fun createSource(topic: String, groupId: String): KafkaSource<Event> =
        KafkaSource.builder<Event>()
            .setBootstrapServers(properties.bootstrapServers)
            .setTopics(topic)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(EventDeserializationSchema())
            .build()
}
```

#### ClickHouseSink.kt

**Before (Java):**
```java
package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.config.ClickHouseProperties;
import com.example.ctr.domain.model.CTRResult;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.PreparedStatement;

public class ClickHouseSink {
    private final ClickHouseProperties properties;

    public ClickHouseSink(ClickHouseProperties properties) {
        this.properties = properties;
    }

    public SinkFunction<CTRResult> createSink() {
        return JdbcSink.sink(
            "INSERT INTO ctr_results_raw (product_id, ctr, impressions, clicks, window_start, window_end) VALUES (?, ?, ?, ?, ?, ?)",
            (JdbcStatementBuilder<CTRResult>) (ps, result) -> {
                ps.setString(1, result.getProductId());
                ps.setDouble(2, result.getCtr());
                ps.setLong(3, result.getImpressions());
                ps.setLong(4, result.getClicks());
                ps.setLong(5, result.getWindowStart());
                ps.setLong(6, result.getWindowEnd());
            },
            JdbcExecutionOptions.builder()
                .withBatchSize(100)
                .withBatchIntervalMs(5000)
                .withMaxRetries(3)
                .build(),
            new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl(properties.getUrl())
                .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
                .build()
        );
    }
}
```

**After (Kotlin):**
```kotlin
package com.example.ctr.infrastructure.flink.sink

import com.example.ctr.config.ClickHouseProperties
import com.example.ctr.domain.model.CTRResult
import org.apache.flink.connector.jdbc.JdbcConnectionOptions
import org.apache.flink.connector.jdbc.JdbcExecutionOptions
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction

class ClickHouseSink(
    private val properties: ClickHouseProperties
) {
    fun createSink(): SinkFunction<CTRResult> =
        JdbcSink.sink(
            """
            INSERT INTO ctr_results_raw
            (product_id, ctr, impressions, clicks, window_start, window_end)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            { ps, result ->
                ps.setString(1, result.productId)
                ps.setDouble(2, result.ctr)
                ps.setLong(3, result.impressions)
                ps.setLong(4, result.clicks)
                ps.setLong(5, result.windowStart)
                ps.setLong(6, result.windowEnd)
            },
            JdbcExecutionOptions.builder()
                .withBatchSize(100)
                .withBatchIntervalMs(5000)
                .withMaxRetries(3)
                .build(),
            JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl(properties.url)
                .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
                .build()
        )
}
```

**주요 변경사항:**
- Lambda 타입 추론 (JdbcStatementBuilder 제거)
- Multi-line string (""" """)
- Property access (getter 제거)

### 3.3 CtrJobPipelineBuilder.kt (체이닝 강화)

**After (Kotlin with DSL):**
```kotlin
package com.example.ctr.infrastructure.flink

import com.example.ctr.config.CtrJobProperties
import com.example.ctr.domain.model.CTRResult
import com.example.ctr.domain.model.Event
import com.example.ctr.domain.service.CTRResultWindowProcessFunction
import com.example.ctr.domain.service.EventCountAggregator
import com.example.ctr.infrastructure.flink.sink.ClickHouseSink
import com.example.ctr.infrastructure.flink.sink.DuckDBSink
import com.example.ctr.infrastructure.flink.source.KafkaSourceFactory
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import java.time.Duration

class CtrJobPipelineBuilder(
    private val kafkaSourceFactory: KafkaSourceFactory,
    private val clickHouseSink: ClickHouseSink,
    private val duckDBSink: DuckDBSink,
    private val aggregator: EventCountAggregator,
    private val windowFunction: CTRResultWindowProcessFunction,
    private val properties: CtrJobProperties
) {
    fun build(env: StreamExecutionEnvironment): DataStream<CTRResult> {
        // Source Pipeline with Chaining
        val impressionStream = env
            .fromKafkaSource(
                topic = properties.impressionTopic,
                groupId = properties.groupId,
                name = "Impression Kafka Source",
                uid = "impression-source",
                slotSharingGroup = "source-group"
            )
            .filterValidEvents("Impression")

        val clickStream = env
            .fromKafkaSource(
                topic = properties.clickTopic,
                groupId = properties.groupId,
                name = "Click Kafka Source",
                uid = "click-source",
                slotSharingGroup = "source-group"
            )
            .filterValidEvents("Click")

        // Aggregation Pipeline
        val ctrResults = impressionStream
            .union(clickStream)
            .filter { it.hasProductId() }
            .name("Filter by ProductId")
            .uid("filter-product-id")
            .slotSharingGroup("processing-group")
            .keyBy { it.productId!! }
            .window(TumblingEventTimeWindows.of(Time.seconds(10)))
            .allowedLateness(Time.seconds(5))
            .aggregate(aggregator, windowFunction)
            .name("CTR Aggregation")
            .uid("ctr-aggregation")

        // Sinks (독립 실행)
        ctrResults.sinkToClickHouse()
        ctrResults.sinkToDuckDB()

        return ctrResults
    }

    // Extension functions for DSL-style chaining
    private fun StreamExecutionEnvironment.fromKafkaSource(
        topic: String,
        groupId: String,
        name: String,
        uid: String,
        slotSharingGroup: String
    ) = fromSource(
        kafkaSourceFactory.createSource(topic, groupId),
        WatermarkStrategy.forBoundedOutOfOrderness<Event>(Duration.ofSeconds(5))
            .withTimestampAssigner { event, _ -> event.eventTimeMillisUtc() },
        name
    )
        .uid(uid)
        .name(name)
        .slotSharingGroup(slotSharingGroup)

    private fun DataStream<Event>.filterValidEvents(streamType: String) =
        this.filter { it != null }
            .name("Filter Null $streamType")
            .uid("filter-null-${streamType.lowercase()}")
            .filter { it.isValid() }
            .name("Validate $streamType")
            .uid("validate-${streamType.lowercase()}")

    private fun DataStream<CTRResult>.sinkToClickHouse() {
        this.addSink(clickHouseSink.createSink())
            .name("ClickHouse Sink")
            .uid("clickhouse-sink")
            .slotSharingGroup("sink-group")
            .disableChaining()
    }

    private fun DataStream<CTRResult>.sinkToDuckDB() {
        this.addSink(duckDBSink.createSink())
            .name("DuckDB Sink")
            .uid("duckdb-sink")
            .setParallelism(1)
            .slotSharingGroup("sink-group")
            .disableChaining()
    }
}
```

**주요 개선사항:**
- Extension functions로 DSL 스타일 구현
- Named arguments로 가독성 향상
- 반복 코드 제거 (filterValidEvents)
- 체이닝 로직을 명확하게 분리

---

## 🧪 Phase 4: 테스트 마이그레이션

### 4.1 단위 테스트 (MockK 활용)

**Before (Java + Mockito):**
```java
@Test
void shouldCalculateCTRCorrectly() {
    EventCount count = new EventCount(100L, 10L);
    CTRResult result = CTRResult.from("product-1", count, 0L, 10000L);

    assertEquals(0.1, result.getCtr(), 0.001);
    assertEquals(100L, result.getImpressions());
    assertEquals(10L, result.getClicks());
}
```

**After (Kotlin + Kotest):**
```kotlin
@Test
fun `should calculate CTR correctly`() {
    val count = EventCount(impressions = 100L, clicks = 10L)
    val result = CTRResult.from("product-1", count, 0L, 10000L)

    result.ctr shouldBe 0.1.plusOrMinus(0.001)
    result.impressions shouldBe 100L
    result.clicks shouldBe 10L
}
```

**MockK 예시:**
```kotlin
@Test
fun `should create Kafka source with correct configuration`() {
    // Given
    val properties = KafkaProperties(
        bootstrapServers = "localhost:9092",
        groupId = "test-group"
    )
    val factory = KafkaSourceFactory(properties)

    // When
    val source = factory.createSource("test-topic", "test-group")

    // Then
    source shouldNotBe null
}
```

---

## 🚀 Phase 5: 빌드 및 배포

### 5.1 빌드

```bash
cd flink-app
./gradlew clean build shadowJar
```

### 5.2 테스트

```bash
./gradlew test
```

### 5.3 배포

```bash
cd ..
./scripts/setup.sh
```

---

## 📋 마이그레이션 체크리스트

### 코드 변환
- [ ] Domain models (Event, EventCount, CTRResult)
- [ ] Domain services (Aggregator, WindowFunction)
- [ ] Config classes (Properties)
- [ ] Infrastructure (Sources, Sinks)
- [ ] Application (CtrJobService, Main)
- [ ] Tests

### Gradle 설정
- [ ] build.gradle.kts 변환
- [ ] Kotlin 플러그인 추가
- [ ] Kotlin 의존성 추가
- [ ] shadowJar 설정 업데이트

### 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] E2E 테스트 통과

### 문서
- [ ] README 업데이트
- [ ] 코드 주석 업데이트
- [ ] 아키텍처 다이어그램 업데이트

---

## 💡 Kotlin 베스트 프랙티스

### 1. Null 안전성
```kotlin
// Bad
val name: String? = null
val length = name!!.length // 위험!

// Good
val length = name?.length ?: 0
```

### 2. Data Classes
```kotlin
// 불변 데이터는 data class + val
data class User(val id: String, val name: String)

// 가변 데이터는 var (필요시만)
data class Counter(var count: Int = 0)
```

### 3. Extension Functions
```kotlin
fun String.isValidProductId(): Boolean =
    this.isNotBlank() && this.length <= 50
```

### 4. Scope Functions
```kotlin
val result = EventCount().apply {
    addImpression()
    addClick()
}
```

### 5. When Expressions
```kotlin
when (eventType) {
    "impression" -> handleImpression()
    "click" -> handleClick()
    else -> logUnknown()
}
```

---

## 🔗 참고 자료

- [Kotlin Official Documentation](https://kotlinlang.org/docs/home.html)
- [Kotlin for Java Developers](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Flink with Kotlin](https://nightlies.apache.org/flink/flink-docs-release-1.18/)
- [MockK](https://mockk.io/)
- [Kotest](https://kotest.io/)
