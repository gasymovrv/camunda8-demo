package com.example.camunda8demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication
//@Deployment(resources = ["classpath:/bpmn/**/*.bpmn"])
//@Deployment(resources = ["classpath:/bpmn/routing/*.bpmn"])
class Camunda8DemoApplication

fun main(args: Array<String>) {
    runApplication<Camunda8DemoApplication>(*args)
}
