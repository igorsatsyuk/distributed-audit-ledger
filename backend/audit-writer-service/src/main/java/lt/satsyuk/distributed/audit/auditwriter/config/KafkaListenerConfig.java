package lt.satsyuk.distributed.audit.auditwriter.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit Kafka listener container setup for the audit-writer consumer.
 *
 * <p>Uses {@link ErrorHandlingDeserializer} to wrap {@link JsonDeserializer} so that
 * poison records (malformed JSON, unknown payload type) are routed to the error handler
 * rather than stalling the partition at the same offset indefinitely.
 *
 * <p>The {@link DefaultErrorHandler} is configured with a fixed back-off of
 * {@value #RETRY_INTERVAL_MS} ms and {@value #RETRY_ATTEMPTS} retry attempts before
 * the record is logged and skipped.  A Dead-Letter Topic (DLT) can be wired in here
 * once a Kafka producer bean is available.
 */
@Configuration
public class KafkaListenerConfig {

    static final long RETRY_INTERVAL_MS = 2_000L;
    static final long RETRY_ATTEMPTS    = 2L;

    @Bean
    public ConsumerFactory<String, AuditEvent> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:audit-writer-consumer}") String groupId
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Wrap JsonDeserializer with ErrorHandlingDeserializer so poison records
        // (bad JSON / unknown type) are forwarded to the error handler, not stuck on
        // the same partition offset.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "lt.satsyuk.distributed.audit.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UserLoggedInEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEvent> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // Explicit error handler: retry up to RETRY_ATTEMPTS times with a fixed back-off,
        // then log and skip the record.  This prevents an infinite retry loop while
        // still giving transient failures a chance to recover.
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS)));
        return factory;
    }
}

