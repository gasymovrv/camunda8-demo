package com.example.camunda8demo.worker

import com.example.camunda8demo.DATETIME_PATTERN
import com.example.camunda8demo.util.ZeebeJobUtils.withJobHandling
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.VariablesAsType
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Component
class SystemWorkers(private val zeebe: ZeebeClient) {
    val log: Logger = LoggerFactory.getLogger(javaClass)
    val instancesDurationsMs = ConcurrentHashMap<Long, Long>()

    @JobWorker(type = "SendMsg", autoComplete = false)
    fun handleSendMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: Map<String, Any>
    ) = withJobHandling(jobClient, job) {
        val msgName = vars["msgName"] as String
        val correlationKey = vars["correlationKey"] as String
        val others = vars["vars"] ?: mapOf<String, Any>()

        zeebe.newPublishMessageCommand()
            .messageName(msgName)
            .correlationKey(correlationKey)
            .variables(others)
            .send()
            .thenAccept { _ ->
                log.debug("=============== SendMsg, msgName = $msgName, correlationKey = $correlationKey, others = $others")
            }
            .await()
        return@withJobHandling
    }

    @JobWorker(type = "LogStart", autoComplete = false)
    fun handleLogStart(
        jobClient: JobClient,
        job: ActivatedJob
    ) = withJobHandling(jobClient, job) {
        val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATETIME_PATTERN))
        log.debug("========== Instance '${job.bpmnProcessId}' with key '${job.processInstanceKey}' started at $timeStr")

        mapOf("startInstanceTime" to timeStr)
    }

    @JobWorker(type = "LogEnd", autoComplete = false)
    fun handleLogEnd(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable startInstanceTime: LocalDateTime
    ) = withJobHandling(jobClient, job) {
        val time = LocalDateTime.now()
        val executionTime = Instant.now().toEpochMilli() - startInstanceTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        log.debug("========== Instance '${job.bpmnProcessId}' with key '${job.processInstanceKey}' ended at $time. Execution time: $executionTime ms")
        instancesDurationsMs.put(job.processInstanceKey, executionTime)

        return@withJobHandling
    }
}
