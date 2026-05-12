package com.dreamhomes.haven.common.config;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class KafkaTopicConfig {

    @Value("${haven.kafka.topic-partitions:3}")
    private int partitions;

    @Value("${haven.kafka.replication-factor:1}")
    private short replicationFactor;

    @Bean
    NewTopic inspectionRequestedTopic() {
        return TopicBuilder.name(InspectionRequestedEvent.TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic inspectionRequestedDlt() {
        return TopicBuilder.name(InspectionRequestedEvent.TOPIC + ".DLT")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic offerSubmittedTopic() {
        return TopicBuilder.name(OfferSubmittedEvent.TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic offerSubmittedDlt() {
        return TopicBuilder.name(OfferSubmittedEvent.TOPIC + ".DLT")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
