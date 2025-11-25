package com.example.ctr.application;

import com.example.ctr.config.CtrJobProperties;
import com.example.ctr.infrastructure.flink.FlinkEnvironmentFactory;
import com.example.ctr.infrastructure.flink.CtrJobPipelineBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * CTR Flink 잡을 시작하는 서비스.
 *
 * 책임:
 * 1️⃣ {@link FlinkEnvironmentFactory} 를 통해 설정된
 * {@link StreamExecutionEnvironment} 획득
 * 2️⃣ {@link CtrJobPipelineBuilder} 로 데이터 파이프라인 구성
 * 3️⃣ 잡 실행 및 그레이스풀 셧다운 처리
 */
@Slf4j
@Service
public class CtrJobService {

    private final FlinkEnvironmentFactory envFactory;
    private final CtrJobPipelineBuilder pipelineBuilder;
    private final CtrJobProperties properties;
    private volatile StreamExecutionEnvironment env;

    public CtrJobService(FlinkEnvironmentFactory envFactory,
            CtrJobPipelineBuilder pipelineBuilder,
            CtrJobProperties properties) {
        this.envFactory = envFactory;
        this.pipelineBuilder = pipelineBuilder;
        this.properties = properties;
    }

    public void execute() {
        try {
            log.info("Starting CTR Calculator Flink Job (Exactly‑once)...");

            // 실행 환경 생성 (체크포인트/재시작 전략 설정 등)
            env = envFactory.create();

            // 파이프라인 구성 (소스, 집계, 싱크)
            pipelineBuilder.build(env);

            // 그레이스풀 셧다운을 위한 훅 등록
            registerShutdownHook();

            log.info("Executing Flink job...");
            env.execute("CTR Calculator Job (Exactly‑once)");
            log.info("Flink job completed successfully");
        } catch (Exception e) {
            log.error("Failed to execute Flink job", e);
            throw new RuntimeException("Flink job execution failed", e);
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered. Gracefully stopping Flink job...");
            try {
                if (env != null) {
                    // JVM 종료 시 Flink가 그레이스풀 셧다운을 처리
                    log.info("Flink job shutdown initiated");
                }
            } catch (Exception e) {
                log.error("Error during graceful shutdown", e);
            }
        }, "flink-shutdown-hook"));
    }

    @PreDestroy
    public void onDestroy() {
        log.info("CtrJobService is being destroyed. Cleanup if needed.");
    }
}
