package com.example.camunda8demo.worker

import com.example.camunda8demo.service.Sla
import com.example.camunda8demo.service.SlaService
import com.example.camunda8demo.service.SlaType
import com.example.camunda8demo.util.ZeebeJobUtils.withJobHandling
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.CustomHeaders
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.VariablesAsType
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

    /**
     * Воркер схемы ROUTER_PROCESS
     */
    @JobWorker(type = "CreateSlaList", autoComplete = false)
    fun handleCreateSlaList(
        jobClient: JobClient,
        job: ActivatedJob
    ) = withJobHandling(jobClient, job) {
        val savedSlaList = slaService.createRouterSla()
        log.info("=============== CreateSlaList: $savedSlaList")

        mapOf(
            "slaList" to savedSlaList,
            "startWorkSlaCompleted" to false,
            "resolutionSlaCompleted" to false
        )
    }

    /**
     *  Используется как месседж-воркер за пределами схемы SLA_PROCESS.
     *  Лучше делать так чтобы избежать отправки лишних сообщений на паузу SLA когда он в некорректном статусе.
     *  А также не придется усложнять схему SLA_PROCESS лишним event-subprocess-ом
     */
    @JobWorker(type = "SendPauseSlaMsg", autoComplete = false)
    fun handleSendPauseSlaMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: RouterVars,
        @CustomHeaders headers: Map<String, String>
    ) = withJobHandling(jobClient, job) {
        val slaProcessId = vars.slaList.getByType(headers).id

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
                    log.info("=============== SendPauseSlaMsg: sent, msgName = 'PAUSE_SLA', sla = $pausedSla")
                }
                .await()
            mapOf("slaList" to vars.slaList.replaceByType(pausedSla))
        } else {
            null
        }
    }

    /**
     *  Используется как месседж-воркер за пределами схемы SLA_PROCESS.
     *  Лучше делать так чтобы избежать отправки лишних сообщений на возобновление SLA когда он в некорректном статусе.
     *  А также не придется усложнять схему SLA_PROCESS лишним event-subprocess-ом
     */
    @JobWorker(type = "SendResumeSlaMsg", autoComplete = false)
    fun handleSendResumeSlaMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: RouterVars,
        @CustomHeaders headers: Map<String, String>
    ) = withJobHandling(jobClient, job) {
        val slaProcessId = vars.slaList.getByType(headers).id

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
                    log.info("=============== SendResumeSlaMsg: sent, msgName = 'RESUME_SLA', sla = $resumedSla")
                }
                .await()
            mapOf("slaList" to vars.slaList.replaceByType(resumedSla))
        } else {
            null
        }
    }

    /**
     *  Используется как месседж-воркер за пределами схемы SLA_PROCESS.
     *  Лучше делать так чтобы не усложнять схему SLA_PROCESS
     */
    @JobWorker(type = "SendCompleteSlaMsg", autoComplete = false)
    fun handleSendCompleteSlaMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: RouterVars,
        @CustomHeaders headers: Map<String, String>
    ) = withJobHandling(jobClient, job) {
        val slaProcessId = vars.slaList.getByType(headers).id

        val completedSla = slaService.complete(slaProcessId)

        zeebe.newPublishMessageCommand()
            .messageName("COMPLETE_SLA")
            .correlationKey(slaProcessId)
            .variables(mapOf("sla" to completedSla))
            .send()
            .thenAccept { _ ->
                log.info("=============== SendCompleteSlaMsg: sent, msgName = 'COMPLETE_SLA', sla = $completedSla")
            }
            .await()

        val vars = mutableMapOf<String, Any>("slaList" to vars.slaList.replaceByType(completedSla))

        if (completedSla.type == SlaType.START_WORK)
            vars["startWorkSlaCompleted"] = true
        if (completedSla.type == SlaType.RESOLUTION)
            vars["resolutionSlaCompleted"] = true
        vars
    }

    /**
     *  Используется как месседж-воркер за пределами схемы SLA_PROCESS.
     *  Лучше делать так чтобы не усложнять схему SLA_PROCESS
     */
    @JobWorker(type = "SendChangeSlaMsg", autoComplete = false)
    fun handleSendChangeSlaMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: RouterVars,
        @CustomHeaders headers: Map<String, String>
    ) = withJobHandling(jobClient, job) {
        val slaProcessId = vars.slaList.getByType(headers).id

        val changedSla = slaService.change(slaProcessId)

        zeebe.newPublishMessageCommand()
            .messageName("CHANGE_SLA")
            .correlationKey(slaProcessId)
            .variables(mapOf("sla" to changedSla))
            .send()
            .thenAccept { _ ->
                log.info("=============== SendChangeSlaMsg: sent, msgName = 'CHANGE_SLA', sla = $changedSla")
            }
            .await()
        mapOf("slaList" to vars.slaList.replaceByType(changedSla))
    }

    /**
     * Воркер схемы SLA_PROCESS
     */
    @JobWorker(type = "SlaExpired", autoComplete = false)
    fun handleSlaExpired(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: SlaVars,
    ) = withJobHandling(jobClient, job) {
        val savedSla = slaService.expire(vars.sla.id)
        log.info("=============== SlaExpired: $savedSla")

        mapOf("sla" to savedSla)
    }

    /**
     * Воркер схемы SLA_PROCESS
     */
    @JobWorker(type = "SlaWarn", autoComplete = false)
    fun handleSlaWarn(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: SlaVars,
    ) = withJobHandling(jobClient, job) {
        val savedSla = slaService.warn(vars.sla.id)
        log.info("=============== SlaWarn: $savedSla")

        mapOf("sla" to savedSla)
    }

    /**
     * Воркер схемы TASK_PROCESS
     */
    @JobWorker(type = "TaskOne", autoComplete = false)
    fun handleTaskOne(
        jobClient: JobClient,
        job: ActivatedJob
    ) = withJobHandling(jobClient, job) {
        log.info("=============== TaskOne")
    }

    /**
     * Воркер схемы TASK_PROCESS
     */
    @JobWorker(type = "TaskTwo", autoComplete = false)
    fun handleTaskTwo(
        jobClient: JobClient,
        job: ActivatedJob
    ) = withJobHandling(jobClient, job) {
        log.info("=============== TaskTwo")
    }

    /**
     * Воркер схемы TASK_PROCESS
     */
    @JobWorker(type = "CreateTaskSla", autoComplete = false)
    fun handleCreateTaskSla(
        jobClient: JobClient,
        job: ActivatedJob
    ) = withJobHandling(jobClient, job) {
        val savedSlaList = slaService.createTaskSla()
        log.info("=============== CreateTaskSla: $savedSlaList")
        mapOf("slaList" to savedSlaList)
    }

    /**
     * Воркер схемы REQUEST_TO_CLIENT_PROCESS
     */
    @JobWorker(type = "SendRequestToClient", autoComplete = false)
    fun handleSendRequestToClient(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable key: String
    ) = withJobHandling(jobClient, job) {
        log.debug("=============== SendRequestToClient, processId: $processId, requestToClientId: $key")
    }

    /**
     * Воркер схемы REQUEST_TO_CLIENT_PROCESS
     */
    @JobWorker(type = "HandleClientResponse", autoComplete = false)
    fun handleHandleClientResponse(
        jobClient: JobClient,
        job: ActivatedJob,
        @Variable processId: String,
        @Variable key: String
    ) = withJobHandling(jobClient, job) {
        log.debug("=============== HandleClientResponse, processId: $processId, requestToClientId: $key")
    }

    data class RouterVars(
        val slaList: List<Sla>
    )

    data class SlaVars(
        val sla: Sla
    )

    private fun List<Sla>.getByType(headers: Map<String, String>) =
        this.first { it.type.name == headers["slaType"] }

    private fun List<Sla>.replaceByType(updatedSla: Sla) =
        this.map { if (it.type == updatedSla.type) updatedSla else it }
}
