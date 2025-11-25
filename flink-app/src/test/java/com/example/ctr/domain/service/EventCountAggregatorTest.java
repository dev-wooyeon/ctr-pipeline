package com.example.ctr.domain.service;

import com.example.ctr.domain.model.Event;
import com.example.ctr.domain.model.EventCount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventCountAggregatorTest {

    private final EventCountAggregator aggregator = new EventCountAggregator();

    @Test
    void aggregatesViewAndClickEvents() {
        EventCount accumulator = aggregator.createAccumulator();
        Event view = Event.builder().eventType("view").build();
        Event click = Event.builder().eventType("click").build();

        EventCount afterView = aggregator.add(view, accumulator);
        EventCount afterClick = aggregator.add(click, afterView);

        EventCount result = aggregator.getResult(afterClick);

        assertThat(result.getImpressions()).isEqualTo(1);
        assertThat(result.getClicks()).isEqualTo(1);
    }

    @Test
    void mergesAccumulators() {
        EventCount a = new EventCount(1, 2);
        EventCount b = new EventCount(3, 4);

        EventCount merged = aggregator.merge(a, b);

        assertThat(merged.getImpressions()).isEqualTo(4);
        assertThat(merged.getClicks()).isEqualTo(6);
    }
}
