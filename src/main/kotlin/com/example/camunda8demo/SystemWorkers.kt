package com.example.camunda8demo

import com.example.camunda8demo.util.ZeebeJobUtils
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.VariablesAsType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

@Async
@Component
class SystemWorkers(private val zeebe: ZeebeClient) {
    val log: Logger = LoggerFactory.getLogger(javaClass)
    val instancesDurationsMs: MutableMap<Long, Long> = ConcurrentHashMap()

//    @JobWorker(type = "SendMsg")
//    fun handleSendMsg(
//        jobClient: JobClient,
//        job: ActivatedJob,
//        @VariablesAsType vars: Map<String, Any>
//    ) = runBlocking {
//        val msgName = vars["msgName"] as String
//        val correlationKey = vars["correlationKey"] as String
//        val others = vars["vars"] ?: mapOf<String, Any>()
//
//        zeebe.newPublishMessageCommand()
//            .messageName(msgName)
//            .correlationKey(correlationKey)
//            .variables(others)
//            .send()
//            .whenComplete { _, _ ->
//                log.info("=============== SendMsg, msgName = $msgName, correlationKey = $correlationKey, others = $others")
//            }.await()
//    }

    @JobWorker(type = "SendMsg", autoComplete = false)
    fun handleSendMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: Map<String, Any>
    ): Future<Void> {
        val msgName = vars["msgName"] as String
        val correlationKey = vars["correlationKey"] as String
        val others = vars["vars"] ?: mapOf<String, Any>()

        return zeebe.newPublishMessageCommand()
            .messageName(msgName)
            .correlationKey(correlationKey)
            .variables(others)
            .send()
            .thenAcceptBoth(
                ZeebeJobUtils.sendCompleteJob(
                    jobClient,
                    job
                )
            ) { _, _ ->
                log.info("=============== SendMsg, msgName = $msgName, correlationKey = $correlationKey, others = $others")
            }
            .toCompletableFuture()
    }

    @JobWorker(type = "LogStart", autoComplete = false)
    fun handleLogStart(
        jobClient: JobClient,
        job: ActivatedJob
    ): Future<*> {
        val timeStr = LocalDateTime.now().format(pattern)
        log.info("========== Instance '${job.bpmnProcessId}' with key '${job.processInstanceKey}' started at $timeStr")

        return ZeebeJobUtils.sendCompleteJob(
            jobClient,
            job,
            mapOf("startInstanceTime" to timeStr)
        )
    }

    @JobWorker(type = "LogEnd", autoComplete = false)
    fun handleLogEnd(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable startInstanceTime: LocalDateTime
    ): Future<*> {
        val time = LocalDateTime.now()
        val executionTime = Instant.now().toEpochMilli() - startInstanceTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        log.info("========== Instance '${job.bpmnProcessId}' with key '${job.processInstanceKey}' ended at $time. Execution time: $executionTime ms")
        instancesDurationsMs[job.processInstanceKey] = executionTime
        log.info("========== Average execution time: ${instancesDurationsMs.values.average()} ms")

        return ZeebeJobUtils.sendCompleteJob(jobClient, job)
    }
}
