package com.example.ctr.infrastructure.flink.sink.impl;

import com.example.ctr.domain.model.CTRResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

public class RedisRichSink extends RichSinkFunction<CTRResult> {

    private final String redisHost;
    private final int redisPort;
    private transient JedisPooled jedis;
    private transient ObjectMapper objectMapper;
    private transient Counter successCounter;
    private transient Counter failureCounter;

    public RedisRichSink(String redisHost, int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(2_000)
                .socketTimeoutMillis(2_000)
                .build();
        this.jedis = new JedisPooled(new HostAndPort(redisHost, redisPort), config);
        this.objectMapper = new ObjectMapper();
        this.successCounter = getRuntimeContext().getMetricGroup().counter("redis_sink_success_total");
        this.failureCounter = getRuntimeContext().getMetricGroup().counter("redis_sink_failure_total");
    }

    @Override
    public void invoke(CTRResult value, Context context) throws Exception {
        int attempts = 0;
        while (attempts < 3) {
            try {
                attempts++;
                String json = objectMapper.writeValueAsString(value);
                jedis.hset("ctr:latest", value.getProductId(), json);
                successCounter.inc();
                return;
            } catch (Exception e) {
                failureCounter.inc();
                if (attempts >= 3) {
                    throw e;
                }
                Thread.sleep(50L * attempts);
            }
        }
    }

    @Override
    public void close() throws Exception {
        // JedisPooled handles its own resources; nothing to close explicitly
        super.close();
    }
}
