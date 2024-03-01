package com.example.camunda8demo.util

/**
 * Use it when you need to throw something from a worker with future retries of the job
 */
open class RetryableJobException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
