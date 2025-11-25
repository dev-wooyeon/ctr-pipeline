package com.example.ctr.infrastructure.flink.source;

import com.example.ctr.config.KafkaProperties;
import com.example.ctr.domain.model.Event;
import com.example.ctr.infrastructure.flink.source.deserializer.EventDeserializationSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.springframework.stereotype.Component;

@Component
public class KafkaSourceFactory {

    private final KafkaProperties kafkaProperties;

    public KafkaSourceFactory(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    public KafkaSource<Event> createSource(String topic, String groupId) {
        return KafkaSource.<Event>builder()
                .setBootstrapServers(kafkaProperties.getBootstrapServers())
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new EventDeserializationSchema())
                .build();
    }
}
