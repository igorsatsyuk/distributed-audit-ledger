package lt.satsyuk.distributed.audit.auditwriter.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit Kafka listener container setup for the audit-writer consumer.
 *
 * <p>Uses {@link ErrorHandlingDeserializer} to wrap
 * {@link org.springframework.kafka.support.serializer.JsonDeserializer} so that
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
 * <p>Three exception types receive special handling:
 * <ul>
 *   <li>{@link BlockchainWriterService.NonRecoverableEventException} — registered as
 *       non-retryable; skips back-off and is forwarded immediately to the DLT.</li>
 *   <li>{@link BlockchainWriterService.BlockchainNotConfiguredException} — goes through
 *       the configured fixed back-off (so the consumer does not spin in a tight loop),
 *       and then the custom recoverer re-throws it without publishing to the DLT.  The
 *       offset stays uncommitted and the record is redelivered once configuration is
 *       present.</li>
 *   <li>{@link BlockchainWriterService.ReceiptTimeoutException} — retried with the
 *       container back-off (no explicit thread sleep in the recoverer) and re-thrown by
 *       the recoverer without publishing to the DLT when retries are exhausted, because
 *       the write outcome is unknown and may still be mined after timeout.</li>
 * </ul>
 *
 * <p>The DLT producer uses {@link DltValueSerializer} to preserve raw {@code byte[]}
 * values from deserialization failures unchanged, while still serializing normal
 * event objects as JSON.
 */
