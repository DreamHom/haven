package com.dreamhomes.haven.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Logs a single actionable message when the configured broker is unreachable at
 * startup, instead of leaving {@code NetworkClient} to WARN-spam every reconnect.
 */
@Component
@Slf4j
public class KafkaBrokerStartupCheck implements ApplicationRunner {

    private final KafkaAdmin kafkaAdmin;
    private final String bootstrapServers;

    public KafkaBrokerStartupCheck(
            KafkaAdmin kafkaAdmin,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.kafkaAdmin = kafkaAdmin;
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public void run(ApplicationArguments args) {
        Properties props = new Properties();
        props.putAll(kafkaAdmin.getConfigurationProperties());
        props.putIfAbsent(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        props.putIfAbsent(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5_000);

        try (AdminClient client = AdminClient.create(props)) {
            var nodes = client.describeCluster().nodes().get(5, TimeUnit.SECONDS);
            log.info(
                    "Kafka broker reachable (bootstrap={}, brokers={})",
                    bootstrapServers,
                    nodes.size());
        } catch (Exception ex) {
            log.error(
                    """
                    Kafka is not reachable at bootstrap {}. \
                    Notification listeners will keep retrying in the background; \
                    HTTP API and outbox relay may still run but async events will not flow. \
                    Local fix: `docker compose up -d kafka` and set KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
                    with KAFKA_SECURITY_PROTOCOL=PLAINTEXT in `.env`. \
                    Cause: {}""",
                    bootstrapServers,
                    rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
