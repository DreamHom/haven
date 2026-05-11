package com.dreamhomes.haven.common.config;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link KafkaTopicConfig} actually publishes {@link NewTopic} beans
 * for the two domain topics + their DLT siblings, with the partition + replication
 * counts the property file declares. The bean's mere existence is what tells
 * spring-kafka's admin to create the topic with that shape on broker connect; if
 * we ever drop a bean by accident, this test catches it before we discover the
 * topic auto-created with broker defaults.
 */
class KafkaTopicConfigIT extends AbstractPostgresIT {

    @Autowired
    ApplicationContext context;

    @Test
    void inspectionRequestedTopicHasExpectedShape() {
        NewTopic topic = context.getBean("inspectionRequestedTopic", NewTopic.class);

        assertThat(topic.name()).isEqualTo(InspectionRequestedEvent.TOPIC);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void offerSubmittedTopicHasExpectedShape() {
        NewTopic topic = context.getBean("offerSubmittedTopic", NewTopic.class);

        assertThat(topic.name()).isEqualTo(OfferSubmittedEvent.TOPIC);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void deadLetterTopicsExistWithMatchingPartitionCount() {
        NewTopic inspectionDlt = context.getBean("inspectionRequestedDlt", NewTopic.class);
        NewTopic offerDlt = context.getBean("offerSubmittedDlt", NewTopic.class);

        // DLT partition count must match the source so the DeadLetterPublishingRecoverer
        // can route a failure onto the same partition number — preserving per-key order
        // even in the failure tail.
        assertThat(inspectionDlt.name()).isEqualTo(InspectionRequestedEvent.TOPIC + ".DLT");
        assertThat(inspectionDlt.numPartitions()).isEqualTo(3);
        assertThat(offerDlt.name()).isEqualTo(OfferSubmittedEvent.TOPIC + ".DLT");
        assertThat(offerDlt.numPartitions()).isEqualTo(3);
    }
}
