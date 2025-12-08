package com.example.ctr.infrastructure.flink.source

import com.example.ctr.config.KafkaProperties
import com.example.ctr.domain.model.Event
import com.example.ctr.domain.model.ParsingResult
import com.example.ctr.infrastructure.flink.source.deserializer.EventDeserializationSchema
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer

class KafkaSourceFactory(private val kafkaProperties: KafkaProperties) {

    fun createSource(topic: String, groupId: String): KafkaSource<ParsingResult<Event>> =
            KafkaSource.builder<ParsingResult<Event>>()
                    .setBootstrapServers(kafkaProperties.bootstrapServers)
                    .setTopics(topic)
                    .setGroupId(groupId)
                    .setStartingOffsets(OffsetsInitializer.latest())
                    .setValueOnlyDeserializer(EventDeserializationSchema())
                    .build()
}
