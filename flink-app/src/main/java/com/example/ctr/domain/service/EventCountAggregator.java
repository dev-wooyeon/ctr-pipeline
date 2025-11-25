package com.example.ctr.domain.service;

import com.example.ctr.domain.model.Event;
import com.example.ctr.domain.model.EventCount;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.springframework.stereotype.Component;

/**
 * 이벤트를 집계하여 조회수(impressions)와 클릭수(clicks)를 계산하는 Aggregator
 */
@Component
public class EventCountAggregator implements AggregateFunction<Event, EventCount, EventCount> {

    @Override
    public EventCount createAccumulator() {
        return EventCount.initial();
    }

    @Override
    public EventCount add(Event event, EventCount accumulator) {
        if ("view".equals(event.getEventType())) {
            accumulator.incrementImpressions();
        } else if ("click".equals(event.getEventType())) {
            accumulator.incrementClicks();
        }
        return accumulator;
    }

    @Override
    public EventCount getResult(EventCount accumulator) {
        return accumulator;
    }

    @Override
    public EventCount merge(EventCount a, EventCount b) {
        return EventCount.merge(a, b);
    }
}
