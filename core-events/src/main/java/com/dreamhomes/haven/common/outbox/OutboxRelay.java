package com.dreamhomes.haven.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Reads unpublished outbox rows, publishes each to its declared topic, and stamps
 * {@code publishedAt} only after Kafka acks the send. Rows whose publish fails are
 * left untouched — the next tick re-claims and retries (at-least-once delivery;
 * consumer-side dedup is owned by {@link com.dreamhomes.haven.notification.NotificationService}).
 *
 * <h2>Two trigger paths</h2>
 * <ol>
 *   <li><b>After-commit hook</b> — services fire an {@link OutboxRowReadyEvent} after
 *       writing an outbox row; once the originating transaction commits, this bean
 *       drains immediately. Happy-path Kafka latency is tens of milliseconds, not up
 *       to a second.</li>
 *   <li><b>Scheduled poll</b> — runs every second as the safety net. If the JVM
 *       crashes between commit and listener invocation, the row sits in the outbox
 *       until the next poll.</li>
 * </ol>
 *
 * <p>Crash anywhere between Kafka ack and {@code save()} → row stays unpublished →
 * gets republished. The downstream consumer's {@code event_id} dedup makes this safe.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Periodic safety net. Catches rows that the after-commit hook missed (e.g. JVM crash mid-flight). */
    @Scheduled(fixedDelay = 1000L)
    @Transactional
    public void publishPending() {
        drainBatch();
    }

    /**
     * Fires synchronously after the originating service's transaction commits — gives
     * us "ship to Kafka right now" latency without coupling the service to KafkaTemplate.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOutboxRowReady(OutboxRowReadyEvent event) {
        drainBatch();
    }

    private void drainBatch() {
        List<OutboxEvent> batch = outboxRepository.claimBatchForPublishing(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent row : batch) {
            try {
                Class<?> payloadClass = Class.forName(row.getEventType());
                Object payload = objectMapper.readValue(row.getPayload(), payloadClass);
                kafkaTemplate.send(row.getTopic(), row.getPartitionKey(), payload).get();
                row.setPublishedAt(Instant.now());
                outboxRepository.save(row);
                log.info("Outbox shipped id={} eventId={} topic={}",
                        row.getId(), row.getEventId(), row.getTopic());
            } catch (ClassNotFoundException unknownType) {
                // Refusing-to-load-unknown-type is the right behaviour even at the cost of
                // a stuck row. Operator intervention should clear or fix it.
                log.error("Outbox row id={} references unknown event type '{}' — leaving for manual handling",
                        row.getId(), row.getEventType());
            } catch (Exception e) {
                // Network blips, broker down, anything else — leave row unpublished, retry next tick.
                log.warn("Outbox publish failed for id={}, will retry: {}", row.getId(), e.getMessage());
            }
        }
    }
}
