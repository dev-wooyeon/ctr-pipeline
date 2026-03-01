package com.example.ctr.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EventTest {

    @Test
    fun `eventTimeMillisUtc should return timestamp when present`() {
        val event = Event(
            eventId = "e1",
            productId = "p1",
            eventType = "click",
            timestamp = 1_700_000_000_000L
        )

        val eventTime = event.eventTimeMillisUtc()

        assertThat(eventTime).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `eventTimeMillisUtc should throw when timestamp is null`() {
        val event = Event(
            eventId = "e2",
            productId = "p1",
            eventType = "click",
            timestamp = null
        )

        assertThatThrownBy { event.eventTimeMillisUtc() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Event timestamp is required")
    }

    @Test
    fun `eventTimeMillisUtcOrNull should return null when timestamp is missing`() {
        val event = Event(
            eventId = "e3",
            productId = "p1",
            eventType = "view",
            timestamp = null
        )

        assertThat(event.eventTimeMillisUtcOrNull()).isNull()
    }
}
