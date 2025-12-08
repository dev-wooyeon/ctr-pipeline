package com.example.ctr.infrastructure.flink

import com.example.ctr.config.CtrJobProperties
import com.example.ctr.domain.model.CTRResult
import com.example.ctr.domain.model.Event
import com.example.ctr.domain.model.EventCount
import com.example.ctr.domain.model.ParsingResult
import com.example.ctr.domain.service.CTRResultWindowProcessFunction
import com.example.ctr.domain.service.EventCountAggregator
import com.example.ctr.infrastructure.flink.sink.ClickHouseSink
import com.example.ctr.infrastructure.flink.source.KafkaSourceFactory
import java.time.Duration
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.apache.flink.util.OutputTag

class CtrJobPipelineBuilder(
        private val kafkaSourceFactory: KafkaSourceFactory,
        private val clickHouseSink: ClickHouseSink,
        private val aggregator: AggregateFunction<Event, EventCount, EventCount> =
                EventCountAggregator(),
        private val windowFunction:
                ProcessWindowFunction<EventCount, CTRResult, String?, TimeWindow> =
                CTRResultWindowProcessFunction(),
        private val properties: CtrJobProperties
) {

        fun build(env: StreamExecutionEnvironment): DataStream<CTRResult> {
                val impressionStream =
                        env.kafkaPipeline(
                                properties.impressionTopic,
                                properties.groupId,
                                "Impression",
                                "impression-source"
                        )
                val clickStream =
                        env.kafkaPipeline(
                                properties.clickTopic,
                                properties.groupId,
                                "Click",
                                "click-source"
                        )

                val ctrResults = buildAggregation(impressionStream, clickStream)

                ctrResults.chainSink(
                        clickHouseSink.createSink(),
                        "ClickHouse",
                        "clickhouse-sink",
                        slotSharingGroup = "sink-group"
                )

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
                val dlqTag = object : OutputTag<ParsingResult<Event>>("${prefix}DLQ") {}

                val rawStream =
                        fromSource(
                                        kafkaSourceFactory.createSource(topic, groupId),
                                        WatermarkStrategy.forBoundedOutOfOrderness<
                                                        ParsingResult<Event>>(Duration.ofSeconds(5))
                                                .withTimestampAssigner { result, _ ->
                                                        // Timestamp extraction is tricky for failed
                                                        // parsing.
                                                        // We try to extract from result if
                                                        // possible, or use
                                                        // current time (or 0)
                                                        result.result?.eventTimeMillisUtc()
                                                                ?: System.currentTimeMillis()
                                                },
                                        "$prefix Kafka Source"
                                )
                                .uid(baseUid)
                                .slotSharingGroup("source-group")

                val processedStream =
                        rawStream
                                .process(
                                        object : ProcessFunction<ParsingResult<Event>, Event>() {
                                                override fun processElement(
                                                        value: ParsingResult<Event>,
                                                        ctx: Context,
                                                        out: Collector<Event>
                                                ) {
                                                        if (value.isSuccess()) {
                                                                out.collect(value.result!!)
                                                        } else {
                                                                ctx.output(dlqTag, value)
                                                        }
                                                }
                                        }
                                )
                                .name("Split DLQ $prefix")
                                .uid("split-dlq-$baseUid")

                // Handle DLQ (For now, just log it. Ideally sink to a DLQ Topic or S3)
                processedStream
                        .getSideOutput(dlqTag)
                        .map {
                                "DLQ [$prefix]: ${it.errorMessage}, Data: ${it.rawData?.toString(Charsets.UTF_8)}"
                        }
                        .print()
                        .name("DLQ Sink $prefix")
                        .uid("dlq-sink-$baseUid")

                return processedStream
        }

        private fun SingleOutputStreamOperator<CTRResult>.chainSink(
                sink: SinkFunction<CTRResult>,
                namePrefix: String,
                uid: String,
                slotSharingGroup: String,
                parallelism: Int? = null
        ) {
                val sinkOperator =
                        addSink(sink)
                                .name("$namePrefix Sink")
                                .uid(uid)
                                .slotSharingGroup(slotSharingGroup)
                // .disableChaining()
                parallelism?.let { sinkOperator.setParallelism(it) }
        }
}
