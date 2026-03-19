package dev.gimi.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gimi-server — Optional Spring Boot server for webhook receiving.
 */
@SpringBootApplication
public class GimiServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GimiServerApplication.class, args);
    }
}
