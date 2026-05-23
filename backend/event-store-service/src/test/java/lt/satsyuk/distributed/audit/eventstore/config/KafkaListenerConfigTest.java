package lt.satsyuk.distributed.audit.eventstore.config;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.eventstore.consumer.AuditEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KafkaListenerConfigTest {

    @Test
    void consumerFactory_disablesAutoCommitAndSetsAuditEventDefaults() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        MockEnvironment environment = new MockEnvironment();

        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, AuditEvent> consumerFactory =
                (org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, AuditEvent>)
                        config.consumerFactory("localhost:9092", "event-store-consumer");

        assertThat(consumerFactory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "event-store-consumer")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry("spring.json.value.default.type", AuditEvent.class.getName());
    }

    @Test
    void kafkaListenerContainerFactory_clampsNegativeBackoffAndRetryValues() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, AuditEvent> consumerFactory = mock(ConsumerFactory.class);

        assertThatCode(() -> config.kafkaListenerContainerFactory(consumerFactory, -50L, -1L))
                .doesNotThrowAnyException();
    }

    @Test
    void recoverer_skipsPoisonPillExceptions() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        ConsumerRecordRecoverer recoverer = config.recoverer();
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>("user.login.events", 0, 7L, "event-key", null);

        assertThatCode(() -> recoverer.accept(record,
                new AuditEventConsumer.SkippableDeserializationException("bad payload")))
                .doesNotThrowAnyException();
    }

    @Test
    void recoverer_skipsWhenDeserializationExceptionIsNestedCause() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        ConsumerRecordRecoverer recoverer = config.recoverer();
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>("user.login.events", 0, 8L, "event-key", null);
        RuntimeException wrapped = new RuntimeException("wrapper", mock(DeserializationException.class));

        assertThatCode(() -> recoverer.accept(record, wrapped)).doesNotThrowAnyException();
    }

    @Test
    void recoverer_rethrowsNonPoisonFailures() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        ConsumerRecordRecoverer recoverer = config.recoverer();
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>("user.login.events", 1, 9L, "event-key", null);
        RuntimeException processingFailure = new RuntimeException("db unavailable");

        assertThatThrownBy(() -> recoverer.accept(record, processingFailure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Non-poison processing error must not be skipped")
                .hasCause(processingFailure);
    }

    @Test
    void sanitizeHelpersFallBackToSafeDefaults() throws Exception {
        KafkaListenerConfig config = new KafkaListenerConfig();
        Method sanitizeBackoffInterval = KafkaListenerConfig.class.getDeclaredMethod("sanitizeBackoffInterval", long.class);
        Method sanitizeMaxRetries = KafkaListenerConfig.class.getDeclaredMethod("sanitizeMaxRetries", long.class);
        sanitizeBackoffInterval.setAccessible(true);
        sanitizeMaxRetries.setAccessible(true);

        assertThat((long) sanitizeBackoffInterval.invoke(config, -123L)).isEqualTo(100L);
        assertThat((long) sanitizeBackoffInterval.invoke(config, 250L)).isEqualTo(250L);
        assertThat((long) sanitizeMaxRetries.invoke(config, -5L)).isEqualTo(3L);
        assertThat((long) sanitizeMaxRetries.invoke(config, 8L)).isEqualTo(8L);
    }
}

