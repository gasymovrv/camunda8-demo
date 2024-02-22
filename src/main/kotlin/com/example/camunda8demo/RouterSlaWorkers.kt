package com.example.camunda8demo

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.Variable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap


@Component
class RouterSlaWorkers {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val pattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private val remainingMsMap: MutableMap<String, Long> = ConcurrentHashMap()
    private val spentMsMap: MutableMap<String, Long> = ConcurrentHashMap()
    private val sla = 60L
    private val recalcSla = 120L

    @JobWorker(type = "CreateSLA")
    suspend fun handleCreateSLA(jobClient: JobClient, job: ActivatedJob): Map<String, Any> {
        log.info("=============== CreateSLA")

        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val warnDate = now.plusSeconds(sla / 2)
        val expirationDate = now.plusSeconds(sla)

        return mapOf(
            "warnDate" to warnDate.format(pattern),
            "expirationDate" to expirationDate.format(pattern),
            "startTime" to LocalDateTime.now().format(pattern)
        )
    }

    @JobWorker(type = "CreateRecalculatedSLA")
    fun handleCreateRecalculatedSLA(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ): Map<String, Any> {
        log.info("=============== CreateRecalculatedSLA")
        val spentMs = spentMsMap[processId]!!

        val warnDate = Instant.now().plusSeconds(recalcSla / 2).minusMillis(spentMs)
        val expirationDate = Instant.now().plusSeconds(recalcSla).minusMillis(spentMs)

        return mapOf(
            "warnDate" to LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
            "expirationDate" to LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
            "startTime" to LocalDateTime.now().format(pattern)
        )
    }

    @JobWorker(type = "ResumeSla")
    fun handleResumeSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ): Map<String, Any> {
        log.info("=============== ResumeSla")
        val remainingMs = remainingMsMap[processId]!!

        val warnDate = Instant.now().plusMillis(remainingMs / 2)
        val expirationDate = Instant.now().plusMillis(remainingMs)

        return mapOf(
            "warnDate" to LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
            "expirationDate" to LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
            "startTime" to LocalDateTime.now().format(pattern)
        )
    }

    @JobWorker(type = "ChangeSla")
    fun handleChangeSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable startTime: LocalDateTime,
    ) {
        spentMsMap[processId] = Instant.now().toEpochMilli() - startTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        log.info("=============== ChangeSla")
    }

    @JobWorker(type = "PauseSla")
    fun handlePauseSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable expirationDate: LocalDateTime
    ) {
        remainingMsMap[processId] =
            expirationDate.toInstant(ZoneOffset.UTC).toEpochMilli() - Instant.now().toEpochMilli()
        log.info("=============== PauseSla")
    }

    @JobWorker(type = "SlaWarn")
    fun handleSlaWarn(jobClient: JobClient, job: ActivatedJob, @Variable processId: String) {
        log.info("=============== SlaWarn")
    }

    @JobWorker(type = "CheckSla")
    fun handleCheckSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable completed: Boolean? = null
    ): Map<String, Any> {
        log.info("=============== CheckSla")
        return mapOf(
            "completed" to (completed ?: false)
        )
    }
}
