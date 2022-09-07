package com.example.camunda8demo

import io.camunda.zeebe.spring.client.EnableZeebeClient
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableZeebeClient
@ZeebeDeployment(resources = ["classpath:/bpmn/**/*.bpmn"])
class Camunda8DemoApplication

fun main(args: Array<String>) {
    runApplication<Camunda8DemoApplication>(*args)
}
