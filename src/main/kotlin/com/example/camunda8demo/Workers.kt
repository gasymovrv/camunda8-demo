package com.example.camunda8demo

import com.example.camunda8demo.util.ZeebeJobUtils
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.response.PublishMessageResponse
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.VariablesAsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Future

@Component
class Workers(private val zeebe: ZeebeClient) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    @JobWorker(type = "simple_task1", autoComplete = false)
    fun handleSimpleTask1(jobClient: JobClient, job: ActivatedJob, @VariablesAsType vars: Vars) {
        log.info("=============== execute simple_task1, vars: ${job.variables}...")
        job.print()
        ZeebeJobUtils.sendCompleteJob(jobClient, job, vars)
    }

    @JobWorker(type = "simple_task2", autoComplete = true)
    fun handleSimpleTask2(job: ActivatedJob) {
        log.info("=============== execute simple_task2, vars: ${job.variables}...")
        job.print()
        log.info("=============== finish simple_task2 -----\n")
    }

    @JobWorker(type = "retryable_task", autoComplete = false)
    fun handleRetryableTask(jobClient: JobClient, job: ActivatedJob) = runBlocking {
        log.info("=============== execute retryable_task, instance: ${job.processInstanceKey}, vars: ${job.variables} ==================")

        val delay: Long = (job.variablesAsMap["delay"] as Int? ?: 30000).toLong()
        val fail = job.variablesAsMap["fail"] as Boolean? ?: false
        val error = job.variablesAsMap["error"] as Boolean? ?: false
        val handleError = job.variablesAsMap["handleError"] as Boolean? ?: false

        val failException =
            RuntimeException("everything is bad... we will see it in incident when the retries are over")
        val errorException =
            if (handleError) HandlingException("specific error to handle in BPMN by code in error boundary event")
            else RuntimeException("everything is much more bad... we will see it in incident because we threw this error")
        val remainAttempts = job.retries - 1

        // If both are true then leave retries==1 and throw error
        if (fail && error) {
            if (remainAttempts > 0) {
                delay(delay)
                ZeebeJobUtils.sendFailJob(failException, jobClient, job, remainAttempts)
            } else {
                ZeebeJobUtils.sendJobError(errorException, jobClient, job)
            }

        } else if (fail) {
            delay(delay)
            ZeebeJobUtils.sendFailJob(failException, jobClient, job, remainAttempts)

        } else if (error) {
            ZeebeJobUtils.sendJobError(errorException, jobClient, job)

        } else {
            ZeebeJobUtils.sendCompleteJob(jobClient, job)
        }

        log.info("=============== finish retryable_task, instance: ${job.processInstanceKey} ==================")
    }

    @JobWorker(type = "long_task", autoComplete = false)
    fun handleLongTask(jobClient: JobClient, job: ActivatedJob, @VariablesAsType vars: Vars) = runBlocking {
        log.info("=============== execute long_task, vars: ${job.variables}...")
        val deadline = LocalDateTime.ofInstant(Instant.ofEpochMilli(job.deadline), ZoneId.systemDefault())
        val deadlineStr = deadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        if (deadline.isBefore(LocalDateTime.now()))
            log.warn("=============== job.deadline has expired: $deadlineStr")
        else
            log.info("=============== job.deadline has not expired yet: $deadlineStr")

        Thread.sleep(30000)

        ZeebeJobUtils.sendCompleteJob(jobClient, job, vars)
    }

    @Async
    @JobWorker(type = "SendMsg")
    fun handleSendMsg(
        jobClient: JobClient,
        job: ActivatedJob,
        @VariablesAsType vars: Map<String, Any>
    ): Future<PublishMessageResponse> {
        val msgName = vars["msgName"] as String
        val correlationKey = vars["correlationKey"] as String
        val others = vars["vars"] ?: mapOf<String, Any>()

        return zeebe.newPublishMessageCommand()
            .messageName(msgName)
            .correlationKey(correlationKey)
            .variables(others)
            .send()
            .whenComplete { _, _ ->
                log.info("=============== SendMsg, msgName = $msgName, correlationKey = $correlationKey, others = $others")
            }.toCompletableFuture()
    }

    private fun ActivatedJob.print() {
        println("bpmnProcessId = $bpmnProcessId")
        println("key = $key")
        println("elementId = $elementId")
        println("variables = $variablesAsMap")
        println("customHeaders = $customHeaders")
    }

    data class Vars(
        val key: String
    )
}

/**
 * Can be handled by error boundary event when error with specific code is thrown
 */
class HandlingException(msg: String) : RuntimeException(msg)
