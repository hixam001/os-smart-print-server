package com.printscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Print Server Scheduler — Backend entry point.
 *
 * <p>Spring Boot 4.0.6 / Java 26
 */
@SpringBootApplication
@EnableScheduling
public class PrintSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrintSchedulerApplication.class, args);
    }
}
