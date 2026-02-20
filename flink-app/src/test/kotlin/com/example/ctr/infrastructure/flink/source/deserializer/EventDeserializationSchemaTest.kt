package com.example.ctr.infrastructure.flink.source.deserializer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventDeserializationSchemaTest {

    private val schema = EventDeserializationSchema()

    @Test
    fun `deserializes valid event`() {
        val epochMillis = 1704110400000L
        val payload = """
            {
              "event_id": "1",
              "event_type": "impression",
              "product_id": "P123",
              "user_id": "U1",
              "timestamp": "2024-01-01 12:00:00"
            }
        """.trimIndent()

        val result = schema.deserialize(payload.toByteArray())

        assertThat(result.isSuccess()).isTrue()
        assertThat(result.result).isNotNull
        assertThat(result.result?.productId).isEqualTo("P123")
        assertThat(result.result?.eventTimeMillisUtc()).isEqualTo(epochMillis)
        assertThat(result.result?.isValid()).isTrue()
    }

    @Test
    fun `deserializes epoch millis timestamp`() {
        val epochMillis = 1_700_000_000_000L
        val payload = """
            {
              "event_id": "10",
              "event_type": "click",
              "product_id": "P999",
              "user_id": "U9",
              "timestamp": $epochMillis,
              "session_id": "session_1"
            }
        """.trimIndent()

        val result = schema.deserialize(payload.toByteArray())

        assertThat(result.isSuccess()).isTrue()
        assertThat(result.result).isNotNull
        assertThat(result.result?.timestamp).isEqualTo(epochMillis)
        assertThat(result.result?.eventTimeMillisUtc()).isEqualTo(epochMillis)
        assertThat(result.result?.sessionId).isEqualTo("session_1")
    }

    @Test
    fun `drops invalid event type`() {
        val payload = """
            {
              "event_id": "2",
              "event_type": "unknown",
              "product_id": "P123",
              "user_id": "U1",
              "timestamp": "2024-01-01 12:00:00"
            }
        """.trimIndent()

        val result = schema.deserialize(payload.toByteArray())
        assertThat(result.isSuccess()).isFalse()
        assertThat(result.result).isNull()
    }

    @Test
    fun `deserializes missing timestamp into DLQ`() {
        val payload = """
            {
              "event_id": "3",
              "event_type": "impression",
              "product_id": "P999",
              "user_id": "U1"
            }
        """.trimIndent()

        val result = schema.deserialize(payload.toByteArray())

        assertThat(result.isSuccess()).isFalse()
        assertThat(result.result).isNull()
        assertThat(result.errorMessage).isEqualTo("Invalid event data")
        assertThat(result.rawData).isNotNull
    }
}
