package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.domain.model.CTRResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@Component
public class RedisSink {

    private final String redisHost;
    private final int redisPort;

    public RedisSink(
            @Value("${redis.host}") String redisHost,
            @Value("${redis.port}") int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    public RichSinkFunction<CTRResult> createSink() {
        return new RedisRichSink(redisHost, redisPort);
    }

    public static class RedisRichSink extends RichSinkFunction<CTRResult> {
        private final String redisHost;
        private final int redisPort;
        private transient Jedis jedis;
        private transient ObjectMapper objectMapper;

        public RedisRichSink(String redisHost, int redisPort) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            this.jedis = new Jedis(redisHost, redisPort);
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public void invoke(CTRResult value, Context context) throws Exception {
            String json = objectMapper.writeValueAsString(value);
            // Store latest CTR
            jedis.hset("ctr:latest", value.getProductId(), json);
            // Store history (optional, or use a list/sorted set)
            // jedis.lpush("ctr:history:" + value.getProductId(), json);
        }

        @Override
        public void close() throws Exception {
            if (jedis != null) {
                jedis.close();
            }
            super.close();
        }
    }
}
