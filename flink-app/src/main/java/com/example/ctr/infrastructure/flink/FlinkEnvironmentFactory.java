package com.example.ctr.infrastructure.flink;

import com.example.ctr.config.CtrJobProperties;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Factory that creates a configured {@link StreamExecutionEnvironment}.
 *
 * Configuration values are supplied via {@link CtrJobProperties}.
 */
@Component
public class FlinkEnvironmentFactory {

    private final CtrJobProperties properties;

    public FlinkEnvironmentFactory(CtrJobProperties properties) {
        this.properties = properties;
    }

    public StreamExecutionEnvironment create() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(properties.getParallelism());

        // Exactly‑once checkpointing
        if (properties.isCheckpointEnabled()) {
            env.enableCheckpointing(properties.getCheckpointInterval());
            CheckpointConfig checkpointConfig = env.getCheckpointConfig();
            checkpointConfig.setCheckpointingMode(properties.getCheckpointingMode());
            checkpointConfig.setCheckpointStorage(properties.getCheckpointStorage());
            checkpointConfig.setCheckpointTimeout(properties.getCheckpointTimeout());
            checkpointConfig.setMinPauseBetweenCheckpoints(properties.getCheckpointMinPause());
            checkpointConfig.setMaxConcurrentCheckpoints(properties.getCheckpointMaxConcurrent());
            checkpointConfig.setExternalizedCheckpointCleanup(properties.getExternalizedCheckpointCleanup());
        }

        // Fixed‑delay restart strategy (configurable)
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
                properties.getRestartAttempts(),
                Time.of(properties.getRestartDelayMs(), TimeUnit.MILLISECONDS)));

        return env;
    }
}
