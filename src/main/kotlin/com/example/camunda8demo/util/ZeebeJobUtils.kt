package com.example.camunda8demo.util

import com.example.camunda8demo.exception.ZeebeIncident
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.response.CompleteJobResponse
import io.camunda.zeebe.client.api.response.FailJobResponse
import io.camunda.zeebe.client.api.worker.JobClient
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CompletionStage

object ZeebeJobUtils {
    private const val JOB_INFO_MSG = "job id: %d, instance id: %d"
    private const val LOG_PREFIX = "====="

    private val log = LoggerFactory.getLogger(javaClass)

    fun sendCompleteJob(
        jobClient: JobClient,
        job: ActivatedJob,
        vars: Any? = null
    ): CompletionStage<CompleteJobResponse> {
        val completeCommandAction = jobClient.newCompleteCommand(job)
        if (vars !is Unit && vars != null) {
            completeCommandAction.variables(vars)
        }
        return completeCommandAction.send()
            .whenComplete { _, err ->
                if (err == null) {
                    log.info(
                        "$LOG_PREFIX Completed job ${job.type}, " +
                            JOB_INFO_MSG.format(job.key, job.processInstanceKey)
                    )
                } else {
                    log.error("$LOG_PREFIX Could not complete job '${job.type}' with key '${job.key}': ${err?.message}")
                }
            }
    }

    fun sendFailJob(
        e: Exception,
        jobClient: JobClient,
        job: ActivatedJob,
        retries: Int = (job.retries - 1)
    ): CompletionStage<FailJobResponse> {
        val stringWriter = StringWriter()
        e.printStackTrace(PrintWriter(stringWriter))
        return jobClient
            .newFailCommand(job)
            .retries(retries)
            .errorMessage(stringWriter.toString())
            .send()
            .whenComplete { _, err ->
                if (err == null) {
                    log.info(
                        "$LOG_PREFIX Failed job ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}" +
                            ", remaining retries: $retries"
                    )
                } else {
                    log.error("$LOG_PREFIX Could not fail job '${job.type}' with key '${job.key}': ${err?.message}")
                }
            }
    }

    fun sendJobError(
        jobClient: JobClient,
        job: ActivatedJob,
        zeebeIncident: ZeebeIncident
    ): CompletionStage<Void> {
        return jobClient.newThrowErrorCommand(job.key)
            .errorCode(zeebeIncident.code)
            .errorMessage(zeebeIncident.message)
            .send()
            .whenComplete { _, err ->
                if (err == null) {
                    log.info(
                        "$LOG_PREFIX Error '${zeebeIncident.code}' sent to broker, " +
                            JOB_INFO_MSG.format(job.key, job.processInstanceKey)
                    )
                } else {
                    log.error(
                        "$LOG_PREFIX Could not send error '${zeebeIncident.code}' to broker, " +
                            JOB_INFO_MSG.format(job.key, job.processInstanceKey),
                        err
                    )
                }
            }
    }
}
