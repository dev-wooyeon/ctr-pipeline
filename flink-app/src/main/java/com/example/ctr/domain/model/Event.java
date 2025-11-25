package com.example.ctr.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public boolean hasProductId() {
        return productId != null && !productId.isEmpty();
    }

    public boolean isValid() {
        return hasProductId()
                && timestamp != null
                && VALID_EVENT_TYPES.contains(eventType);
    }

    public long eventTimeMillisUtc() {
        if (timestamp == null) {
            return 0L;
        }
        return timestamp.atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static final Set<String> VALID_EVENT_TYPES = Set.of("view", "click");
}
