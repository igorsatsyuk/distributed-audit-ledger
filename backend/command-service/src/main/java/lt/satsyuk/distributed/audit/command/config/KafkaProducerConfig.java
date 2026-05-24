package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit producer bean for {@code KafkaTemplate<String, AuditEvent>}.
 *
 * <p>Spring Boot 4 may otherwise expose only a raw {@code KafkaTemplate<?, ?>}
 * bean, which does not satisfy the strongly-typed constructor dependency.
 */
@Configuration
public class KafkaProducerConfig {

    private static final String JSON_ADD_TYPE_HEADERS_CONFIG = "spring.json.add.type.headers";

    @Bean
    @Primary
    public ProducerFactory<String, AuditEvent> auditEventProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        props.put(JSON_ADD_TYPE_HEADERS_CONFIG, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate(
            ProducerFactory<String, AuditEvent> auditEventProducerFactory
    ) {
        return new KafkaTemplate<>(auditEventProducerFactory);
    }
}

