package com.example.ctr.domain.model

import com.example.ctr.domain.model.deserializer.TimestampDeserializer
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
    @JsonProperty("event_id")
    val eventId: String? = null,

    @JsonProperty("event_type")
    val eventType: String? = null,

    @JsonProperty("product_id")
    val productId: String? = null,

    @JsonProperty("user_id")
    val userId: String? = null,

    @JsonProperty("timestamp")
    @JsonDeserialize(using = TimestampDeserializer::class)
    val timestamp: Long? = null,

    @JsonProperty("session_id")
    val sessionId: String? = null
) {

    fun hasProductId(): Boolean = !productId.isNullOrBlank()

    fun isValid(): Boolean = hasProductId() && timestamp != null && VALID_EVENT_TYPES.contains(eventType)

    fun eventTimeMillisUtc(): Long = timestamp ?: 0L

    companion object {
        private val VALID_EVENT_TYPES: Set<String> = setOf("view", "impression", "click")

        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var eventId: String? = null
        private var eventType: String? = null
        private var productId: String? = null
        private var userId: String? = null
        private var timestamp: Long? = null
        private var sessionId: String? = null

        fun eventId(eventId: String?) = apply { this.eventId = eventId }
        fun eventType(eventType: String?) = apply { this.eventType = eventType }
        fun productId(productId: String?) = apply { this.productId = productId }
        fun userId(userId: String?) = apply { this.userId = userId }
        fun timestamp(timestamp: Long?) = apply { this.timestamp = timestamp }
        fun sessionId(sessionId: String?) = apply { this.sessionId = sessionId }

        fun build(): Event = Event(eventId, eventType, productId, userId, timestamp, sessionId)
    }
}
