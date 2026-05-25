package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.command.CommandServiceIntegrationTest;
import lt.satsyuk.distributed.audit.command.service.AuditCommandPublisher;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link KafkaProducerConfig} correctly registers a strongly-typed
 * {@code KafkaTemplate<String, AuditEvent>} bean and that {@link AuditCommandPublisher}
 * is wired successfully from the real application context.
 */
@CommandServiceIntegrationTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.properties.client.id=command-service-it",
        "spring.kafka.producer.properties.acks=all"
})
class KafkaProducerConfigIntegrationTest {

    private static final String JSON_SERIALIZER_FQCN =
            "org.springframework.kafka.support.serializer.JsonSerializer";

    private final KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate;
    private final ProducerFactory<String, AuditEvent> auditEventProducerFactory;
    private final AuditCommandPublisher auditCommandPublisher;
    private final String embeddedKafkaBrokers;

    KafkaProducerConfigIntegrationTest(KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate,
                                       ProducerFactory<String, AuditEvent> auditEventProducerFactory,
                                       AuditCommandPublisher auditCommandPublisher,
                                       @Value("${spring.embedded.kafka.brokers}") String embeddedKafkaBrokers) {
        this.auditEventKafkaTemplate = auditEventKafkaTemplate;
        this.auditEventProducerFactory = auditEventProducerFactory;
        this.auditCommandPublisher = auditCommandPublisher;
        this.embeddedKafkaBrokers = embeddedKafkaBrokers;
    }

    @Test
    void auditEventKafkaTemplateBeanIsCreated() {
        assertThat(auditEventKafkaTemplate).isNotNull();
    }

    @Test
    void auditCommandPublisherBeanIsCreated() {
        assertThat(auditCommandPublisher).isNotNull();
    }

    @Test
    void producerFactoryPicksUpSpringKafkaOverrides() {
        assertThat(auditEventProducerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);

        @SuppressWarnings("unchecked")
        DefaultKafkaProducerFactory<String, AuditEvent> defaultFactory =
                (DefaultKafkaProducerFactory<String, AuditEvent>) auditEventProducerFactory;

        Map<String, Object> configurationProperties =
                defaultFactory.getConfigurationProperties();

        assertThat(configurationProperties)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBrokers)
                .containsEntry("client.id", "command-service-it")
                .containsEntry("acks", "all");

        assertThat(configurationProperties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                .isIn(StringSerializer.class, StringSerializer.class.getName());
        Object valueSerializer = configurationProperties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
        assertThat(valueSerializer).satisfiesAnyOf(
                serializer -> assertThat(serializer).isEqualTo(JSON_SERIALIZER_FQCN),
                serializer -> assertThat(serializer)
                        .isInstanceOf(Class.class)
                        .extracting(candidate -> ((Class<?>) candidate).getName())
                        .isEqualTo(JSON_SERIALIZER_FQCN)
        );
        assertThat(configurationProperties.get("spring.json.add.type.headers"))
                .isIn(false, "false");
    }
}

