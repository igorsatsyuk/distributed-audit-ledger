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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

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
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "lt.satsyuk.distributed.audit.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AuditEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEvent> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Configure error handler to skip poison records after retries.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer(),
                new FixedBackOff(100, 3)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public ConsumerRecordRecoverer recoverer() {
        return (record, ex) -> {
            if (isSkippablePoisonRecord(ex)) {
                log.error(
                        "Poison pill detected; record will be skipped. topic=[{}], partition=[{}], offset=[{}], key=[{}]",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        ex
                );
                return;
            }

            throw new IllegalStateException(
                    "Non-poison processing error must not be skipped. topic=["
                            + record.topic() + "], partition=[" + record.partition() + "], offset=[" + record.offset() + "]",
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
}

