package com.sarinah.product_bundling.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

//@Component
//public class PublishEventService {
//    private  KafkaTemplate<String,Object> kafkaTemplate;
//
//    private ObjectMapper objectMapper;
//
//
//    public PublishEventService(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
//        this.kafkaTemplate = kafkaTemplate;
//        this.objectMapper = objectMapper;
//    }
//
//    @SneakyThrows
//    public void publishToKafka() {
//        Map<String, Object> body = new HashMap<>();
//        body.put("type", "NOTIF_OMO");
//        body.put("title", "Stock Alert");
//        body.put("message", "Stock tinggal 5");
//        body.put("sku", "ABC123");
//        body.put("qty", 5); // bisa Number / Boolean / nested Map, dll
//
//        String json = objectMapper.writeValueAsString(body);
//        Message<String> message = MessageBuilder
//                .withPayload(json) // di sini jadi String lagi
//                .setHeader(KafkaHeaders.TOPIC, "omo-notification")
//                .setHeader("eventCode", "inventory-inj")
//                .setHeader("x-channel", "web")
//                .build();
//
//
//        kafkaTemplate.send(message);
//    }
//}
