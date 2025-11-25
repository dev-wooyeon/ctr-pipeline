package com.example.ctr.infrastructure.flink.sink;

import com.example.ctr.config.DuckDBProperties;
import com.example.ctr.domain.model.CTRResult;
import com.example.ctr.infrastructure.flink.sink.impl.DuckDBRichSink;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.springframework.stereotype.Component;

@Component
public class DuckDBSink {

    private final DuckDBProperties duckDBProperties;

    public DuckDBSink(DuckDBProperties duckDBProperties) {
        this.duckDBProperties = duckDBProperties;
    }

    public RichSinkFunction<CTRResult> createSink() {
        return new DuckDBRichSink(duckDBProperties.getUrl());
    }
}
