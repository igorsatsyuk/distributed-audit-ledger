package lt.satsyuk.distributed.audit.auditwriter.config;

import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit Kafka listener container setup for the audit-writer consumer.
 *
 * <p>Uses {@link ErrorHandlingDeserializer} to wrap {@link JsonDeserializer} so that
 * poison records (malformed JSON, unknown payload type) are routed to the container
 * error handler rather than stalling the partition at the same offset indefinitely.
 *
 * <p>The {@link DefaultErrorHandler} retries with a fixed back-off of
 * {@code kafka.listener.retry-interval-ms} ms (default 2 000 ms) and
 * {@value #RETRY_ATTEMPTS} retry attempts, then routes the record to a Dead-Letter
 * Topic (DLT).  The DLT is always written to partition 0 to avoid a partition-count
 * mismatch between the source topic and the DLT (which is auto-created by the broker
 * with the default partition count).
 *
 * <p>Two exception types bypass the DLT recoverer:
 * <ul>
 *   <li>{@link BlockchainWriterService.BlockchainNotConfiguredException} — the offset
 *       stays uncommitted and the record is redelivered once configuration is present.</li>
 *   <li>{@link BlockchainWriterService.NonRecoverableEventException} — skips retries
 *       and is forwarded directly to the DLT.</li>
 * </ul>
 */
@Configuration
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

    static final long RETRY_ATTEMPTS = 2L;

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

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

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topics.user-login-events-dlt}") String deadLetterTopic,
            @Value("${kafka.listener.retry-interval-ms:2000}") long retryIntervalMs
    ) {
        // Always route DLT records to partition 0.
        // Using record.partition() would require the DLT topic to have at least as many
        // partitions as the source topic; with auto-creation that is not guaranteed.
        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(deadLetterTopic, 0)
        );

        // Custom recoverer: skip DLT for infrastructure-misconfiguration failures so
        // the offset remains uncommitted and the record is redelivered once config is present.
        // For all other exceptions, fall through to the standard DLT recoverer.
        var safeRecoverer = (org.springframework.kafka.listener.ConsumerRecordRecoverer)
                (record, exception) -> {
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    if (cause instanceof BlockchainWriterService.BlockchainNotConfiguredException notCfg) {
                        log.error("[audit-writer] Blockchain not configured — offset will NOT be advanced "
                                + "to DLT; record will be redelivered once web3j.private-key and "
                                + "web3j.contract-address are set. topic={} partition={} offset={}",
                                record.topic(), record.partition(), record.offset());
                        throw notCfg; // rethrow to keep the partition offset uncommitted
                    }
                    dltRecoverer.accept(record, exception);
                };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(safeRecoverer,
                new FixedBackOff(retryIntervalMs, RETRY_ATTEMPTS));

        // Skip retries for known non-recoverable exceptions — send straight to DLT (or, in
        // the case of BlockchainNotConfiguredException, stay uncommitted via the recoverer above).
        errorHandler.addNotRetryableExceptions(
                BlockchainWriterService.NonRecoverableEventException.class,
                BlockchainWriterService.BlockchainNotConfiguredException.class
        );

        return errorHandler;
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEvent> consumerFactory,
            CommonErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
