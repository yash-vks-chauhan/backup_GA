package com.parking.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootApplication
public class AppApplication {

    @Bean
    DefaultMessageListenerContainer messageListenerContainer(MongoTemplate template) {
        return new DefaultMessageListenerContainer(template);
    }

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}
