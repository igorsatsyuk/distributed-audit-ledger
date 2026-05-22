package lt.satsyuk.distributed.audit.auditwriter.consumer;

import lt.satsyuk.distributed.audit.auditwriter.AuditWriterServiceApplication;
import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.apache.kafka.common.TopicPartition;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
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
@Testcontainers(disabledWithoutDocker = true)
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

    /**
     * Unique suffix per test-class instantiation so each run (and parallel CI job)
     * gets its own Kafka topic namespace, preventing cross-run interference.
     *
     * <p>Note: {@code T_SUFFIX}, {@code TOPIC}, and {@code DLT_TOPIC} are static
     * class-level constants, so <em>all test methods within this class share the same
     * topic and committed offsets</em>.  The unique suffix only isolates separate
     * test-class runs or parallel CI jobs from each other; it does not give each
     * {@code @Test} method its own clean topic.
     */
    private static final String T_SUFFIX = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String TOPIC     = "user.login.events-" + T_SUFFIX;
    private static final String DLT_TOPIC = TOPIC + ".dlt";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0")
    );

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Use test-run-unique topic names so every class instantiation (and parallel CI job)
        // starts with an empty topic and no committed offsets — no ordering dependency needed.
        registry.add("kafka.topics.user-login-events", () -> TOPIC);
        registry.add("kafka.topics.user-login-events-dlt", () -> DLT_TOPIC);
    }

    @MockitoBean
    private BlockchainWriterService blockchainWriterService;

    @BeforeEach
    void resetMock() {
        reset(blockchainWriterService);
    }

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

    private KafkaTemplate<String, byte[]> buildRawByteProducer() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    private void waitForListenerAssignment() {
        kafkaListenerEndpointRegistry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment((ConcurrentMessageListenerContainer<?, ?>) container, 1));
    }

    private Long committedSourceOffset() throws Exception {
        Map<String, Object> adminProps = new HashMap<>();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                    .listConsumerGroupOffsets("audit-writer-consumer")
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);

            OffsetAndMetadata metadata = committed.get(new TopicPartition(TOPIC, 0));
            return metadata == null ? null : metadata.offset();
        }
    }

    private static String keyAsUtf8(ConsumerRecord<byte[], byte[]> consumerRecord) {
        return consumerRecord.key() == null ? null : new String(consumerRecord.key(), StandardCharsets.UTF_8);
    }

    private Long awaitSourceOffsetSettled() throws Exception {
        Long baseline = committedSourceOffset();
        int unchangedReads = 0;
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(20).toNanos();

        while (System.nanoTime() < deadlineNanos) {
            pauseWithoutThreadSleep(200L);
            Long current = committedSourceOffset();
            if (Objects.equals(current, baseline)) {
                unchangedReads++;
                if (unchangedReads >= 5) {
                    return baseline;
                }
            } else {
                baseline = current;
                unchangedReads = 0;
            }
        }

        throw new IllegalStateException("Source committed offset did not settle within timeout");
    }

    private static void pauseWithoutThreadSleep(long millis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        long maxParkNanos = TimeUnit.MILLISECONDS.toNanos(1);
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test wait was interrupted");
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return;
            }

            LockSupport.parkNanos(Math.min(remainingNanos, maxParkNanos));
        }
    }

    private KafkaConsumer<byte[], byte[]> buildDltByteArrayConsumer(String groupId) {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<>(consumerProps);
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
     * (the test-run-unique DLT topic) rather than silently dropping it.
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
        try (KafkaConsumer<byte[], byte[]> dltConsumer = buildDltByteArrayConsumer(
                "dlt-verify-group-" + System.nanoTime())) {
            dltConsumer.subscribe(List.of(DLT_TOPIC));

            List<ConsumerRecord<byte[], byte[]>> received = new ArrayList<>();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        ConsumerRecords<byte[], byte[]> polled = dltConsumer.poll(Duration.ofMillis(300));
                        polled.forEach(received::add);
                        boolean hasExpectedEvent = received.stream()
                                .map(AuditEventConsumerKafkaTestcontainersTest::keyAsUtf8)
                                .anyMatch(event.getEventId()::equals);
                        assertThat(hasExpectedEvent)
                                .as("expected DLT record for eventId=" + event.getEventId())
                                .isTrue();
                    });

            ConsumerRecord<byte[], byte[]> dltRecord = received.stream()
                    .filter(rec -> event.getEventId().equals(keyAsUtf8(rec)))
                    .findFirst()
                    .orElseThrow();
            String dltKey = keyAsUtf8(dltRecord);
            assertThat(dltKey)
                    .as("DLT record key must be the original event's eventId")
                    .isEqualTo(event.getEventId());
        }

        kafkaTemplate.destroy();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void shouldNotPublishToDltWhenBlockchainNotConfigured() throws Exception {
        doThrow(new BlockchainWriterService.BlockchainNotConfiguredException("simulated not-configured"))
                .when(blockchainWriterService).anchorEvent(any());

        UserLoggedInEvent event = UserLoggedInEvent.of("not-configured-user", "10.0.0.2", null);
        // Drain async commits from earlier test methods before capturing this method's baseline.
        Long committedBefore = awaitSourceOffsetSettled();

        KafkaTemplate<String, AuditEvent> kafkaTemplate = buildProducer();
        waitForListenerAssignment();

        kafkaTemplate.send(TOPIC, event.getEventId(), event).get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(blockchainWriterService, atLeastOnce()).anchorEvent(argThat(received ->
                        received != null && event.getEventId().equals(received.getEventId()))));

        Awaitility.await()
                .during(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(8))
                .untilAsserted(() -> assertThat(committedSourceOffset()).isEqualTo(committedBefore));

        try (KafkaConsumer<byte[], byte[]> dltConsumer = buildDltByteArrayConsumer(
                "dlt-not-configured-" + System.nanoTime())) {
            dltConsumer.subscribe(List.of(DLT_TOPIC));

            List<ConsumerRecord<byte[], byte[]>> received = new ArrayList<>();
            long deadlineNanos = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (System.nanoTime() < deadlineNanos) {
                ConsumerRecords<byte[], byte[]> polled = dltConsumer.poll(Duration.ofMillis(300));
                polled.forEach(received::add);
            }

            boolean foundEventInDlt = received.stream()
                    .map(ConsumerRecord::key)
                    .filter(Objects::nonNull)
                    .map(key -> new String(key, StandardCharsets.UTF_8))
                    .anyMatch(event.getEventId()::equals);

            assertThat(foundEventInDlt)
                    .as("record for not-configured failure must not be published to DLT")
                    .isFalse();
        }

        // Important for test isolation: this scenario intentionally leaves offset uncommitted.
        // Switch the mock back to success and wait until the same record gets redelivered and
        // committed, so later tests are not blocked behind this offset.
        reset(blockchainWriterService);
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    Long committedAfterRecovery = committedSourceOffset();
                    if (committedBefore == null) {
                        assertThat(committedAfterRecovery).isNotNull();
                    } else {
                        assertThat(committedAfterRecovery).isGreaterThanOrEqualTo(committedBefore + 1L);
                    }
                });

        kafkaTemplate.destroy();
    }

    @Test
    void shouldPreserveRawPoisonRecordBytesOnDlt() throws Exception {
        String poisonKey = "poison-" + UUID.randomUUID();
        byte[] poisonPayload = "{broken-json".getBytes(StandardCharsets.UTF_8);

        KafkaTemplate<String, byte[]> rawProducer = buildRawByteProducer();
        waitForListenerAssignment();

        rawProducer.send(TOPIC, poisonKey, poisonPayload).get(10, TimeUnit.SECONDS);
        rawProducer.flush();

        try (KafkaConsumer<byte[], byte[]> dltConsumer = buildDltByteArrayConsumer(
                "dlt-poison-" + System.nanoTime())) {
            dltConsumer.subscribe(List.of(DLT_TOPIC));

            final ConsumerRecord<byte[], byte[]>[] matched = new ConsumerRecord[1];
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        ConsumerRecords<byte[], byte[]> polled = dltConsumer.poll(Duration.ofMillis(300));
                        for (ConsumerRecord<byte[], byte[]> consumerRecord : polled) {
                            String key = consumerRecord.key() == null ? null : new String(consumerRecord.key(), StandardCharsets.UTF_8);
                            if (poisonKey.equals(key)) {
                                matched[0] = consumerRecord;
                                break;
                            }
                        }
                        assertThat(matched[0]).as("expected poison record on DLT").isNotNull();
                    });

            assertThat(matched[0].value())
                    .as("DLT payload must preserve original malformed JSON bytes")
                    .isEqualTo(poisonPayload);
        }

        rawProducer.destroy();
    }

    @Test
    void shouldPublishTombstoneToDltAsNonRecoverable() throws Exception {
        String tombstoneKey = "tombstone-" + UUID.randomUUID();

        KafkaTemplate<String, byte[]> rawProducer = buildRawByteProducer();
        waitForListenerAssignment();

        // Null payload simulates a Kafka tombstone record.
        rawProducer.send(TOPIC, tombstoneKey, null).get(10, TimeUnit.SECONDS);
        rawProducer.flush();

        try (KafkaConsumer<byte[], byte[]> dltConsumer = buildDltByteArrayConsumer(
                "dlt-tombstone-" + System.nanoTime())) {
            dltConsumer.subscribe(List.of(DLT_TOPIC));

            final ConsumerRecord<byte[], byte[]>[] matched = new ConsumerRecord[1];
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        ConsumerRecords<byte[], byte[]> polled = dltConsumer.poll(Duration.ofMillis(300));
                        for (ConsumerRecord<byte[], byte[]> consumerRecord : polled) {
                            String key = consumerRecord.key() == null ? null : new String(consumerRecord.key(), StandardCharsets.UTF_8);
                            if (tombstoneKey.equals(key)) {
                                matched[0] = consumerRecord;
                                break;
                            }
                        }
                        assertThat(matched[0]).as("expected tombstone record on DLT").isNotNull();
                    });

            assertThat(matched[0].value())
                    .as("DLT value for tombstone should remain null")
                    .isNull();
        }

        rawProducer.destroy();
    }
}
