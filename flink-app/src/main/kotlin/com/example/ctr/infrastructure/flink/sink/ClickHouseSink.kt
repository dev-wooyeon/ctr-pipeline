package com.example.ctr.infrastructure.flink.sink

import com.example.ctr.config.ClickHouseProperties
import com.example.ctr.domain.model.CTRResult
import java.sql.PreparedStatement
import org.apache.flink.connector.jdbc.JdbcConnectionOptions
import org.apache.flink.connector.jdbc.JdbcExecutionOptions
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.connector.jdbc.JdbcStatementBuilder
import org.apache.flink.streaming.api.functions.sink.SinkFunction

class ClickHouseSink(private val clickHouseProperties: ClickHouseProperties) {

    fun createSink(): SinkFunction<CTRResult> =
            JdbcSink.sink(
                    "INSERT INTO ctr_results_raw (product_id, ctr, impressions, clicks, window_start, window_end) VALUES (?, ?, ?, ?, ?, ?)",
                    JdbcStatementBuilder<CTRResult> { ps, ctrResult ->
                        bindCtrResult(ps, ctrResult)
                    },
                    JdbcExecutionOptions.builder()
                            .withBatchSize(1000)
                            .withBatchIntervalMs(200)
                            .withMaxRetries(3)
                            .build(),
                    JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                            .withUrl(clickHouseProperties.url)
                            .withDriverName(clickHouseProperties.driver)
                            .build()
            )

    companion object {
        private const val IDX_PRODUCT_ID = 1
        private const val IDX_CTR = 2
        private const val IDX_IMPRESSIONS = 3
        private const val IDX_CLICKS = 4
        private const val IDX_WINDOW_START = 5
        private const val IDX_WINDOW_END = 6

        private fun bindCtrResult(ps: PreparedStatement, ctrResult: CTRResult) {
            ps.setString(IDX_PRODUCT_ID, ctrResult.productId)
            ps.setDouble(IDX_CTR, ctrResult.ctr)
            ps.setLong(IDX_IMPRESSIONS, ctrResult.impressions)
            ps.setLong(IDX_CLICKS, ctrResult.clicks)
            ps.setLong(IDX_WINDOW_START, ctrResult.windowStart)
            ps.setLong(IDX_WINDOW_END, ctrResult.windowEnd)
        }
    }
}
