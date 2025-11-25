package com.example.ctr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    @NotBlank
    private String bootstrapServers;

    @NotBlank
    private String groupId;

    private Topics topics = new Topics();

    @Getter
    @Setter
    public static class Topics {
        @NotBlank
        private String impression;
        @NotBlank
        private String click;
    }
}
