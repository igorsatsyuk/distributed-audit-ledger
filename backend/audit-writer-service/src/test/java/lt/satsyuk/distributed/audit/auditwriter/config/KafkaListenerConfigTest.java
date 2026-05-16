package lt.satsyuk.distributed.audit.auditwriter.config;

import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaListenerConfigTest {

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, Object> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }

    @Test
    void kafkaErrorHandler_doesNotRegisterReceiptTimeoutAsNonRetryable() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        KafkaTemplate<String, Object> kafkaTemplate = mockKafkaTemplate();
        DefaultErrorHandler errorHandler = (DefaultErrorHandler)
                config.kafkaErrorHandler(kafkaTemplate, "user.login.events.dlt", 2_000L, 30L);

        assertThat(errorHandler.removeClassification(BlockchainWriterService.ReceiptTimeoutException.class))
                .as("ReceiptTimeoutException should use default retryable classification so container backoff can be applied")
                .isNull();
    }

    @Test
    void kafkaErrorHandler_clampsNonPositiveRetryIntervalAndStillClassifiesTimeout() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        KafkaTemplate<String, Object> kafkaTemplate = mockKafkaTemplate();
        DefaultErrorHandler errorHandler = (DefaultErrorHandler)
                config.kafkaErrorHandler(kafkaTemplate, "user.login.events.dlt", 0L, 30L);

        assertThat(errorHandler.removeClassification(BlockchainWriterService.ReceiptTimeoutException.class))
                .as("ReceiptTimeoutException should remain retryable when retry interval is clamped")
                .isNull();
    }

    @Test
    void kafkaErrorHandler_registersDeserializationExceptionAsNonRetryable() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        KafkaTemplate<String, Object> kafkaTemplate = mockKafkaTemplate();
        DefaultErrorHandler errorHandler = (DefaultErrorHandler)
                config.kafkaErrorHandler(kafkaTemplate, "user.login.events.dlt", 2_000L, 30L);

        assertThat(errorHandler.removeClassification(DeserializationException.class))
                .as("DeserializationException must bypass retries and go to DLT immediately")
                .isEqualTo(Boolean.FALSE);
    }
}

