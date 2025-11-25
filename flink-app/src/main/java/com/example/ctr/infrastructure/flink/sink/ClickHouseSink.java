package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.domain.model.CTRResult;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClickHouseSink {

    private final String clickhouseUrl;
    private final String clickhouseDriver;

    public ClickHouseSink(
            @Value("${clickhouse.url}") String clickhouseUrl,
            @Value("${clickhouse.driver}") String clickhouseDriver) {
        this.clickhouseUrl = clickhouseUrl;
        this.clickhouseDriver = clickhouseDriver;
    }

    public SinkFunction<CTRResult> createSink() {
        return JdbcSink.sink(
                "INSERT INTO ctr_results (product_id, ctr, impressions, clicks, window_start, window_end) VALUES (?, ?, ?, ?, ?, ?)",
                (JdbcStatementBuilder<CTRResult>) (preparedStatement, ctrResult) -> {
                    preparedStatement.setString(1, ctrResult.getProductId());
                    preparedStatement.setDouble(2, ctrResult.getCtr());
                    preparedStatement.setLong(3, ctrResult.getImpressions());
                    preparedStatement.setLong(4, ctrResult.getClicks());
                    preparedStatement.setLong(5, ctrResult.getWindowStart());
                    preparedStatement.setLong(6, ctrResult.getWindowEnd());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(100)
                        .withBatchIntervalMs(200)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(clickhouseUrl)
                        .withDriverName(clickhouseDriver)
                        .build());
    }
}
