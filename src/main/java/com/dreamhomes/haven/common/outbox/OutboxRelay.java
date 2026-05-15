package com.dreamhomes.haven.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import com.dreamhomes.haven.notification.NotificationService;

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
 * <h2>Async publish</h2>
 * <p>The Kafka {@code send(...)} call is fire-and-callback — we do <b>not</b> block on
 * {@code .get()}. The originating service's API thread is therefore decoupled from
 * broker latency: a slow ISR sync no longer holds open the request. The
 * {@code whenComplete} callback runs on the producer I/O thread; because the original tx
 * is already closed by then, marking {@code publishedAt} happens in a fresh transaction
 * via {@link TransactionTemplate}.</p>
 *
 * <p>Crash anywhere between Kafka ack and {@code save()} → row stays unpublished →
 * gets republished. The downstream consumer's {@code event_id} dedup makes this safe.</p>
 *
 * <h2>Timer</h2>
 * <p>Every publish is wrapped in a {@code haven.kafka.publish.duration} Micrometer
 * Timer, tagged by {@code topic} and {@code outcome} (success / failure). Surfaces in
 * Prometheus alongside the existing {@code haven.outbox.unpublished} gauge for
 * dashboarding broker latency over time.</p>
 */
@Component
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final String PUBLISH_TIMER = "haven.kafka.publish.duration";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(OutboxEventRepository outboxRepository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper,
                       TransactionTemplate transactionTemplate,
                       MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
    }

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
                Timer.Sample sample = Timer.start(meterRegistry);
                kafkaTemplate.send(row.getTopic(), row.getPartitionKey(), payload)
                        .whenComplete((sendResult, ex) -> recordPublishOutcome(row, sample, ex));
            } catch (ClassNotFoundException unknownType) {
                // Refusing-to-load-unknown-type is the right behaviour even at the cost of
                // a stuck row. Operator intervention should clear or fix it.
                log.error("Outbox row id={} references unknown event type '{}' — leaving for manual handling",
                        row.getId(), row.getEventType());
            } catch (Exception e) {
                // Serialisation failures or anything else thrown synchronously — leave row
                // unpublished, the next tick retries.
                log.warn("Outbox publish failed pre-send for id={}, will retry: {}", row.getId(), e.getMessage());
            }
        }
    }

    private void recordPublishOutcome(OutboxEvent row, Timer.Sample sample, Throwable ex) {
        String outcome = (ex == null) ? "success" : "failure";
        sample.stop(Timer.builder(PUBLISH_TIMER)
                .tag("topic", row.getTopic())
                .tag("outcome", outcome)
                .register(meterRegistry));
        if (ex != null) {
            log.warn("Outbox publish failed for id={}, will retry: {}", row.getId(), ex.getMessage());
            return;
        }
        markPublished(row.getId());
    }

    /**
     * Stamp publishedAt in a fresh transaction. The whenComplete callback runs on the
     * producer I/O thread, after the originating @Transactional method has already
     * returned and committed — so we cannot reuse the outer tx.
     */
    private void markPublished(Long outboxId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    outboxRepository.findById(outboxId).ifPresent(row -> {
                        row.setPublishedAt(Instant.now());
                        outboxRepository.save(row);
                        log.info("Outbox shipped id={} eventId={} topic={}",
                                row.getId(), row.getEventId(), row.getTopic());
                    }));
        } catch (Exception saveFailed) {
            // The next claimBatchForPublishing tick will see this row as still unpublished
            // and retry. Consumer-side event_id dedup makes the redelivery a no-op.
            log.warn("Outbox row id={} shipped to Kafka but markPublished failed; "
                    + "scheduled poll will retry: {}", outboxId, saveFailed.getMessage());
        }
    }
}
