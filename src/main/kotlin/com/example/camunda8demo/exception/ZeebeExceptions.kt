package com.example.camunda8demo.exception

abstract class ZeebeIncident(override val message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    val code: String = javaClass.simpleName
}
