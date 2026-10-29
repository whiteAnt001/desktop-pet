package com.whiteant.pet.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PetServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetServerApplication.class, args);
    }
}