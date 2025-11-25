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
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Builds the Flink data‑flow pipeline.
 *
 * All configuration (Kafka topics, group id) is supplied via
 * {@link CtrJobProperties}.
 */
@Component
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
        // Sources
        DataStream<Event> impressionStream = env.fromSource(
                kafkaSourceFactory.createSource(properties.getImpressionTopic(), properties.getGroupId()),
                WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner(
                                (event, ts) -> event.eventTimeMillisUtc()),
                "Impression Source");
        impressionStream = impressionStream.filter(Objects::nonNull).filter(Event::isValid);

        DataStream<Event> clickStream = env.fromSource(
                kafkaSourceFactory.createSource(properties.getClickTopic(), properties.getGroupId()),
                WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner(
                                (event, ts) -> event.eventTimeMillisUtc()),
                "Click Source");
        clickStream = clickStream.filter(Objects::nonNull).filter(Event::isValid);

        // Union & aggregation
        DataStream<CTRResult> ctrResults = impressionStream.union(clickStream)
                .filter(Event::hasProductId)
                .keyBy(Event::getProductId)
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .allowedLateness(Time.seconds(5))
                .aggregate(aggregator, windowFunction);

        // Sinks
        ctrResults.addSink(redisSink.createSink()).name("Redis Sink");
        ctrResults.addSink(duckDBSink.createSink()).name("DuckDB Sink").setParallelism(1);
        ctrResults.addSink(clickHouseSink.createSink()).name("ClickHouse Sink");

        return ctrResults;
    }
}
