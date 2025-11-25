package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.config.RedisProperties;
import com.example.ctr.domain.model.CTRResult;
import com.example.ctr.infrastructure.flink.sink.impl.RedisRichSink;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.springframework.stereotype.Component;

@Component
public class RedisSink {

    private final RedisProperties redisProperties;

    public RedisSink(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    public RichSinkFunction<CTRResult> createSink() {
        return new RedisRichSink(redisProperties.getHost(), redisProperties.getPort());
    }
}
