package lt.satsyuk.distributed.audit.eventstore.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.eventstore.consumer.AuditEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);
    private static final String JSON_DESERIALIZER_CLASS = "org.springframework.kafka.support.serializer.JsonDeserializer";
    private static final String JSON_TRUSTED_PACKAGES_CONFIG = "spring.json.trusted.packages";
    private static final String JSON_USE_TYPE_INFO_HEADERS_CONFIG = "spring.json.use.type.headers";
    private static final String JSON_VALUE_DEFAULT_TYPE_CONFIG = "spring.json.value.default.type";

    @Bean
    public ConsumerFactory<String, AuditEvent> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:event-store-consumer}") String groupId
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JSON_DESERIALIZER_CLASS);
        props.put(JSON_TRUSTED_PACKAGES_CONFIG, "lt.satsyuk.distributed.audit.event");
        props.put(JSON_USE_TYPE_INFO_HEADERS_CONFIG, false);
        props.put(JSON_VALUE_DEFAULT_TYPE_CONFIG, AuditEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEvent> consumerFactory,
            @Value("${spring.kafka.listener.error-handler.backoff-interval-ms:100}") long backoffIntervalMs,
            @Value("${spring.kafka.listener.error-handler.max-retries:3}") long maxRetries
    ) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Configure retry/backoff for poison records; values are externally configurable.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer(),
                new FixedBackOff(sanitizeBackoffInterval(backoffIntervalMs), sanitizeMaxRetries(maxRetries))
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public ConsumerRecordRecoverer recoverer() {
        return (recoverableRecord, ex) -> {
            if (isSkippablePoisonRecord(ex)) {
                log.error(
                        "Poison pill detected; record will be skipped. topic=[{}], partition=[{}], offset=[{}], key=[{}]",
                        recoverableRecord.topic(),
                        recoverableRecord.partition(),
                        recoverableRecord.offset(),
                        recoverableRecord.key(),
                        ex
                );
                return;
            }

            throw new IllegalStateException(
                    "Non-poison processing error must not be skipped. topic=["
                            + recoverableRecord.topic() + "], partition=[" + recoverableRecord.partition() + "], offset=[" + recoverableRecord.offset() + "]",
                    ex
            );
        };
    }

    private boolean isSkippablePoisonRecord(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof AuditEventConsumer.SkippableDeserializationException
                    || current instanceof DeserializationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long sanitizeBackoffInterval(long configuredIntervalMs) {
        return configuredIntervalMs >= 0 ? configuredIntervalMs : 100;
    }

    private long sanitizeMaxRetries(long configuredRetries) {
        return configuredRetries >= 0 ? configuredRetries : 3;
    }
}

