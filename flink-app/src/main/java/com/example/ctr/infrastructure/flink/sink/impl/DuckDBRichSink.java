package com.example.ctr.infrastructure.flink.sink.impl;

import com.example.ctr.domain.model.CTRResult;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class DuckDBRichSink extends RichSinkFunction<CTRResult> {

    private final String duckdbUrl;
    private transient Connection connection;
    private transient PreparedStatement preparedStatement;
    private transient Counter successCounter;
    private transient Counter failureCounter;

    public DuckDBRichSink(String duckdbUrl) {
        this.duckdbUrl = duckdbUrl;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        Class.forName("org.duckdb.DuckDBDriver");
        connection = DriverManager.getConnection(duckdbUrl);

        String createTableSql = "CREATE TABLE IF NOT EXISTS ctr_results (" +
                "product_id VARCHAR, " +
                "ctr DOUBLE, " +
                "impressions BIGINT, " +
                "clicks BIGINT, " +
                "window_start BIGINT, " +
                "window_end BIGINT" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }

        String insertSql = "INSERT INTO ctr_results VALUES (?, ?, ?, ?, ?, ?)";
        preparedStatement = connection.prepareStatement(insertSql);
        successCounter = getRuntimeContext().getMetricGroup().counter("duckdb_sink_success_total");
        failureCounter = getRuntimeContext().getMetricGroup().counter("duckdb_sink_failure_total");
    }

    @Override
    public void invoke(CTRResult value, Context context) throws Exception {
        int attempts = 0;
        while (attempts < 2) {
            try {
                attempts++;
                preparedStatement.setString(1, value.getProductId());
                preparedStatement.setDouble(2, value.getCtr());
                preparedStatement.setLong(3, value.getImpressions());
                preparedStatement.setLong(4, value.getClicks());
                preparedStatement.setLong(5, value.getWindowStart());
                preparedStatement.setLong(6, value.getWindowEnd());
                preparedStatement.execute();
                successCounter.inc();
                return;
            } catch (Exception e) {
                failureCounter.inc();
                if (attempts >= 2) {
                    throw e;
                }
                reopen();
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (preparedStatement != null) {
            preparedStatement.close();
        }
        if (connection != null) {
            connection.close();
        }
        super.close();
    }

    private void reopen() throws Exception {
        close();
        open(new Configuration());
    }
}
