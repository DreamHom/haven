package com.dreamhomes.haven;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DreamhomesHavenApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamhomesHavenApplication.class, args);
    }
}
