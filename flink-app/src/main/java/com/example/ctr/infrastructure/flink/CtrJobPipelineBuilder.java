package com.example.ctr.infrastructure.flink;

import com.example.ctr.config.CtrJobProperties;
import com.example.ctr.domain.model.CTRResult;
import com.example.ctr.domain.model.Event;
import com.example.ctr.domain.service.CTRResultWindowProcessFunction;
import com.example.ctr.domain.service.EventCountAggregator;
import com.example.ctr.infrastructure.flink.sink.ClickHouseSink;
import com.example.ctr.infrastructure.flink.sink.DuckDBSink;
import com.example.ctr.infrastructure.flink.sink.RedisSink;
import com.example.ctr.infrastructure.flink.source.KafkaSourceFactory;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;
import java.util.Objects;

/**
 * Flink 데이터 파이프라인 빌더 (Operator Chaining 최적화 적용)
 * 
 * Operator Chaining 전략:
 * 1. Source -> Filter 체이닝: 소스와 필터 연산자를 하나의 태스크로 묶음
 * 2. Slot Sharing Group: 관련 연산자들을 같은 슬롯에서 실행
 * 3. 명시적 체이닝: startNewChain()과 disableChaining()으로 제어
 */
public class CtrJobPipelineBuilder {

        private final KafkaSourceFactory kafkaSourceFactory;
        private final RedisSink redisSink;
        private final DuckDBSink duckDBSink;
        private final ClickHouseSink clickHouseSink;
        private final EventCountAggregator aggregator;
        private final CTRResultWindowProcessFunction windowFunction;
        private final CtrJobProperties properties;

        public CtrJobPipelineBuilder(KafkaSourceFactory kafkaSourceFactory,
                        RedisSink redisSink,
                        DuckDBSink duckDBSink,
                        ClickHouseSink clickHouseSink,
                        EventCountAggregator aggregator,
                        CTRResultWindowProcessFunction windowFunction,
                        CtrJobProperties properties) {
                this.kafkaSourceFactory = kafkaSourceFactory;
                this.redisSink = redisSink;
                this.duckDBSink = duckDBSink;
                this.clickHouseSink = clickHouseSink;
                this.aggregator = aggregator;
                this.windowFunction = windowFunction;
                this.properties = properties;
        }

        public DataStream<CTRResult> build(StreamExecutionEnvironment env) {
                // ===== 1. Impression Source Pipeline (Chained) =====
                // Source -> Filter(nonNull) -> Filter(isValid) 를 하나의 체인으로 구성
                SingleOutputStreamOperator<Event> impressionStream = env.fromSource(
                                kafkaSourceFactory.createSource(properties.getImpressionTopic(),
                                                properties.getGroupId()),
                                WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                                .withTimestampAssigner((event, ts) -> event.eventTimeMillisUtc()),
                                "Impression Source")
                                .uid("impression-source")
                                .name("Impression Kafka Source")
                                .slotSharingGroup("source-group") // 소스들을 같은 슬롯 그룹에 배치
                                .filter(Objects::nonNull)
                                .name("Filter Null Impressions")
                                .uid("filter-null-impressions")
                                .filter(Event::isValid)
                                .name("Validate Impressions")
                                .uid("validate-impressions");

                // ===== 2. Click Source Pipeline (Chained) =====
                // Source -> Filter(nonNull) -> Filter(isValid) 를 하나의 체인으로 구성
                SingleOutputStreamOperator<Event> clickStream = env.fromSource(
                                kafkaSourceFactory.createSource(properties.getClickTopic(), properties.getGroupId()),
                                WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                                .withTimestampAssigner((event, ts) -> event.eventTimeMillisUtc()),
                                "Click Source")
                                .uid("click-source")
                                .name("Click Kafka Source")
                                .slotSharingGroup("source-group") // 소스들을 같은 슬롯 그룹에 배치
                                .filter(Objects::nonNull)
                                .name("Filter Null Clicks")
                                .uid("filter-null-clicks")
                                .filter(Event::isValid)
                                .name("Validate Clicks")
                                .uid("validate-clicks");

                // ===== 3. Union & Aggregation Pipeline (Chained) =====
                // Union -> Filter -> KeyBy -> Window -> Aggregate 체인
                SingleOutputStreamOperator<CTRResult> ctrResults = impressionStream
                                .union(clickStream)
                                .filter(Event::hasProductId)
                                .name("Filter by ProductId")
                                .uid("filter-product-id")
                                .slotSharingGroup("processing-group") // 처리 연산자들을 같은 슬롯 그룹에 배치
                                .keyBy(Event::getProductId)
                                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                                .allowedLateness(Time.seconds(5))
                                .aggregate(aggregator, windowFunction)
                                .name("CTR Aggregation")
                                .uid("ctr-aggregation");

                // ===== 4. Sinks (각각 독립적인 체인) =====
                // 각 싱크는 독립적으로 실행되어야 하므로 disableChaining() 사용
                ctrResults
                                .addSink(redisSink.createSink())
                                .name("Redis Sink")
                                .uid("redis-sink")
                                .slotSharingGroup("sink-group") // 싱크들을 같은 슬롯 그룹에 배치
                                .disableChaining(); // 체이닝 비활성화로 독립 실행

                ctrResults
                                .addSink(duckDBSink.createSink())
                                .name("DuckDB Sink")
                                .uid("duckdb-sink")
                                .setParallelism(1) // DuckDB는 단일 병렬도로 실행
                                .slotSharingGroup("sink-group")
                                .disableChaining(); // 체이닝 비활성화로 독립 실행

                ctrResults
                                .addSink(clickHouseSink.createSink())
                                .name("ClickHouse Sink")
                                .uid("clickhouse-sink")
                                .slotSharingGroup("sink-group")
                                .disableChaining(); // 체이닝 비활성화로 독립 실행

                return ctrResults;
        }
}
