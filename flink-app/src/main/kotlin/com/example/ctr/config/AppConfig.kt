package com.example.ctr.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.io.InputStream

@JsonIgnoreProperties(ignoreUnknown = true)
class AppConfig {

    var kafka: KafkaProperties = KafkaProperties()
    var clickhouse: ClickHouseProperties = ClickHouseProperties()
    var ctr: CtrConfig = CtrConfig()
    var flink: FlinkConfig = FlinkConfig()

    class CtrConfig {
        var job: CtrJobProperties = CtrJobProperties()
    }

    class FlinkConfig {
        var parallelism: Int = 1
        var checkpoint: CheckpointConfig = CheckpointConfig()
        var stateBackend: StateBackendConfig = StateBackendConfig()
        var restartStrategy: RestartStrategyConfig = RestartStrategyConfig()

        class CheckpointConfig {
            var enabled: Boolean = true
            var interval: Long = 60_000L
            var timeout: Long = 600_000L
            var minPause: Long = 5_000L
            var maxConcurrent: Int = 1
            var mode: String = "EXACTLY_ONCE"
        }

        class StateBackendConfig {
            var type: String = "filesystem"
            var checkpointDir: String = ""
            var savepointDir: String = ""
        }

        class RestartStrategyConfig {
            var type: String = "fixed-delay"
            var attempts: Int = 3
            var delay: Long = 10_000L
        }
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

                return mapper.readValue(inputStream, AppConfig::class.java)
            } catch (ex: Exception) {
                throw RuntimeException("Failed to load configuration", ex)
            }
        }
    }
}
