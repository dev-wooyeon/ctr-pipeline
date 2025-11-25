package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.domain.model.CTRResult;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class DuckDBSink {

    private final String duckdbUrl;

    public DuckDBSink(@Value("${duckdb.url}") String duckdbUrl) {
        this.duckdbUrl = duckdbUrl;
    }

    public RichSinkFunction<CTRResult> createSink() {
        return new DuckDBRichSink(duckdbUrl);
    }

    public static class DuckDBRichSink extends RichSinkFunction<CTRResult> {
        private final String duckdbUrl;
        private transient Connection connection;
        private transient PreparedStatement preparedStatement;

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
        }

        @Override
        public void invoke(CTRResult value, Context context) throws Exception {
            preparedStatement.setString(1, value.getProductId());
            preparedStatement.setDouble(2, value.getCtr());
            preparedStatement.setLong(3, value.getImpressions());
            preparedStatement.setLong(4, value.getClicks());
            preparedStatement.setLong(5, value.getWindowStart());
            preparedStatement.setLong(6, value.getWindowEnd());
            preparedStatement.execute();
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
    }
}
