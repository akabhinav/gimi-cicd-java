package dev.gimi.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * gimi-server — Spring Boot server for CI/CD pipeline management,
 * webhook receiving, and distributed execution coordination.
 */
@SpringBootApplication
@EnableScheduling
public class GimiServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GimiServerApplication.class, args);
    }
}
