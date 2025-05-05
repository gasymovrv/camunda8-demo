package com.example.camunda8demo.controller

import com.example.camunda8demo.worker.SystemWorkers
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.client.api.response.PublishMessageResponse
import io.camunda.zeebe.client.api.response.Topology
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletionStage

@RestController
class Controller(
    private val zeebe: ZeebeClient,
    private val systemWorkers: SystemWorkers
) {

    @PostMapping("/processes")
    fun createInstance(@RequestBody createInstanceRequest: CreateInstanceRequest): CompletionStage<CreateInstanceResponse> {
        val (bpmnProcessId, vars) = createInstanceRequest

        return zeebe.newCreateInstanceCommand()
            .bpmnProcessId(bpmnProcessId)
            .latestVersion()
            .variables(vars ?: mapOf())
            .send()
            .thenApply {
                println("Instance created. Key: ${it.processInstanceKey}, vars: $vars")
                CreateInstanceResponse(systemInfo = it, inputVars = vars)
            }
    }

    @PostMapping("/processes/{key}/cancel")
    fun cancelInstance(@PathVariable key: Long) {
        zeebe.newCancelInstanceCommand(key)
            .send()
            .thenApply {
                println("Instance $key cancelled ")
            }.exceptionally {
                error(it)
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
                println("Message '$msgName' sent. CorrelationKey: $correlationKey, messageKey: ${it.messageKey}, vars: $vars")
                it
            }
    }

    @PostMapping("/incidents/{key}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resolveIncident(@PathVariable key: Long): CompletionStage<Void> {
        return zeebe.newResolveIncidentCommand(key).send()
            .thenAccept {
                println("Incident '$key' resolved")
            }.exceptionally {
                error(it)
            }
    }

    @PostMapping("/jobs/{key}/retries")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateJobRetries(@PathVariable key: Long, @RequestBody request: UpdateRetriesRequest): CompletionStage<Void> {
        return zeebe.newUpdateRetriesCommand(key)
            .retries(request.retries)
            .send()
            .thenAccept {
                println("Retries for job '$key' updated")
            }.exceptionally {
                error(it)
            }
    }

    @DeleteMapping("/process-definitions/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteResource(@PathVariable key: Long): CompletionStage<Void> {
        return zeebe.newDeleteResourceCommand(key).send()
            .thenAccept {
                println("Process definition '$key' deleted")
            }.exceptionally {
                error(it)
            }
    }

    @PostMapping("/process-definitions")
    fun deployResource(@RequestBody request: DeployResourceRequest): CompletionStage<DeploymentEvent> {
        return zeebe.newDeployResourceCommand()
            .addResourceFile(request.fileName)
            .send()
            .thenApply {
                println("Process definition '${request.fileName}' deployed, key: ${it.key}")
                it
            }.exceptionally {
                error(it)
            }
    }

    @GetMapping("/topology")
    fun topology(): CompletionStage<Topology> {
        return zeebe.newTopologyRequest().send()
            .thenApply {
                println("Topology: $it")
                it
            }.exceptionally {
                error(it)
            }
    }

    @GetMapping("/execution-time/average")
    fun executionTimeAverage() =
        """
        Average execution time: ${systemWorkers.instancesDurationsMs.values.average() / 1000} seconds.
        Processes count: ${systemWorkers.instancesDurationsMs.size}
        """.trimIndent()

    @GetMapping("/execution-time/max")
    fun executionTimeMax() =
        """
        Max execution time: ${systemWorkers.instancesDurationsMs.values.max() / 1000} seconds.
        Processes count: ${systemWorkers.instancesDurationsMs.size}
        """.trimIndent()

    @GetMapping("/execution-time/min")
    fun executionTimeMin() =
        """
        Min execution time: ${systemWorkers.instancesDurationsMs.values.min() / 1000} seconds.
        Processes count: ${systemWorkers.instancesDurationsMs.size}
        """.trimIndent()

    @DeleteMapping("/execution-time")
    fun executionTimeClear() {
        systemWorkers.instancesDurationsMs.clear()
        println("Execution time measures cleared")
    }
}

data class CreateInstanceRequest(
    val bpmnProcessId: String,
    val vars: Map<String, Any?>? = null
)

data class CreateInstanceResponse(
    val systemInfo: ProcessInstanceEvent,
    val link: String = "http://localhost:8082/views/instances/${systemInfo.processInstanceKey}",
    val inputVars: Map<String, Any?>? = null
)

data class SendMessageRequest(
    val msgName: String,
    val correlationKey: String,
    val messageId: String? = null,
    val vars: Map<String, Any?>? = null
)

data class DeployResourceRequest(
    val fileName: String
)

data class UpdateRetriesRequest(
    val retries: Int
)
