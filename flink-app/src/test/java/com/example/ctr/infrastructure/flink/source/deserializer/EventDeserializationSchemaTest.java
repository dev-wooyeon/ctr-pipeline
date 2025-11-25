package com.example.ctr.infrastructure.flink.source.deserializer;

import com.example.ctr.domain.model.Event;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeserializationSchemaTest {

    private final EventDeserializationSchema schema = new EventDeserializationSchema();

    @Test
    void deserializesValidEvent() throws Exception {
        String payload = """
                {
                  "event_id": "1",
                  "event_type": "view",
                  "product_id": "P123",
                  "user_id": "U1",
                  "timestamp": "2024-01-01 12:00:00"
                }
                """;

        Event event = schema.deserialize(payload.getBytes());

        assertThat(event).isNotNull();
        assertThat(event.getProductId()).isEqualTo("P123");
        assertThat(event.isValid()).isTrue();
    }

    @Test
    void dropsInvalidEventType() throws Exception {
        String payload = """
                {
                  "event_id": "2",
                  "event_type": "unknown",
                  "product_id": "P123",
                  "user_id": "U1",
                  "timestamp": "2024-01-01 12:00:00"
                }
                """;

        Event event = schema.deserialize(payload.getBytes());

        assertThat(event).isNull();
    }
}
