package com.example.camunda8demo

import com.example.camunda8demo.util.ZeebeJobUtils
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.Variable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

@Async
@Component
class RouterSlaWorkers {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val remainingMsMap = ConcurrentHashMap<String, Long>()
    private val spentMsMap = ConcurrentHashMap<String, Long>()

    // Emulate optimistic lock
    private val slaPauseVersionMap = ConcurrentHashMap<String, Int>()
    private val sla = 60L
    private val recalcSla = 120L

    @JobWorker(type = "SendRequestToClient", autoComplete = false)
    fun handleSendRequestToClient(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable requestToClientId: String
    ): Future<*> {
        log.info("=============== SendRequestToClient, requestToClientId: $requestToClientId")

        return ZeebeJobUtils.sendCompleteJob(jobClient, job)
    }

    @JobWorker(type = "CreateSLA", autoComplete = false)
    fun handleCreateSLA(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ): Future<*> {
        log.info("=============== CreateSLA")
        slaPauseVersionMap[processId] = 0

        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val warnDate = now.plusSeconds(sla / 2)
        val expirationDate = now.plusSeconds(sla)

        return ZeebeJobUtils.sendCompleteJob(
            jobClient,
            job,
            mapOf(
                "warnDate" to warnDate.format(pattern),
                "expirationDate" to expirationDate.format(pattern),
                "startTime" to LocalDateTime.now().format(pattern)
            )
        )
    }

    @JobWorker(type = "CreateRecalculatedSLA", autoComplete = false)
    fun handleCreateRecalculatedSLA(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ): Future<*> {
        log.info("=============== CreateRecalculatedSLA")
        slaPauseVersionMap[processId] = 0

        val spentMs = spentMsMap[processId]!!

        val warnDate = Instant.now().plusSeconds(recalcSla / 2).minusMillis(spentMs)
        val expirationDate = Instant.now().plusSeconds(recalcSla).minusMillis(spentMs)

        return ZeebeJobUtils.sendCompleteJob(
            jobClient,
            job,
            mapOf(
                "warnDate" to LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
                "expirationDate" to LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
                "startTime" to LocalDateTime.now().format(pattern)
            )
        )
    }

    @JobWorker(type = "ResumeSla", autoComplete = false)
    fun handleResumeSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ): Future<*> {
        log.info("=============== ResumeSla")
        slaPauseVersionMap[processId] = 0

        val remainingMs = remainingMsMap[processId]!!

        val warnDate = Instant.now().plusMillis(remainingMs / 2)
        val expirationDate = Instant.now().plusMillis(remainingMs)

        return ZeebeJobUtils.sendCompleteJob(
            jobClient,
            job,
            mapOf(
                "warnDate" to LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
                "expirationDate" to LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
                "startTime" to LocalDateTime.now().format(pattern)
            )
        )
    }

    @JobWorker(type = "ChangeSla", autoComplete = false)
    fun handleChangeSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable startTime: LocalDateTime,
    ): Future<*> {
        spentMsMap[processId] = Instant.now().toEpochMilli() - startTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        log.info("=============== ChangeSla")

        return ZeebeJobUtils.sendCompleteJob(jobClient, job)
    }

    @JobWorker(type = "PauseSla", autoComplete = false)
    fun handlePauseSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable expirationDate: LocalDateTime
    ): Future<*> {
        val pauseVersion = slaPauseVersionMap.computeIfPresent(processId) { _, v -> v + 1 }

        val slaShouldPause: Boolean
        if (pauseVersion == 1) {
            remainingMsMap[processId] =
                expirationDate.toInstant(ZoneOffset.UTC).toEpochMilli() - Instant.now().toEpochMilli()
            slaShouldPause = true
            log.info("=============== PauseSla: done, processId: $processId")
        } else {
            slaShouldPause = false
            log.info("=============== PauseSla: already paused (version: $pauseVersion), processId: $processId")
        }

        return ZeebeJobUtils.sendCompleteJob(jobClient, job, mapOf("slaShouldPause" to slaShouldPause))
    }

    @JobWorker(type = "SlaWarn", autoComplete = false)
    fun handleSlaWarn(jobClient: JobClient, job: ActivatedJob, @Variable processId: String): Future<*> {
        log.info("=============== SlaWarn")

        return ZeebeJobUtils.sendCompleteJob(jobClient, job)
    }

    @JobWorker(type = "CheckSla", autoComplete = false)
    fun handleCheckSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable completed: Boolean? = null
    ): Future<*> {
        log.info("=============== CheckSla")

        return ZeebeJobUtils.sendCompleteJob(
            jobClient,
            job,
            mapOf(
                "completed" to (completed ?: false)
            )
        )
    }
}
