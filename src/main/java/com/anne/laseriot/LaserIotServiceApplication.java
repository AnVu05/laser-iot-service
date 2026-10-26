package com.anne.laseriot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LaserIotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaserIotServiceApplication.class, args);
    }
}
