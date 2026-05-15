package com.dreamhomes.haven.common.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * Wires retry-then-DLT for every {@code @KafkaListener} on the platform.
 *
 * <p>What we own:
 * <ul>
 *   <li><b>Retry policy</b> — exponential backoff starting at 500 ms, capped at 5 s,
 *       multiplier 2.0, with a hard ceiling of {@value #MAX_RETRY_ELAPSED_MS} so we
 *       don't tie up a partition forever.</li>
 *   <li><b>DLT routing</b> — original topic + {@code .DLT} suffix. Reuses the same
 *       partition number so the dead-letter ordering follows the original.</li>
 *   <li><b>Non-retryable exceptions</b> — none for now; we trust transient failures
 *       to clear on retry, and stuck failures to land on DLT after the time cap.</li>
 * </ul>
 *
 * <p>The framework (Spring Kafka's {@code DefaultErrorHandler} + DLT publishing) does
 * the actual retry loop and message routing. Our config bean expresses the policy.
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /** Stop retrying any single message after 30 seconds — partitions can keep moving. */
    private static final long MAX_RETRY_ELAPSED_MS = 30_000L;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // DLT topic = "<original>.DLT" on the same partition, so per-key ordering is
        // preserved even in the failure tail.
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
