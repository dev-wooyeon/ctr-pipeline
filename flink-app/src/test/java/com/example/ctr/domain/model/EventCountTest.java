package com.example.ctr.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventCountTest {

    @Test
    void initial_ShouldReturnZeroCount() {
        // When
        EventCount eventCount = EventCount.initial();

        // Then
        assertThat(eventCount.getImpressions()).isEqualTo(0L);
        assertThat(eventCount.getClicks()).isEqualTo(0L);
    }

    @Test
    void incrementImpressions_ShouldIncreaseImpressionsCount() {
        // Given
        EventCount eventCount = EventCount.initial();

        // When
        eventCount.incrementImpressions();
        eventCount.incrementImpressions();

        // Then
        assertThat(eventCount.getImpressions()).isEqualTo(2L);
        assertThat(eventCount.getClicks()).isEqualTo(0L);
    }

    @Test
    void incrementClicks_ShouldIncreaseClicksCount() {
        // Given
        EventCount eventCount = EventCount.initial();

        // When
        eventCount.incrementClicks();
        eventCount.incrementClicks();
        eventCount.incrementClicks();

        // Then
        assertThat(eventCount.getImpressions()).isEqualTo(0L);
        assertThat(eventCount.getClicks()).isEqualTo(3L);
    }

    @Test
    void merge_ShouldCombineTwoEventCounts() {
        // Given
        EventCount count1 = new EventCount(100L, 5L);
        EventCount count2 = new EventCount(200L, 10L);

        // When
        EventCount merged = EventCount.merge(count1, count2);

        // Then
        assertThat(merged.getImpressions()).isEqualTo(300L);
        assertThat(merged.getClicks()).isEqualTo(15L);
    }

    @Test
    void copy_ShouldCreateIndependentCopy() {
        // Given
        EventCount original = new EventCount(100L, 5L);

        // When
        EventCount copy = original.copy();
        copy.incrementImpressions();
        copy.incrementClicks();

        // Then
        assertThat(original.getImpressions()).isEqualTo(100L);
        assertThat(original.getClicks()).isEqualTo(5L);
        assertThat(copy.getImpressions()).isEqualTo(101L);
        assertThat(copy.getClicks()).isEqualTo(6L);
    }

    @Test
    void mixedOperations_ShouldWorkCorrectly() {
        // Given
        EventCount eventCount = EventCount.initial();

        // When
        eventCount.incrementImpressions();
        eventCount.incrementImpressions();
        eventCount.incrementClicks();

        // Then
        assertThat(eventCount.getImpressions()).isEqualTo(2L);
        assertThat(eventCount.getClicks()).isEqualTo(1L);
    }
}
