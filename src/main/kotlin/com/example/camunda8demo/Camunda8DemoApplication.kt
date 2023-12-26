package com.example.camunda8demo

import io.camunda.zeebe.spring.client.annotation.Deployment
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@Deployment(resources = ["classpath:/bpmn/**/*.bpmn"])
class Camunda8DemoApplication

fun main(args: Array<String>) {
    runApplication<Camunda8DemoApplication>(*args)
}
