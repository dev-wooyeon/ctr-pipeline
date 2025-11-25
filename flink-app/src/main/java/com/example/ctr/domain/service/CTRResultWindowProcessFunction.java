package com.example.ctr.domain.service;

import com.example.ctr.domain.model.CTRResult;
import com.example.ctr.domain.model.EventCount;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.springframework.stereotype.Component;

/**
 * 윈도우별로 집계된 EventCount를 CTRResult로 변환하는 ProcessFunction
 */
@Component
public class CTRResultWindowProcessFunction
        extends ProcessWindowFunction<EventCount, CTRResult, String, TimeWindow> {

    @Override
    public void process(String productId, Context context, Iterable<EventCount> elements,
            Collector<CTRResult> out) {
        EventCount counts = elements.iterator().next();
        long impressions = counts.getImpressions();
        long clicks = counts.getClicks();
        long windowStart = context.window().getStart();
        long windowEnd = context.window().getEnd();

        CTRResult result = CTRResult.calculate(productId, impressions, clicks, windowStart, windowEnd);
        out.collect(result);
    }
}
