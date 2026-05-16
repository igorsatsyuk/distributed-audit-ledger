package lt.satsyuk.distributed.audit.auditwriter.config;

import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaListenerConfigTest {

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, Object> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }

    @Test
    void kafkaErrorHandler_registersReceiptTimeoutAsNonRetryable() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        KafkaTemplate<String, Object> kafkaTemplate = mockKafkaTemplate();
        DefaultErrorHandler errorHandler = (DefaultErrorHandler)
                config.kafkaErrorHandler(kafkaTemplate, "user.login.events.dlt", 2_000L);

        assertThat(errorHandler.removeClassification(BlockchainWriterService.ReceiptTimeoutException.class))
                .as("ReceiptTimeoutException must be classified as non-retryable by the Kafka error handler")
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void kafkaErrorHandler_clampsNonPositiveRetryIntervalAndStillClassifiesTimeout() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        KafkaTemplate<String, Object> kafkaTemplate = mockKafkaTemplate();
        DefaultErrorHandler errorHandler = (DefaultErrorHandler)
                config.kafkaErrorHandler(kafkaTemplate, "user.login.events.dlt", 0L);

        assertThat(errorHandler.removeClassification(BlockchainWriterService.ReceiptTimeoutException.class))
                .as("ReceiptTimeoutException classification must remain non-retryable when retry interval is clamped")
                .isEqualTo(Boolean.FALSE);
    }
}

