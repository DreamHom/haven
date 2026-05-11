package com.dreamhomes.haven.common.outbox;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.inspection.model.InspectionRequest;

/**
 * Covers the relay's contract:
 * <ul>
 *   <li>Each claimed row is deserialised back to its typed event and shipped to Kafka
 *       with the topic + key recorded on the outbox row.</li>
 *   <li>Successfully-shipped rows have {@code publishedAt} stamped (in a fresh tx).</li>
 *   <li>If Kafka fails the future, that row is left unpublished — the next tick retries.</li>
 *   <li>The {@code haven.kafka.publish.duration} timer records each attempt with the
 *       right {@code outcome} tag.</li>
 * </ul>
 *
 * <p>SKIP LOCKED, polling cadence, and the {@code @Scheduled} fire schedule are all
 * Spring/Postgres concerns — covered in their own tests, not here.</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    SimpleMeterRegistry meterRegistry;
    TransactionTemplate transactionTemplate;
    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Lightweight tx manager that just runs the callback — adequate for unit tests.
        transactionTemplate = new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition def) {
                return new SimpleTransactionStatus();
            }
            @Override public void commit(TransactionStatus status) {}
            @Override public void rollback(TransactionStatus status) {}
        });
        relay = new OutboxRelay(outboxRepository, kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(),
                transactionTemplate, meterRegistry);
    }

    @Test
    void publishesEventToTopicKeyedByPartitionKeyAndStampsPublishedAt() throws Exception {
        UUID eventId = UUID.randomUUID();
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                eventId, 1L, 50L, 7L, 99L, 100L,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.parse("2026-05-15T08:30:00Z"));
        OutboxEvent row = OutboxEvent.builder()
                .id(123L).eventId(eventId)
                .aggregateType("InspectionRequest").aggregateId(1L)
                .eventType(InspectionRequestedEvent.class.getName())
                .topic(InspectionRequestedEvent.TOPIC)
                .partitionKey("7")
                .payload(new ObjectMapper().findAndRegisterModules().writeValueAsString(event))
                .createdAt(Instant.now())
                .build();
        when(outboxRepository.claimBatchForPublishing(50)).thenReturn(List.of(row));
        when(outboxRepository.findById(123L)).thenReturn(Optional.of(row));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(stubSendResult()));

        relay.publishPending();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(InspectionRequestedEvent.TOPIC), eq("7"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(InspectionRequestedEvent.class);
        assertThat(((InspectionRequestedEvent) payload.getValue()).eventId()).isEqualTo(eventId);

        ArgumentCaptor<OutboxEvent> savedRow = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(savedRow.capture());
        assertThat(savedRow.getValue().getPublishedAt()).isNotNull();

        // Timer fires once per publish, tagged with topic + outcome=success.
        long successCount = meterRegistry.find("haven.kafka.publish.duration")
                .tag("topic", InspectionRequestedEvent.TOPIC)
                .tag("outcome", "success")
                .timer().count();
        assertThat(successCount).isEqualTo(1L);
    }

    @Test
    void leavesRowUnpublishedWhenKafkaSendFails() throws Exception {
        OutboxEvent row = stubRow();
        when(outboxRepository.claimBatchForPublishing(50)).thenReturn(List.of(row));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        verify(outboxRepository, never()).save(any());

        // Timer still records the attempt — just with outcome=failure.
        long failureCount = meterRegistry.find("haven.kafka.publish.duration")
                .tag("topic", InspectionRequestedEvent.TOPIC)
                .tag("outcome", "failure")
                .timer().count();
        assertThat(failureCount).isEqualTo(1L);
    }

    @Test
    void emptyBatchDoesNotInvokeKafka() {
        when(outboxRepository.claimBatchForPublishing(50)).thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void rowWithUnknownEventTypeIsSkippedNotCrashed() {
        OutboxEvent row = OutboxEvent.builder()
                .id(1L).eventId(UUID.randomUUID())
                .aggregateType("Unknown").aggregateId(1L)
                .eventType("com.does.not.Exist")
                .topic("anywhere").partitionKey("1")
                .payload("{}").createdAt(Instant.now()).build();
        when(outboxRepository.claimBatchForPublishing(50)).thenReturn(List.of(row));

        relay.publishPending();  // must not throw

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(Object.class));
        verify(outboxRepository, never()).save(any());
    }

    private static OutboxEvent stubRow() throws Exception {
        UUID eventId = UUID.randomUUID();
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                eventId, 1L, 50L, 7L, 99L, 100L,
                Instant.now(), Instant.now().plusSeconds(3600), Instant.now());
        return OutboxEvent.builder()
                .id(123L).eventId(eventId)
                .aggregateType("InspectionRequest").aggregateId(1L)
                .eventType(InspectionRequestedEvent.class.getName())
                .topic(InspectionRequestedEvent.TOPIC).partitionKey("7")
                .payload(new ObjectMapper().findAndRegisterModules().writeValueAsString(event))
                .createdAt(Instant.now()).build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SendResult stubSendResult() {
        return new SendResult(null, null);
    }
}
