package com.example.ctr.domain.service

import com.example.ctr.domain.model.CTRResult
import com.example.ctr.domain.model.EventCount
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction.Context
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

class CTRResultWindowProcessFunction : ProcessWindowFunction<EventCount, CTRResult, String?, TimeWindow>() {

    override fun process(
        key: String?,
        context: Context,
        elements: Iterable<EventCount>,
        out: Collector<CTRResult>
    ) {
        val counts = elements.iterator().next()
        val result = CTRResult.calculate(
            key ?: "<unknown>",
            counts.impressions,
            counts.clicks,
            context.window().start,
            context.window().end
        )
        out.collect(result)
    }
}
