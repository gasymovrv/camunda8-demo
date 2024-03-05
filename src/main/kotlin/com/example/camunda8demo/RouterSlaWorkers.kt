package com.example.camunda8demo

import com.example.camunda8demo.util.RetryableJobException
import com.example.camunda8demo.util.ZeebeJobUtils.withJobHandling
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.Variable
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

@Component
class RouterSlaWorkers(private val zeebe: ZeebeClient) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val slaDatabase = ConcurrentHashMap<String, Sla>()
    private val sla = 60L
    private val recalcSla = 120L

    @JobWorker(type = "SendRequestToClient", autoComplete = false)
    fun handleSendRequestToClient(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable key: String
    ) = withJobHandling(jobClient, job) {
        log.info("=============== SendRequestToClient, processId: $processId, requestToClientId: $key")
    }

    @JobWorker(type = "HandleClientResponse", autoComplete = false)
    fun handleHandleClientResponse(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable key: String
    ) = withJobHandling(jobClient, job) {
        log.info("=============== HandleClientResponse, processId: $processId, requestToClientId: $key")
    }

    /**
     *  Будет использоваться месседж-воркер за пределами схемы SLA_PROCESS.
     *  Лучше делать так чтобы избежать отправки лишних сообщений на паузу SLA когда он в некорректном статусе.
     *  А также не придется усложнять схему SLA_PROCESS лишним event-subprocess-ом
     */
    @JobWorker(type = "SendPauseSlaMsg", autoComplete = false)
    fun handleSendPauseSlaMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable slaProcessId: String
    ) = withJobHandling(jobClient, job) {
        val pausedSla = try {
            slaDatabase.computeIfPresent(slaProcessId) { k, v ->
                if (v.status == SlaStatus.PAUSED)
                    throw IllegalStateException("SLA already paused")

                val expirationDate = v.expirationDate ?: throw IllegalStateException("SLA expirationDate is null")
                val newExpirationDate = LocalDateTime.parse(expirationDate, pattern)
                val remainingMs =
                    newExpirationDate.toInstant(ZoneOffset.UTC).toEpochMilli() - Instant.now().toEpochMilli()

                Sla(
                    id = k,
                    status = SlaStatus.PAUSED,
                    remainingMs = remainingMs
                )
            } ?: throw RetryableJobException("Not found SLA, maybe it hasn't created yet")
        } catch (e: IllegalStateException) {
            log.warn("=============== ERROR SendPauseSlaMsg: $e, slaProcessId: $slaProcessId, processInstanceKey: ${job.processInstanceKey}")
            null
        }

        if (pausedSla != null) {
            zeebe.newPublishMessageCommand()
                .messageName("PAUSE_SLA")
                .correlationKey(slaProcessId)
                .variables(mapOf("sla" to pausedSla))
                .send()
                .thenAccept { _ ->
                    log.info("=============== SendPauseSlaMsg: sent, msgName = 'PAUSE_SLA', correlationKey = $slaProcessId")
                }
                .await()
        }
        return@withJobHandling
    }

    /**
     *  Будет использоваться месседж-воркер за пределами схемы SLA_PROCESS.
     *  Лучше делать так чтобы избежать отправки лишних сообщений на возобновление SLA когда он в некорректном статусе.
     *  А также не придется усложнять схему SLA_PROCESS лишним event-subprocess-ом
     */
    @JobWorker(type = "SendResumeSlaMsg", autoComplete = false)
    fun handleSendResumeSlaMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable slaProcessId: String
    ) = withJobHandling(jobClient, job) {
        val resumedSla = try {
            slaDatabase.computeIfPresent(slaProcessId) { k, v ->
                if (v.status != SlaStatus.PAUSED)
                    throw IllegalStateException("SLA already resumed")

                val remainingMs = v.remainingMs ?: throw IllegalStateException("SLA remainingMs is null")
                val warnDate = Instant.now().plusMillis(remainingMs / 2)
                val expirationDate = Instant.now().plusMillis(remainingMs)

                Sla(
                    id = k,
                    status = SlaStatus.RUNNING,
                    warnDate = LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
                    expirationDate = LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
                    startTime = LocalDateTime.now().format(pattern),
                )
            } ?: throw RetryableJobException("Not found SLA, maybe it hasn't created yet")
        } catch (e: IllegalStateException) {
            log.warn("=============== SendResumeSlaMsg: already resumed, slaProcessId: $slaProcessId")
            null
        }

        if (resumedSla != null) {
            zeebe.newPublishMessageCommand()
                .messageName("RESUME_SLA")
                .correlationKey(slaProcessId)
                .variables(mapOf("sla" to resumedSla))
                .send()
                .thenAccept { _ ->
                    log.info("=============== SendResumeSlaMsg: sent, msgName = 'RESUME_SLA', correlationKey = $slaProcessId")
                }
                .await()
        }
        return@withJobHandling
    }

