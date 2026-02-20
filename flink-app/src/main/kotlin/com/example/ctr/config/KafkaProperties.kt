package com.example.ctr.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.Valid

class KafkaProperties {
    @field:NotBlank
    var bootstrapServers: String = ""

    @field:NotBlank
    var groupId: String = ""

    @field:NotBlank
    var offsetStrategy: String = "committed"

    @field:NotBlank
    var dlqTopic: String = "dlq-events"

    @field:Valid
    var topics: Topics = Topics()

    class Topics {
        @field:NotBlank
        var impression: String = ""

        @field:NotBlank
        var click: String = ""
    }

    companion object {
        private val VALID_OFFSET_STRATEGIES = setOf("latest", "earliest", "committed")

        fun validateOffsetStrategy(strategy: String): Boolean {
            return strategy.lowercase() in VALID_OFFSET_STRATEGIES
        }
    }

    fun toProducerProperties(): java.util.Properties {
        return java.util.Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("group.id", groupId)
        }
    }
}
