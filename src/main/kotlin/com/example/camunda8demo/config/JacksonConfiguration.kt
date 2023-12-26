package com.example.camunda8demo.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.camunda.zeebe.client.impl.ZeebeObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class JacksonConfiguration {

    @Bean
    @Primary
    fun zeebeObjectMapper(): ZeebeObjectMapper {
        return ZeebeObjectMapper(
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerKotlinModule()
        )
    }
}
