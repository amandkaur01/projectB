package com.example.iotlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // ← enables the daily overdue email scheduler
public class IotlabApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotlabApplication.class, args);
    }
}
