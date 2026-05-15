package lt.satsyuk.distributed.audit.auditwriter.consumer;

import lt.satsyuk.distributed.audit.auditwriter.AuditWriterServiceApplication;
import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * Integration test: real Kafka broker via Testcontainers + real @KafkaListener wiring.
 *
 * <p>Goal: prove that a domain event published to Kafka reaches the audit-writer
 * consumer and is delegated to {@link BlockchainWriterService}.
 *
 * <p>Named {@code *IT} to run in the Maven Failsafe (integration-test) phase,
 * separate from Surefire unit tests.
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
                "web3j.private-key="
        }
)
class AuditEventConsumerKafkaTestcontainersIT {

    private static final String TOPIC = "user.login.events";

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

    @Test
    void shouldConsumeEventFromKafkaAndDelegateToBlockchainWriter() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("tc-user-1", "127.0.0.1", "tc-agent");

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.springframework.kafka.support.serializer.JsonSerializer");
        producerProps.put("spring.json.add.type.headers", false);

        KafkaTemplate<String, AuditEvent> kafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
        kafkaListenerEndpointRegistry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment((ConcurrentMessageListenerContainer<?, ?>) container, 1));

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
}