@Configuration
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);
    private static final String JSON_DESERIALIZER_CLASS = "org.springframework.kafka.support.serializer.JsonDeserializer";
    private static final String JSON_TRUSTED_PACKAGES_CONFIG = "spring.json.trusted.packages";
    private static final String JSON_USE_TYPE_INFO_HEADERS_CONFIG = "spring.json.use.type.headers";
    private static final String JSON_VALUE_DEFAULT_TYPE_CONFIG = "spring.json.value.default.type";

    static final long RETRY_ATTEMPTS = 2L;
    static final long DEFAULT_RETRY_INTERVAL_MS = 2_000L;

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ConfigurableEnvironment environment
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        mergeKafkaOverrides(props, environment, "spring.kafka.producer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, DltValueSerializer.class);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, AuditEvent> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:audit-writer-consumer}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset,
            ConfigurableEnvironment environment,
            @Value("${spring.kafka.consumer.properties.spring.json.value.default.type:"
                    + "lt.satsyuk.distributed.audit.event.AuditEvent}") String valueDefaultType
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        mergeKafkaOverrides(props, environment, "spring.kafka.consumer");
        // Offset commits must stay container-controlled so failed blockchain writes
        // are not acknowledged before retry/DLT handling completes.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Wrap JsonDeserializer with ErrorHandlingDeserializer so poison records
        // (bad JSON / unknown type) are forwarded to the error handler, not stuck on
        // the same partition offset.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JSON_DESERIALIZER_CLASS);
        props.put(JSON_TRUSTED_PACKAGES_CONFIG, "lt.satsyuk.distributed.audit.event");
        props.put(JSON_USE_TYPE_INFO_HEADERS_CONFIG, false);
        props.put(JSON_VALUE_DEFAULT_TYPE_CONFIG, valueDefaultType);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topics.user-login-events-dlt}") String deadLetterTopic,
            @Value("${kafka.listener.retry-interval-ms:2000}") long retryIntervalMs
    ) {
        long effectiveRetryIntervalMs = retryIntervalMs > 0L ? retryIntervalMs : DEFAULT_RETRY_INTERVAL_MS;
        if (retryIntervalMs <= 0L) {
            log.warn("[audit-writer] kafka.listener.retry-interval-ms={} is invalid; using default {} ms to avoid tight re-delivery loops",
                    retryIntervalMs, effectiveRetryIntervalMs);
        }


        // Always route DLT records to partition 0.
        // Using record.partition() would require the DLT topic to have at least as many
        // partitions as the source topic; with auto-creation that is not guaranteed.
        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (failedRecord, ex) -> new TopicPartition(deadLetterTopic, 0)
        );

        // Custom recoverer: skip DLT for infrastructure-misconfiguration failures so
        // the offset remains uncommitted and the record is redelivered once config is present.
        // For all other exceptions, fall through to the standard DLT recoverer.
        var safeRecoverer = (org.springframework.kafka.listener.ConsumerRecordRecoverer)
                (failedRecord, exception) -> {
                    BlockchainWriterService.BlockchainNotConfiguredException notCfg =
                            findCause(exception, BlockchainWriterService.BlockchainNotConfiguredException.class);
                    if (notCfg != null) {
                        log.error("[audit-writer] Blockchain not configured ({}) — offset will NOT be advanced "
                                + "to DLT; restart/redeploy with corrected settings from the error message before redelivery can succeed. "
                                + "topic={} partition={} offset={}",
                                notCfg.getMessage(), failedRecord.topic(), failedRecord.partition(), failedRecord.offset());
                        throw notCfg; // rethrow to keep the partition offset uncommitted
                    }

                    BlockchainWriterService.ReceiptTimeoutException timeout =
                            findCause(exception, BlockchainWriterService.ReceiptTimeoutException.class);
                    if (timeout != null) {
                        log.warn("[audit-writer] Receipt wait timed out ({}) — offset will NOT be advanced to DLT; "
                                        + "transaction outcome is unknown and will be re-checked on redelivery. "
                                        + "topic={} partition={} offset={}",
                                timeout.getMessage(), failedRecord.topic(), failedRecord.partition(), failedRecord.offset());
                        throw timeout;
                    }
                    dltRecoverer.accept(failedRecord, exception);
                };

        // Use standard backoff for all errors; for ReceiptTimeoutException specifically,
        // the service tracks in-flight writes and will backoff before resubmitting on Kafka redelivery.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(safeRecoverer,
                new FixedBackOff(effectiveRetryIntervalMs, RETRY_ATTEMPTS));

        // Skip retries for known terminal malformed events.
        // Exclude BlockchainNotConfiguredException from this list so that unconfigured startup
        // still includes a backoff pause, preventing a tight re-poll loop while the service
        // awaits configuration.
        errorHandler.addNotRetryableExceptions(
                BlockchainWriterService.NonRecoverableEventException.class,
                DeserializationException.class
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

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Value serializer for the DLT producer.
     *
     * <p>When {@link org.springframework.kafka.support.serializer.ErrorHandlingDeserializer}
     * catches a deserialization failure it stores the original raw bytes in the Kafka
     * headers; {@link DeadLetterPublishingRecoverer} then publishes those raw bytes as
     * the DLT record value.  Serializing {@code byte[]} with
     * {@link org.springframework.kafka.support.serializer.JsonSerializer}
     * would encode the array as JSON (base64 or integer array) and corrupt the payload.
     * This serializer passes {@code byte[]} values through unchanged and falls back to
     * JSON for all other types.
     */
    public static class DltValueSerializer implements Serializer<Object> {
        private final ObjectMapper objectMapper = CanonicalObjectMapperFactory.create();

        @Override
        public byte[] serialize(String topic, Object data) {
            if (data == null) {
                return null;
            }
            if (data instanceof byte[] raw) {
                return raw;
            }
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (JsonProcessingException ex) {
                throw new SerializationException("Failed to serialize DLT payload for topic " + topic, ex);
            }
        }
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> targetType) {
        Throwable current = error;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return targetType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }


    /**
     * Merges Kafka client properties from the Spring environment into {@code props}.
     *
     * <p>Uses {@link Binder} to resolve settings with relaxed binding, so
     * environment-variable overrides such as
     * {@code SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL} are honoured in addition
     * to dotted YAML/properties file entries.
     *
     * <p>Two namespaces are always merged:
     * <ol>
     *   <li>{@code spring.kafka.properties.*} — arbitrary shared Kafka client
     *       settings (security, SSL, SASL, etc.) that apply to all factories.</li>
     *   <li>{@code subPrefix.*} — consumer- or producer-specific settings.
     *       Keys under a nested {@code properties.*} sub-key are passed through as
     *       Kafka property keys; other keys have hyphens replaced with dots
     *       (e.g. {@code group-id} → {@code group.id}).</li>
     * </ol>
     *
     * <p>Serializer/deserializer entries set by the caller after this method are
     * applied last and override anything that Binder resolved.
     *
     * @param props      the Kafka client property map to populate
     * @param environment the Spring environment
     * @param subPrefix  dotted sub-prefix, e.g. {@code spring.kafka.consumer}
     */
    private static void mergeKafkaOverrides(Map<String, Object> props,
                                            ConfigurableEnvironment environment,
                                            String subPrefix) {
        Binder binder = Binder.get(environment);

        // Shared client settings — security, SSL, SASL, etc.
        binder.bind("spring.kafka.properties", Bindable.mapOf(String.class, String.class))
              .ifBound(props::putAll);

        // Consumer- or producer-specific settings.
        binder.bind(subPrefix, Bindable.mapOf(String.class, String.class))
              .ifBound(m -> m.forEach((key, value) -> {
                  if (key.startsWith("properties.")) {
                      // spring.kafka.consumer.properties.foo.bar → foo.bar
                      props.put(key.substring("properties.".length()), value);
                  } else {
                      // spring.kafka.consumer.group-id → group.id
                      props.put(key.replace('-', '.'), value);
                  }
              }));
    }
}
