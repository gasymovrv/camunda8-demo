package com.example.camunda8demo

import com.example.camunda8demo.util.ZeebeJobUtils
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.JobWorker
import io.camunda.zeebe.spring.client.annotation.VariablesAsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class Workers {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    @JobWorker(type = "simple_task1", autoComplete = false)
    fun handleSimpleTask1(jobClient: JobClient, job: ActivatedJob, @VariablesAsType vars: Vars) {
        log.info("=============== execute simple_task1, key=${vars.key}...")
        job.print()
        ZeebeJobUtils.sendCompleteJob(jobClient, job, vars)
    }

    @JobWorker(type = "simple_task2", autoComplete = true)
    fun handleSimpleTask2(job: ActivatedJob) {
        log.info("=============== execute simple_task2...")
        job.print()
        log.info("=============== finish simple_task2 -----\n")
    }

    @JobWorker(type = "retryable_task", autoComplete = false)
    fun handleRetryableTask(jobClient: JobClient, job: ActivatedJob) = runBlocking {
        log.info("=============== execute retryable_task, instance: ${job.processInstanceKey} ==================")
        if (job.retries > 1) { // emulate fails
            val remainAttempts = job.retries - 1
            delay(2000)
            jobClient
                .newFailCommand(job)
                .retries(remainAttempts)
                .errorMessage("everything is bad... we will see it in incident when the retries are over")
                .send()
                .whenComplete { _, _ ->
                    log.info("instance '${job.processInstanceKey}' - failed attempt, remain attempts: $remainAttempts")
                }
        } else {
            ZeebeJobUtils.sendCompleteJob(jobClient, job)
        }
        log.info("=============== finish retryable_task, instance: ${job.processInstanceKey} ==================")
    }

    @JobWorker(type = "long_task", autoComplete = false)
    fun handleLongTask(jobClient: JobClient, job: ActivatedJob, @VariablesAsType vars: Vars) = runBlocking {
        log.info("=============== execute long_task, key=${vars.key}...")
        val deadline = LocalDateTime.ofInstant(Instant.ofEpochMilli(job.deadline), ZoneId.systemDefault())
        val deadlineStr = deadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        if (deadline.isBefore(LocalDateTime.now()))
            log.warn("=============== job.deadline has expired: $deadlineStr")
        else
            log.info("=============== job.deadline has not expired yet: $deadlineStr")

        Thread.sleep(30000)

        ZeebeJobUtils.sendCompleteJob(jobClient, job, vars)
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
