package com.example.ctr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clickhouse")
public class ClickHouseProperties {

    @NotBlank
    private String url;

    @NotBlank
    private String driver;
}