// Сделать по аналогии с handleSendPauseSlaMsg и handleSendResumeSlaMsg

//    @JobWorker(type = "ChangeSla", autoComplete = false)
//    fun handleChangeSla(
//        jobClient: JobClient,
//        job: ActivatedJob,
//        @Variable processId: String,
//    ) = withJobHandling(jobClient, job) {
//        val savedSla = slaDatabase.computeIfPresent(processId) { k, v ->
//            val startTime = LocalDateTime.parse(v.startTime, pattern)
//            val spentMs = Instant.now().toEpochMilli() - startTime.toInstant(ZoneOffset.UTC).toEpochMilli()
//
//            Sla(
//                id = k,
//                status = SlaStatus.CHANGED,
//                spentMs = spentMs
//            )
//        }!!
//        log.info("=============== ChangeSla: $savedSla")
//
//        mapOf("sla" to sla)
//    }

    @JobWorker(type = "CreateSLA", autoComplete = false)
    fun handleCreateSLA(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val warnDate = now.plusSeconds(sla / 2)
        val expirationDate = now.plusSeconds(sla)

        val savedSla = Sla(
            id = processId,
            status = SlaStatus.RUNNING,
            warnDate = warnDate.format(pattern),
            expirationDate = expirationDate.format(pattern),
            startTime = LocalDateTime.now().format(pattern),
        )
        slaDatabase[processId] = savedSla
        log.info("=============== CreateSLA: $savedSla")

        mapOf("sla" to savedSla)
    }

    @JobWorker(type = "CreateRecalculatedSLA", autoComplete = false)
    fun handleCreateRecalculatedSLA(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        val savedSla = slaDatabase.computeIfPresent(processId) { k, v ->
            if (v.status != SlaStatus.CHANGED) throw IllegalStateException("SLA '$v' must be in status CHANGED")
            val spentMs = v.spentMs!!

            val warnDate = Instant.now().plusSeconds(recalcSla / 2).minusMillis(spentMs)
            val expirationDate = Instant.now().plusSeconds(recalcSla).minusMillis(spentMs)

            Sla(
                id = k,
                status = SlaStatus.RUNNING,
                warnDate = LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
                expirationDate = LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
                startTime = LocalDateTime.now().format(pattern),
            )
        }!!
        log.info("=============== CreateRecalculatedSLA: $savedSla")

        mapOf("sla" to savedSla)
    }

    @JobWorker(type = "CheckSla", autoComplete = false)
    fun handleCheckSla(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable completed: Boolean? = null
    ) = withJobHandling(jobClient, job) {
        val savedSla = if (completed == true)
            slaDatabase.computeIfPresent(processId) { _, v -> v.copy(status = SlaStatus.COMPLETED) }!!
        else
            slaDatabase[processId]
        log.info("=============== CheckSla: $savedSla")

        mapOf("sla" to savedSla)
    }

    @JobWorker(type = "SlaExpired", autoComplete = false)
    fun handleSlaExpired(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        val savedSla = slaDatabase.computeIfPresent(processId) { _, v -> v.copy(status = SlaStatus.EXPIRED) }!!
        log.info("=============== SlaExpired: $savedSla")

        mapOf("sla" to savedSla)
    }

    @JobWorker(type = "SlaWarn", autoComplete = false)
    fun handleSlaWarn(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        log.info("=============== SlaWarn")
    }

    data class Sla(
        var id: String,
        var status: SlaStatus,
        var expirationDate: String? = null,
        var warnDate: String? = null,
        var startTime: String? = null,
        var remainingMs: Long? = null,
        var spentMs: Long? = null,
    )

    enum class SlaStatus {
        RUNNING, PAUSED, EXPIRED, COMPLETED, CHANGED
    }
}
