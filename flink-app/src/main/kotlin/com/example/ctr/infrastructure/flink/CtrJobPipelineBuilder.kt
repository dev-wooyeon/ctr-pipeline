package com.example.ctr.infrastructure.flink

import com.example.ctr.config.CtrJobProperties
import com.example.ctr.domain.model.CTRResult
import com.example.ctr.domain.model.Event
import com.example.ctr.domain.model.EventCount
import com.example.ctr.domain.service.CTRResultWindowProcessFunction
import com.example.ctr.domain.service.EventCountAggregator
import com.example.ctr.infrastructure.flink.sink.ClickHouseSink
import com.example.ctr.infrastructure.flink.source.KafkaSourceFactory
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import java.time.Duration
import java.util.Objects

class CtrJobPipelineBuilder(
    private val kafkaSourceFactory: KafkaSourceFactory,
    private val clickHouseSink: ClickHouseSink,
    private val aggregator: AggregateFunction<Event, EventCount, EventCount> = EventCountAggregator(),
    private val windowFunction: ProcessWindowFunction<EventCount, CTRResult, String?, TimeWindow> = CTRResultWindowProcessFunction(),
    private val properties: CtrJobProperties
) {

    fun build(env: StreamExecutionEnvironment): DataStream<CTRResult> {
        val impressionStream = env.kafkaPipeline(properties.impressionTopic, properties.groupId, "Impression", "impression-source")
        val clickStream = env.kafkaPipeline(properties.clickTopic, properties.groupId, "Click", "click-source")

        val ctrResults = buildAggregation(impressionStream, clickStream)

        ctrResults.chainSink(clickHouseSink.createSink(), "ClickHouse", "clickhouse-sink", slotSharingGroup = "sink-group")

        return ctrResults
    }

    private fun buildAggregation(
        impressionStream: SingleOutputStreamOperator<Event>,
        clickStream: SingleOutputStreamOperator<Event>
    ): SingleOutputStreamOperator<CTRResult> {
        return impressionStream
            .union(clickStream)
            .filter(Event::hasProductId)
            .name("Filter by ProductId")
            .uid("filter-product-id")
            .slotSharingGroup("processing-group")
            .keyBy(Event::productId)
            .window(TumblingEventTimeWindows.of(Time.seconds(10)))
            .allowedLateness(Time.seconds(5))
            .aggregate(aggregator, windowFunction)
            .name("CTR Aggregation")
            .uid("ctr-aggregation")
    }

    private fun StreamExecutionEnvironment.kafkaPipeline(
        topic: String,
        groupId: String,
        prefix: String,
        baseUid: String
    ): SingleOutputStreamOperator<Event> {
        return fromSource(
            kafkaSourceFactory.createSource(topic, groupId),
            WatermarkStrategy.forBoundedOutOfOrderness<Event>(Duration.ofSeconds(5))
                .withTimestampAssigner { event, _ -> event.eventTimeMillisUtc() },
            "$prefix Kafka Source"
        ).uid(baseUid)
            .slotSharingGroup("source-group")
            .filter(Objects::nonNull)
            .name("Filter Null $prefix")
            .uid("filter-null-$baseUid")
            .filter(Event::isValid)
            .name("Validate $prefix")
            .uid("validate-$baseUid")
    }

    private fun SingleOutputStreamOperator<CTRResult>.chainSink(
        sink: SinkFunction<CTRResult>,
        namePrefix: String,
        uid: String,
        slotSharingGroup: String,
        parallelism: Int? = null
    ) {
        val sinkOperator = addSink(sink)
            .name("$namePrefix Sink")
            .uid(uid)
            .slotSharingGroup(slotSharingGroup)
            .disableChaining()
        parallelism?.let { sinkOperator.setParallelism(it) }
    }
}
