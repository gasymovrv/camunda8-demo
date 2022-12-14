package com.example.camunda8demo

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.client.api.response.PublishMessageResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletionStage

@RestController
class Controller(@Qualifier("zeebeClientLifecycle") private val zeebe: ZeebeClient) {

    @PostMapping("/processes")
    fun createSimpleTestProcess(@RequestBody createInstanceRequest: CreateInstanceRequest): CompletionStage<ProcessInstanceEvent> {
        val (bpmnProcessId, vars) = createInstanceRequest

        return zeebe.newCreateInstanceCommand()
            .bpmnProcessId(bpmnProcessId)
            .latestVersion()
            .variables(vars ?: mapOf())
            .send()
            .thenApply {
                println("Instance created. Key: ${it.processInstanceKey}")
                it
            }
    }

    @PostMapping("/messages")
    fun sendMessage(@RequestBody msgRequest: SendMessageRequest): CompletionStage<PublishMessageResponse> {
        val (msgName, correlationKey, messageId, vars) = msgRequest

        val command = zeebe.newPublishMessageCommand()
            .messageName(msgName) // to link with message catch event by its name
            .correlationKey(correlationKey) // to link with message catch event by correlationKey
            .variables(vars ?: mapOf())
        if (messageId != null) {
            command.messageId(messageId) // a unique id to ensure the message is published and processed only once (among the same messageName)
        }

        return command.send()
            .thenApply {
                println("Message '$msgName' sent. CorrelationKey: $correlationKey, messageKey: ${it.messageKey}")
                it
            }
    }
}

data class CreateInstanceRequest(
    val bpmnProcessId: String,
    val vars: Map<String, Any?>? = null
)

data class SendMessageRequest(
    val msgName: String,
    val correlationKey: String,
    val messageId: String? = null,
    val vars: Map<String, Any?>? = null
)
