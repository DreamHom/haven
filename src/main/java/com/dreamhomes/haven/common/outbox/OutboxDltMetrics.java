package com.dreamhomes.haven.common.outbox;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exposes {@code haven.outbox.dlt} — one gauge per DLT topic — reading the total
 * number of records sitting on the dead-letter topic. Mirror of the existing
 * {@code haven.outbox.unpublished} gauge for the post-DLT-route side of the
 * pipeline: ops can alert when DLT depth grows because that means consumer-side
 * processing has been failing for long enough to exhaust retries.
 *
 * <p>Each DLT topic gets its own gauge tagged with {@code topic=<name>}, so the
 * Prometheus query {@code sum(haven_outbox_dlt)} returns total platform DLT depth
 * and {@code haven_outbox_dlt{topic="..."}} drills into per-topic.</p>
 *
 * <p>Depth = end-offset sum across partitions. We don't subtract committed offsets —
 * the DLT topic doesn't have an active consumer group in this app, so end-offset IS
 * the depth.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxDltMetrics {

    private static final String GAUGE_NAME = "haven.outbox.dlt";

    /** Every domain topic the platform publishes has a sibling {@code .DLT} created by KafkaTopicConfig. */
    private static final List<String> DLT_TOPICS = List.of(
            InspectionRequestedEvent.TOPIC + ".DLT",
            OfferSubmittedEvent.TOPIC + ".DLT");

    private final MeterRegistry meterRegistry;
    private final KafkaAdmin kafkaAdmin;

    @PostConstruct
    void registerGauges() {
        for (String topic : DLT_TOPICS) {
            Gauge.builder(GAUGE_NAME, () -> totalEndOffset(topic))
                    .tag("topic", topic)
                    .description("Records sitting on the dead-letter topic (sum of partition end offsets)")
                    .register(meterRegistry);
        }
    }

    private double totalEndOffset(String topic) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicDescription desc = client.describeTopics(List.of(topic))
                    .allTopicNames().get(5, TimeUnit.SECONDS).get(topic);
            Map<TopicPartition, OffsetSpec> request = new HashMap<>();
            desc.partitions().forEach(p ->
                    request.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));
            ListOffsetsResult result = client.listOffsets(request);
            long total = 0;
            for (TopicPartition tp : request.keySet()) {
                total += result.partitionResult(tp).get(5, TimeUnit.SECONDS).offset();
            }
            return total;
        } catch (Exception e) {
            // A missing topic, broker hiccup, or admin-client timeout shouldn't kill the
            // scrape — emit -1 so dashboards can tell "not measured" apart from "0 depth".
            log.debug("haven.outbox.dlt gauge could not read {}: {}", topic, e.getMessage());
            return -1.0;
        }
    }
}
