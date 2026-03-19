package dev.gimi.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the gimi-worker distributed worker node.
 *
 * <p>This Spring Boot application pulls jobs from a Redis queue
 * and executes pipeline stages on behalf of the CI/CD server.
 */
@SpringBootApplication
@EnableScheduling
public class GimiWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GimiWorkerApplication.class, args);
    }
}
