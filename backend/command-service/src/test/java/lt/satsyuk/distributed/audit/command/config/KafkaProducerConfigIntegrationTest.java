package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.command.service.AuditCommandPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link KafkaProducerConfig} correctly registers a strongly-typed
 * {@code KafkaTemplate<String, AuditEvent>} bean and that {@link AuditCommandPublisher}
 * is wired successfully from the real application context.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"user.login.events"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class KafkaProducerConfigIntegrationTest {

    @Autowired
    private KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate;

    @Autowired
    private AuditCommandPublisher auditCommandPublisher;

    @Test
    void auditEventKafkaTemplateBeanIsCreated() {
        assertThat(auditEventKafkaTemplate).isNotNull();
    }

    @Test
    void auditCommandPublisherBeanIsCreated() {
        assertThat(auditCommandPublisher).isNotNull();
    }
}

