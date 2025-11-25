package com.example.ctr.application;

import com.example.ctr.domain.model.CTRResult;
import com.example.ctr.domain.model.Event;
import com.example.ctr.domain.service.CTRResultWindowProcessFunction;
import com.example.ctr.domain.service.EventCountAggregator;
import com.example.ctr.infrastructure.flink.sink.ClickHouseSink;
import com.example.ctr.infrastructure.flink.sink.DuckDBSink;
import com.example.ctr.infrastructure.flink.sink.RedisSink;
import com.example.ctr.infrastructure.flink.source.KafkaSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;

@Slf4j
@Service
public class CtrJobService {

    private final KafkaSource kafkaSource;
    private final RedisSink redisSink;
    private final DuckDBSink duckDBSink;
    private final ClickHouseSink clickHouseSink;

    private final String impressionTopic;
    private final String clickTopic;
    private final String groupId;
    private final int parallelism;

    private volatile StreamExecutionEnvironment env;

    public CtrJobService(
            KafkaSource kafkaSource,
            RedisSink redisSink,
            DuckDBSink duckDBSink,
            ClickHouseSink clickHouseSink,
            @Value("${kafka.topics.impression}") String impressionTopic,
            @Value("${kafka.topics.click}") String clickTopic,
            @Value("${kafka.group-id}") String groupId,
            @Value("${flink.parallelism}") int parallelism) {
        this.kafkaSource = kafkaSource;
        this.redisSink = redisSink;
        this.duckDBSink = duckDBSink;
        this.clickHouseSink = clickHouseSink;
        this.impressionTopic = impressionTopic;
        this.clickTopic = clickTopic;
        this.groupId = groupId;
        this.parallelism = parallelism;
    }

    public void execute() {
        try {
            log.info("Starting CTR Calculator Flink Job...");
            env = StreamExecutionEnvironment.getExecutionEnvironment();
            env.setParallelism(parallelism);

            // Register shutdown hook for graceful termination
            registerShutdownHook();

            // Sources
            DataStream<Event> impressionStream = env.fromSource(
                    kafkaSource.createSource(impressionTopic, groupId),
                    WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                            .withTimestampAssigner(
                                    (event, timestamp) -> java.sql.Timestamp.valueOf(event.getTimestamp()).getTime()),
                    "Impression Source");

            DataStream<Event> clickStream = env.fromSource(
                    kafkaSource.createSource(clickTopic, groupId),
                    WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                            .withTimestampAssigner(
                                    (event, timestamp) -> java.sql.Timestamp.valueOf(event.getTimestamp()).getTime()),
                    "Click Source");

            // Union & Transformation
            DataStream<CTRResult> ctrResults = impressionStream.union(clickStream)
                    .filter(Event::hasProductId)
                    .keyBy(Event::getProductId)
                    .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                    .allowedLateness(Time.seconds(5))
                    .aggregate(new EventCountAggregator(), new CTRResultWindowProcessFunction());

            // Sinks
            ctrResults.addSink(redisSink.createSink()).name("Redis Sink");
            ctrResults.addSink(duckDBSink.createSink()).name("DuckDB Sink").setParallelism(1);
            ctrResults.addSink(clickHouseSink.createSink()).name("ClickHouse Sink");

            ctrResults.print();

            log.info("Executing Flink job...");
            env.execute("CTR Calculator Job (Spring Boot)");
            log.info("Flink job completed successfully");
        } catch (Exception e) {
            log.error("Failed to execute Flink job", e);
            throw new RuntimeException("Flink job execution failed", e);
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered. Gracefully stopping Flink job...");
            try {
                if (env != null) {
                    // Flink will handle graceful shutdown automatically
                    log.info("Flink job shutdown initiated");
                }
            } catch (Exception e) {
                log.error("Error during graceful shutdown", e);
            }
        }, "flink-shutdown-hook"));
    }

    @PreDestroy
    public void onDestroy() {
        log.info("CtrJobService is being destroyed. Cleanup if needed.");
    }
}
