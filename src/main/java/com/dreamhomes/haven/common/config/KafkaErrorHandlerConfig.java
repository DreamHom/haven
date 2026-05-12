package com.dreamhomes.haven.common.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    private static final long MAX_RETRY_ELAPSED_MS = 30_000L;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
   
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new org.apache.kafka.common.TopicPartition(
                                record.topic() + ".DLT",
                                record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);
        backOff.setMaxElapsedTime(MAX_RETRY_ELAPSED_MS);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
