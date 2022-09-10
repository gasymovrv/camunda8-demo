package com.example.camunda8demo

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class Workers {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    @ZeebeWorker(type = "simple_task1", autoComplete = true)
    fun handleSimpleTask1(job: ActivatedJob) {
        println("execute simple_task 1...")
        job.print()
        println("----- finish simple_task1 -----\n")
    }

    @ZeebeWorker(type = "simple_task2", autoComplete = true)
    fun handleSimpleTask2(job: ActivatedJob) {
        println("execute simple_task 2...")
        job.print()
        println("----- finish simple_task2 -----\n")
    }

    @ZeebeWorker(type = "retryable_task")
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
            jobClient.newCompleteCommand(job)
                .send()
                .whenComplete { _, err -> callBack(job, err) }
        }
        log.info("=============== finish retryable_task, instance: ${job.processInstanceKey} ==================")
    }

    @ZeebeWorker(type = "long_task", autoComplete = true)
    fun handleLongTask(job: ActivatedJob) {
        println("execute long_task...")
        Thread.sleep(60000)
        println("----- finish long_task -----\n")
    }

    private fun ActivatedJob.print() {
        println("bpmnProcessId = $bpmnProcessId")
        println("key = $key")
        println("elementId = $elementId")
        println("variables = $variablesAsMap")
        println("customHeaders = $customHeaders")
    }

    private fun callBack(job: ActivatedJob, err: Throwable?) {
        if (err == null) {
            println("${job.type} with job id '${job.key}' completed successfully")
        } else {
            println("${job.type} with job id '${job.key}' completion failed: $err")
        }
    }
}
