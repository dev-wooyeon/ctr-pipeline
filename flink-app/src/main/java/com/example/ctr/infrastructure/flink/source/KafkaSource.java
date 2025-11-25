package com.example.ctr.infrastructure.flink.source;

import com.example.ctr.domain.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KafkaSource {

    private final String bootstrapServers;

    public KafkaSource(@Value("${kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public org.apache.flink.connector.kafka.source.KafkaSource<Event> createSource(String topic, String groupId) {
        return org.apache.flink.connector.kafka.source.KafkaSource.<Event>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new EventDeserializationSchema())
                .build();
    }

    public static class EventDeserializationSchema implements DeserializationSchema<Event> {
        private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        @Override
        public Event deserialize(byte[] message) throws IOException {
            return objectMapper.readValue(message, Event.class);
        }

        @Override
        public boolean isEndOfStream(Event nextElement) {
            return false;
        }

        @Override
        public TypeInformation<Event> getProducedType() {
            return TypeInformation.of(Event.class);
        }
    }
}
