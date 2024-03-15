package com.example.camunda8demo.worker

import com.example.camunda8demo.service.SlaService
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

@Component
class RoutingWorkers(
    private val zeebe: ZeebeClient,
    private val slaService: SlaService
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @JobWorker(type = "SendRequestToClient", autoComplete = false)
    fun handleSendRequestToClient(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable key: String
    ) = withJobHandling(jobClient, job) {
        log.debug("=============== SendRequestToClient, processId: $processId, requestToClientId: $key")
    }

    @JobWorker(type = "HandleClientResponse", autoComplete = false)
    fun handleHandleClientResponse(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable key: String
    ) = withJobHandling(jobClient, job) {
        log.debug("=============== HandleClientResponse, processId: $processId, requestToClientId: $key")
    }

    /**
     *  Будет использоваться как месседж-воркер за пределами схемы SLA_PROCESS.
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
            slaService.pause(slaProcessId)
        } catch (e: IllegalStateException) {
            log.warn("=============== WARN SendPauseSlaMsg: $e, slaProcessId: $slaProcessId, processInstanceKey: ${job.processInstanceKey}")
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
     *  Будет использоваться как месседж-воркер за пределами схемы SLA_PROCESS.
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
            slaService.resume(slaProcessId)
        } catch (e: IllegalStateException) {
            log.warn("=============== WARN SendResumeSlaMsg: already resumed, slaProcessId: $slaProcessId")
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
        val savedSla = slaService.create(processId)
        log.info("=============== CreateSLA: $savedSla")

        mapOf("sla" to savedSla)
    }

    @JobWorker(type = "CreateRecalculatedSLA", autoComplete = false)
    fun handleCreateRecalculatedSLA(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        val savedSla = slaService.recalculate(processId)
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
            slaService.complete(processId)
        else
            slaService.get(processId)
        log.info("=============== CheckSla: $savedSla")

        mapOf("sla" to savedSla)
    }

    @JobWorker(type = "SlaExpired", autoComplete = false)
    fun handleSlaExpired(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        val savedSla = slaService.expire(processId)
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

    @JobWorker(type = "TaskOne", autoComplete = false)
    fun handleTaskOne(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        log.info("=============== TaskOne")
    }

    @JobWorker(type = "TaskTwo", autoComplete = false)
    fun handleTaskTwo(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String
    ) = withJobHandling(jobClient, job) {
        log.info("=============== TaskTwo")
    }
}
