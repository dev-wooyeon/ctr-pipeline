package com.example.ctr.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Valid
import java.io.InputStream

@JsonIgnoreProperties(ignoreUnknown = true)
class AppConfig {

    @field:Valid
    var kafka: KafkaProperties = KafkaProperties()
    @field:Valid
    var clickhouse: ClickHouseProperties = ClickHouseProperties()
    @field:Valid
    var ctr: CtrConfig = CtrConfig()

    class CtrConfig {
        @field:Valid
        var job: CtrJobProperties = CtrJobProperties()
    }

    companion object {
        fun load(): AppConfig {
            try {
                val mapper = ObjectMapper(YAMLFactory())
                mapper.propertyNamingStrategy = PropertyNamingStrategies.KEBAB_CASE
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

                var inputStream: InputStream? = AppConfig::class.java.classLoader
                    .getResourceAsStream("application-local.yml")
                if (inputStream == null) {
                    inputStream = AppConfig::class.java.classLoader.getResourceAsStream("application.yml")
                }
                if (inputStream == null) {
                    throw RuntimeException("Configuration file not found (application-local.yml or application.yml)")
                }

                return mapper.readValue(inputStream, AppConfig::class.java).also { validate(it) }
            } catch (ex: Exception) {
                throw RuntimeException("Failed to load configuration", ex)
            }
        }

        private fun validate(config: AppConfig) {
            val validator = Validation.buildDefaultValidatorFactory().validator
            val violations: Set<ConstraintViolation<AppConfig>> = validator.validate(config)
            if (violations.isNotEmpty()) {
                val errorMessage = violations
                    .sortedBy { it.propertyPath.toString() }
                    .joinToString("\n") { "${it.propertyPath}: ${it.message}" }
                throw IllegalArgumentException("Invalid application configuration:\n$errorMessage")
            }
        }
    }
}
