package com.example.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.example.api.domain")
@EnableJpaRepositories(basePackages = "com.example.api.repository")
public class KafkaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KafkaConsumerApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}