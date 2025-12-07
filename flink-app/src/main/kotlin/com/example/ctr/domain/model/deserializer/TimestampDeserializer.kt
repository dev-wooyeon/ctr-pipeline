package com.example.ctr.domain.model.deserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TimestampDeserializer : JsonDeserializer<Long?>() {

    private val customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Long? {
        return when (parser.currentToken()) {
            JsonToken.VALUE_NUMBER_INT -> parser.longValue
            JsonToken.VALUE_STRING -> parseString(parser.text)
            JsonToken.VALUE_NULL -> null
            else -> null
        }
    }

    private fun parseString(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        text.toLongOrNull()?.let { return it }
        runCatching {
            LocalDateTime.parse(text, customFormatter)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.onSuccess { return it }
        runCatching {
            Instant.parse(text)
        }.onSuccess { return it.toEpochMilli() }
        return null
    }
}
