package com.sarinah.product_bundling.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublishEventService {
    private  KafkaTemplate<String,Object> kafkaTemplate;

    public PublishEventService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishToKafka() {
        kafkaTemplate.send("omo-notification","sucessfully subtract balance with amount");
    }
}
