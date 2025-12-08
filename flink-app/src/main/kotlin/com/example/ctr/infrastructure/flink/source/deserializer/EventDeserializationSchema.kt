package com.example.ctr.infrastructure.flink.source.deserializer

import com.example.ctr.domain.model.Event
import com.example.ctr.domain.model.ParsingResult
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.time.LocalDateTime
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.slf4j.LoggerFactory

class EventDeserializationSchema : DeserializationSchema<ParsingResult<Event>> {

    companion object {
        private val log = LoggerFactory.getLogger(EventDeserializationSchema::class.java)
        private val objectMapper: ObjectMapper =
                ObjectMapper()
                        .registerModule(JavaTimeModule())
                        .registerModule(
                                SimpleModule()
                                        .addDeserializer(
                                                LocalDateTime::class.java,
                                                LocalDateTimeFlexibleDeserializer()
                                        )
                        )
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun deserialize(message: ByteArray): ParsingResult<Event> {
        return try {
            val event = objectMapper.readValue(message, Event::class.java)
            if (event == null || !event.isValid()) {
                log.warn("Invalid event detected: {}", event)
                return ParsingResult(
                        rawData = message,
                        errorMessage = "Invalid event data",
                        stackTrace = null
                )
            }
            ParsingResult(result = event)
        } catch (ex: Exception) {
            log.warn("Failed to deserialize event payload", ex)
            ParsingResult(
                    rawData = message,
                    errorMessage = ex.message,
                    stackTrace = ex.stackTraceToString()
            )
        }
    }

    override fun isEndOfStream(nextElement: ParsingResult<Event>?): Boolean = false

    override fun getProducedType(): TypeInformation<ParsingResult<Event>> =
            TypeInformation.of(
                    object :
                            org.apache.flink.api.common.typeinfo.TypeHint<ParsingResult<Event>>() {}
            )
}
