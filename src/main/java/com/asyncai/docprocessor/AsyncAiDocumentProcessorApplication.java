package com.asyncai.docprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application entry point.
 *
 * WHY @EnableAsync?
 * While Kafka handles our primary async flow, we also want Spring's
 * @Async for fire-and-forget tasks like sending notifications.
 * Without this annotation, @Async methods execute synchronously.
 *
 * INTERVIEW NOTE: @SpringBootApplication is a composite of:
 *   @Configuration     — this class can define @Beans
 *   @EnableAutoConfiguration — Spring Boot scans classpath and auto-configures
 *   @ComponentScan     — scans this package and sub-packages for @Component etc.
 */
@SpringBootApplication
@EnableAsync
public class AsyncAiDocumentProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncAiDocumentProcessorApplication.class, args);
    }
}
