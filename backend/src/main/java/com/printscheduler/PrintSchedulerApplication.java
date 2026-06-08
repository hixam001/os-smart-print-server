package com.printscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrintSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrintSchedulerApplication.class, args);
    }
}
