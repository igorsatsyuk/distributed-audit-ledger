package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;
import java.util.HashMap;

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
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ConfigurableEnvironment environment
    ) {
        Map<String, Object> props = new HashMap<>();
        mergeKafkaOverrides(props, environment);
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        props.putIfAbsent(JSON_ADD_TYPE_HEADERS_CONFIG, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate(
            ProducerFactory<String, AuditEvent> auditEventProducerFactory
    ) {
        return new KafkaTemplate<>(auditEventProducerFactory);
    }

    private static void mergeKafkaOverrides(Map<String, Object> props,
                                            ConfigurableEnvironment environment) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                mergeKafkaOverridesFromPropertySource(props, enumerablePropertySource, environment);
            }
        }
    }

    private static void mergeKafkaOverridesFromPropertySource(Map<String, Object> props,
                                                              EnumerablePropertySource<?> propertySource,
                                                              ConfigurableEnvironment environment) {
        for (String propertyName : propertySource.getPropertyNames()) {
            if (propertyName.startsWith("spring.kafka.properties.")) {
                String kafkaKey = propertyName.substring("spring.kafka.properties.".length());
                putIfPresent(props, kafkaKey, environment.getProperty(propertyName));
                continue;
            }

            if (!propertyName.startsWith("spring.kafka.producer.")) {
                continue;
            }

            String producerKey = propertyName.substring("spring.kafka.producer.".length());
            String kafkaKey = producerKey.startsWith("properties.")
                    ? producerKey.substring("properties.".length())
                    : producerKey.replace('-', '.');
            putIfPresent(props, kafkaKey, environment.getProperty(propertyName));
        }
    }

    private static void putIfPresent(Map<String, Object> props, String key, String value) {
        if (value != null) {
            props.put(key, value);
        }
    }
}

