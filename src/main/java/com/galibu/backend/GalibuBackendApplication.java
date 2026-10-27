package com.galibu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GalibuBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GalibuBackendApplication.class, args);
    }
}
