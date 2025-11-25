package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.config.ClickHouseProperties;
import com.example.ctr.domain.model.CTRResult;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@Component
public class ClickHouseSink {

    private final ClickHouseProperties clickHouseProperties;

    public ClickHouseSink(ClickHouseProperties clickHouseProperties) {
        this.clickHouseProperties = clickHouseProperties;
    }

    public SinkFunction<CTRResult> createSink() {
        return JdbcSink.sink(
                "INSERT INTO ctr_results (product_id, ctr, impressions, clicks, window_start, window_end) VALUES (?, ?, ?, ?, ?, ?)",
                (JdbcStatementBuilder<CTRResult>) this::bindCtrResult,
                JdbcExecutionOptions.builder()
                        .withBatchSize(1000)
                        .withBatchIntervalMs(200)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(clickHouseProperties.getUrl())
                        .withDriverName(clickHouseProperties.getDriver())
                        .build());
    }

    private static final int IDX_PRODUCT_ID = 1;
    private static final int IDX_CTR = 2;
    private static final int IDX_IMPRESSIONS = 3;
    private static final int IDX_CLICKS = 4;
    private static final int IDX_WINDOW_START = 5;
    private static final int IDX_WINDOW_END = 6;

    private void bindCtrResult(PreparedStatement ps, CTRResult ctrResult) throws SQLException {
        ps.setString(IDX_PRODUCT_ID, ctrResult.getProductId());
        ps.setDouble(IDX_CTR, ctrResult.getCtr());
        ps.setLong(IDX_IMPRESSIONS, ctrResult.getImpressions());
        ps.setLong(IDX_CLICKS, ctrResult.getClicks());
        ps.setLong(IDX_WINDOW_START, ctrResult.getWindowStart());
        ps.setLong(IDX_WINDOW_END, ctrResult.getWindowEnd());
    }
}
