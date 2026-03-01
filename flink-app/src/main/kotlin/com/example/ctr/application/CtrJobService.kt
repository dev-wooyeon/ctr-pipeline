package com.example.ctr.application

import com.example.ctr.infrastructure.flink.CtrJobPipelineBuilder
import com.example.ctr.infrastructure.flink.FlinkEnvironmentFactory
import org.apache.flink.client.program.JobClient
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class CtrJobService(
    private val envFactory: FlinkEnvironmentFactory,
    private val pipelineBuilder: CtrJobPipelineBuilder
) {

    private val log = LoggerFactory.getLogger(CtrJobService::class.java)
    @Volatile
    private var env: StreamExecutionEnvironment? = null
    @Volatile
    private var jobClient: JobClient? = null

    fun execute() {
        try {
            log.info("Starting CTR Calculator Flink Job...")

            val executionEnv = envFactory.create()
            env = executionEnv
            pipelineBuilder.build(executionEnv)
            registerShutdownHook()

            log.info("Executing Flink job...")
            val client = executionEnv.executeAsync("CTR Calculator Job")
            this.jobClient = client
            log.info("Flink job submitted: ${client.jobID}")

            log.info("Waiting for streaming job to finish...")
            client.jobExecutionResult.get()
            log.info("Flink job completed")
        } catch (e: Exception) {
            log.error("Failed to execute Flink job", e)
            throw RuntimeException("Flink job execution failed", e)
        }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread({
            log.info("Shutdown hook triggered. Gracefully stopping Flink job...")
            try {
                val client = jobClient
                if (client == null) {
                    log.warn("No running job client found for graceful shutdown")
                    return@Thread
                }

                try {
                    client.cancel().get(10, TimeUnit.SECONDS)
                    log.info("Flink job cancel request completed: ${client.jobID}")
                } catch (cancelEx: Exception) {
                    log.error("Failed to cancel Flink job cleanly", cancelEx)
                }
            } catch (e: Exception) {
                log.error("Error during graceful shutdown", e)
            }
        }, "flink-shutdown-hook"))
    }
}
