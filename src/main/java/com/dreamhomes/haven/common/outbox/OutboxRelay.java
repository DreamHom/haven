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

    @Scheduled(fixedDelay = 1000L)
    @Transactional
    public void publishPending() {
        drainBatch();
    }

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
        
                log.error("Outbox row id={} references unknown event type '{}' — leaving for manual handling",
                        row.getId(), row.getEventType());
            } catch (Exception e) {
         
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
    
            log.warn("Outbox row id={} shipped to Kafka but markPublished failed; "
                    + "scheduled poll will retry: {}", outboxId, saveFailed.getMessage());
        }
    }
}
