package lt.satsyuk.distributed.audit.auditwriter.consumer;

import lt.satsyuk.distributed.audit.auditwriter.AuditWriterServiceApplication;
import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Integration test: real Kafka broker via Testcontainers + real @KafkaListener wiring.
 *
 * <p>Goal: prove that a domain event published to Kafka reaches the audit-writer
 * consumer and is delegated to {@link BlockchainWriterService}.
 *
 * <p>Also verifies that records for which {@link BlockchainWriterService#anchorEvent}
 * throws a transient {@link BlockchainWriterService.BlockchainWriteException} are
 * eventually published to the DLT topic after the configured retry attempts are
 * exhausted, so no event is silently dropped.
 *
 * <p>Named {@code *Test} so it runs in the Maven Surefire phase with the rest
 * of the module test suite.
 */
@Testcontainers
@SpringBootTest(
        classes = AuditWriterServiceApplication.class,
        properties = {
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "spring.kafka.producer.properties.spring.json.add.type.headers=false",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=lt.satsyuk.distributed.audit.event",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "web3j.contract-address=",
                "web3j.private-key=",
                // Speed up retries so the DLT test completes in a few seconds
                "kafka.listener.retry-interval-ms=100"
        }
)
class AuditEventConsumerKafkaTestcontainersTest {

    private static final String TOPIC = "user.login.events";
    private static final String DLT_TOPIC = "user.login.events.dlt";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    private BlockchainWriterService blockchainWriterService;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private KafkaTemplate<String, AuditEvent> buildProducer() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.springframework.kafka.support.serializer.JsonSerializer");
        producerProps.put("spring.json.add.type.headers", false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    private void waitForListenerAssignment() {
        kafkaListenerEndpointRegistry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment((ConcurrentMessageListenerContainer<?, ?>) container, 1));
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldConsumeEventFromKafkaAndDelegateToBlockchainWriter() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("tc-user-1", "127.0.0.1", "tc-agent");

        KafkaTemplate<String, AuditEvent> kafkaTemplate = buildProducer();
        waitForListenerAssignment();

        kafkaTemplate.send(TOPIC, event.getEventId(), event).get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> verify(blockchainWriterService).anchorEvent(argThat(received ->
                        received != null
                                && event.getEventId().equals(received.getEventId())
                                && event.getEventType() == received.getEventType()
                )));

        kafkaTemplate.destroy();
    }

    /**
     * Verifies the DLT routing: when {@link BlockchainWriterService#anchorEvent} throws a
     * transient {@link BlockchainWriterService.BlockchainWriteException} on every call, the
     * Kafka error handler exhausts retries and publishes the original record to the DLT topic
     * ({@value #DLT_TOPIC}) rather than silently dropping it.
     */
    @Test
    void shouldPublishToDltWhenBlockchainWriterThrowsAfterRetries() throws Exception {
        doThrow(new BlockchainWriterService.BlockchainWriteException("simulated transient failure", null))
                .when(blockchainWriterService).anchorEvent(any());

        UserLoggedInEvent event = UserLoggedInEvent.of("dlt-user", "10.0.0.1", null);

        KafkaTemplate<String, AuditEvent> kafkaTemplate = buildProducer();
        waitForListenerAssignment();

        kafkaTemplate.send(TOPIC, event.getEventId(), event).get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();

        // Build a raw byte-array consumer so we don't need to deserialise DLT payload
        Map<String, Object> consumerProps = new java.util.HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verify-group-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        try (KafkaConsumer<byte[], byte[]> dltConsumer = new KafkaConsumer<>(consumerProps)) {
            dltConsumer.subscribe(List.of(DLT_TOPIC));

            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        ConsumerRecords<byte[], byte[]> records = dltConsumer.poll(Duration.ofMillis(300));
                        assertThat(records.isEmpty())
                                .as("expected at least one record on DLT topic " + DLT_TOPIC)
                                .isFalse();
                    });
        }

        kafkaTemplate.destroy();
    }
}
