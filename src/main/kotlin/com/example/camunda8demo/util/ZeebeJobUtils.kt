package com.example.camunda8demo.util

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.response.CompleteJobResponse
import io.camunda.zeebe.client.api.response.FailJobResponse
import io.camunda.zeebe.client.api.worker.JobClient
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage

object ZeebeJobUtils {
    private const val JOB_INFO_MSG = "jobKey: %d, processInstanceKey: %d"
    private const val JOB_ERROR_MSG = "$JOB_INFO_MSG, error: %d"
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
                    log.error(
                        "$LOG_PREFIX Could not complete job ${job.type}, " +
                            JOB_ERROR_MSG.format(job.key, job.processInstanceKey, err.toString()),
                        err
                    )
                }
            }
    }

    fun sendFailJob(
        e: Exception,
        jobClient: JobClient,
        job: ActivatedJob,
        retries: Int = (job.retries - 1)
    ): CompletionStage<FailJobResponse> {
        val errorCode = e.javaClass.simpleName

        return jobClient
            .newFailCommand(job)
            .retries(retries)
            .errorMessage(e.toString())
            .send()
            .whenComplete { _, err ->
                if (err == null) {
                    log.warn(
                        "$LOG_PREFIX Failed job ${job.type} because of '$errorCode', " +
                            JOB_INFO_MSG.format(job.key, job.processInstanceKey) +
                            ", remaining retries: $retries"
                    )
                } else {
                    log.error(
                        "$LOG_PREFIX Could not fail job ${job.type} on '$errorCode', " +
                            JOB_ERROR_MSG.format(job.key, job.processInstanceKey, err.toString()),
                        err
                    )
                }
            }
    }

    fun sendJobError(
        e: Exception,
        jobClient: JobClient,
        job: ActivatedJob
    ): CompletionStage<Void> {
        val errorCode = e.javaClass.simpleName

        return jobClient.newThrowErrorCommand(job.key)
            .errorCode(errorCode)
            .errorMessage(e.toString())
            .send()
            .whenComplete { _, err ->
                if (err == null) {
                    log.warn(
                        "$LOG_PREFIX Error '$errorCode' in job ${job.type} sent to broker, " +
                            JOB_INFO_MSG.format(job.key, job.processInstanceKey)
                    )
                } else {
                    log.error(
                        "$LOG_PREFIX Could not send error '$errorCode' in job ${job.type} to broker, " +
                            JOB_ERROR_MSG.format(job.key, job.processInstanceKey, err.toString()),
                        err
                    )
                }
            }
    }

    fun logStartJob(job: ActivatedJob) {
        log.info("$LOG_PREFIX Start ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}")
    }

    fun logEndJob(job: ActivatedJob) {
        log.info("$LOG_PREFIX End ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}")
    }

    fun logJobError(job: ActivatedJob, e: Throwable) {
        log.error(
            "$LOG_PREFIX Error occurred in ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}",
            e
        )
    }
}
