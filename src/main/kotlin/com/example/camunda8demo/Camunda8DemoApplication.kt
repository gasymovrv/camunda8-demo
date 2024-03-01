package com.example.camunda8demo

import io.camunda.zeebe.spring.client.annotation.Deployment
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication
//@Deployment(resources = ["classpath:/bpmn/**/*.bpmn"])
@Deployment(
    resources = [
        "classpath:/bpmn/router-process.bpmn",
        "classpath:/bpmn/sla-process.bpmn",
        "classpath:/bpmn/simple-process.bpmn",
        "classpath:/bpmn/client-response-process.bpmn"
    ]
)
class Camunda8DemoApplication

fun main(args: Array<String>) {
    runApplication<Camunda8DemoApplication>(*args)
}
