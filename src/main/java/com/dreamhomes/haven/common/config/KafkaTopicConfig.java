package com.dreamhomes.haven.common.config;

import com.dreamhomes.haven.inspection.events.InspectionCancelledEvent;
import com.dreamhomes.haven.inspection.events.InspectionDecidedEvent;
import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics this app produces to as {@link NewTopic} beans so the
 * partition count and replication factor are pinned at deploy time, not whatever the
 * broker defaults are when the topic auto-creates on first publish.
 *
 * <p>Why this matters: partition count is the throughput knob you can't change later
 * without operational pain (re-partitioning preserves neither order nor downstream
 * consumer offsets). Pinning it explicitly at the source — even with a default of 3 —
 * lets us scale up by changing one number rather than discovering the topic was
 * created with 1 partition months later.</p>
 *
 * <p>Replication factor of 1 is dev/single-broker safe; production overrides via
 * {@code haven.kafka.replication-factor} (typical: 3 against a 3-broker cluster).</p>
 *
 * <h2>Companion DLT topics</h2>
 *
 * <p>Each main topic gets a sibling {@code <topic>.DLT} with matching partition count
 * so {@link com.dreamhomes.haven.common.config.KafkaErrorHandlerConfig}'s
 * {@code DeadLetterPublishingRecoverer} can route failed messages onto the same
 * partition number as the original — preserving per-key ordering even in the failure
 * tail.</p>
 */
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
    NewTopic inspectionDecidedTopic() {
        return TopicBuilder.name(InspectionDecidedEvent.TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic inspectionDecidedDlt() {
        return TopicBuilder.name(InspectionDecidedEvent.TOPIC + ".DLT")
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic inspectionCancelledTopic() {
        return TopicBuilder.name(InspectionCancelledEvent.TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic inspectionCancelledDlt() {
        return TopicBuilder.name(InspectionCancelledEvent.TOPIC + ".DLT")
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
