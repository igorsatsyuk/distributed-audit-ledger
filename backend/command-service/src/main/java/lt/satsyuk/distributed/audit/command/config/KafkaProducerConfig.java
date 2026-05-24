package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.common.serialization.StringSerializer;

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

    private static final String JSON_SERIALIZER_FQCN =
            "org.springframework.kafka.support.serializer.JsonSerializer";
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
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JSON_SERIALIZER_FQCN);
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
        Binder binder = Binder.get(environment);

        binder.bind("spring.kafka.properties", Bindable.mapOf(String.class, String.class))
                .ifBound(props::putAll);

        binder.bind("spring.kafka.producer", Bindable.mapOf(String.class, String.class))
                .ifBound(m -> m.forEach((key, value) -> {
                    if (key.startsWith("properties.")) {
                        props.put(key.substring("properties.".length()), value);
                    } else {
                        props.put(key.replace('-', '.'), value);
                    }
                }));
    }
}

