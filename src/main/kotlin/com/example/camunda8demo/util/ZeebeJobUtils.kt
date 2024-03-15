package com.example.camunda8demo.util

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.response.CompleteJobResponse
import io.camunda.zeebe.client.api.response.FailJobResponse
import io.camunda.zeebe.client.api.worker.JobClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

object ZeebeJobUtils {
    private const val JOB_INFO_MSG = "jobKey: %s, processInstanceKey: %s"
    private const val JOB_ERROR_MSG = "$JOB_INFO_MSG, error: %s"
    private const val LOG_PREFIX = "====="

    private val log = LoggerFactory.getLogger(javaClass)

    fun sendCompleteJob(
        jobClient: JobClient,
        job: ActivatedJob,
        vars: Any? = null
    ): CompletableFuture<CompleteJobResponse> {
        val completeCommandAction = jobClient.newCompleteCommand(job)
        if (vars !is Unit && vars != null) {
            completeCommandAction.variables(vars)
        }
        return completeCommandAction.send()
            .whenComplete { _, err ->
                if (err == null) {
                    log.debug(
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
            }.toCompletableFuture()
    }

    fun sendFailJob(
        e: Exception,
        jobClient: JobClient,
        job: ActivatedJob,
        retries: Int = (job.retries - 1)
    ): CompletableFuture<FailJobResponse> {
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
            }.toCompletableFuture()
    }

    fun sendJobError(
        e: Exception,
        jobClient: JobClient,
        job: ActivatedJob
    ): CompletableFuture<Void> {
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
            }.toCompletableFuture()
    }

    private suspend fun failWithDelay(
        e: Exception,
        client: JobClient,
        job: ActivatedJob
    ) {
        val remainRetries = job.retries - 1
        if (remainRetries > 0) {
            // It is required to delay before sending fail,
            // otherwise another job client can start new try without delay
            delay(5000)
        }
        sendFailJob(e, client, job, remainRetries).await()
    }

    fun <T> withJobHandling(
        client: JobClient,
        job: ActivatedJob,
        executeJobAction: suspend () -> T
    ) = runBlocking {
        logStartJob(job)
        try {
            sendCompleteJob(client, job, executeJobAction()).await()
        } catch (e: RetryableJobException) {
            logJobError(job, e)
            failWithDelay(e, client, job)
        } catch (e: Exception) {
            logJobError(job, e)
            sendJobError(e, client, job).await()
        } finally {
            logEndJob(job)
        }
    }

    fun logStartJob(job: ActivatedJob) {
        log.debug("$LOG_PREFIX Start ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}")
    }

    fun logEndJob(job: ActivatedJob) {
        log.debug("$LOG_PREFIX End ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}")
    }

    fun logJobError(job: ActivatedJob, e: Throwable) {
        log.error(
            "$LOG_PREFIX Error occurred in ${job.type}, ${JOB_INFO_MSG.format(job.key, job.processInstanceKey)}",
            e
        )
    }
}
