package com.sarinah.product_bundling.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory(
            ConsumerFactory<String,String> consumerFactory,
            KafkaTemplate<String,String> kafkaTemplate
    ){
        var factory = new ConcurrentKafkaListenerContainerFactory<String,String>();
        factory.setConsumerFactory(consumerFactory);

        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 5));

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}

