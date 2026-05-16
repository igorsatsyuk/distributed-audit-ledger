package lt.satsyuk.distributed.audit.auditwriter.config;

import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaListenerConfigTest {

    @Test
    void kafkaErrorHandler_registersReceiptTimeoutAsNonRetryable() {
        KafkaListenerConfig config = new KafkaListenerConfig();
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = (DefaultErrorHandler)
                config.kafkaErrorHandler(kafkaTemplate, "user.login.events.dlt", 2_000L);

        assertThat(errorHandler.removeClassification(BlockchainWriterService.ReceiptTimeoutException.class))
                .as("ReceiptTimeoutException must be classified as non-retryable by the Kafka error handler")
                .isEqualTo(Boolean.FALSE);
    }
}

