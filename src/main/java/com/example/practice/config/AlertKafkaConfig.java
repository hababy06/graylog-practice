package com.example.practice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@Profile("kafka")
public class AlertKafkaConfig {
    // 現在使用原生 KafkaConsumer，不需要 @EnableKafka
}


