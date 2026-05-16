package lt.satsyuk.distributed.audit.auditwriter.consumer;

import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditEventConsumer}.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private BlockchainWriterService blockchainWriterService;

    @InjectMocks
    private AuditEventConsumer consumer;

    @Test
    void consume_delegatesToBlockchainWriterService() {
        UserLoggedInEvent event = UserLoggedInEvent.of("user1", "1.2.3.4", "TestAgent");

        consumer.consume(event, 0, 0L);

        verify(blockchainWriterService, times(1)).anchorEvent(event);
    }

    @Test
    void consume_rethrowsBlockchainWriteException() {
        UserLoggedInEvent event = UserLoggedInEvent.of("userFail", null, null);
        doThrow(new BlockchainWriterService.BlockchainWriteException("err", new RuntimeException()))
                .when(blockchainWriterService).anchorEvent(event);

        assertThatThrownBy(() -> consumer.consume(event, 0, 1L))
                .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class);

        verify(blockchainWriterService).anchorEvent(event);
    }

    @Test
    void consume_rethrowsUnexpectedException() {
        UserLoggedInEvent event = UserLoggedInEvent.of("userErr", null, null);
        doThrow(new RuntimeException("unexpected"))
                .when(blockchainWriterService).anchorEvent(event);

        assertThatThrownBy(() -> consumer.consume(event, 0, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("unexpected");

        verify(blockchainWriterService).anchorEvent(event);
    }

    @Test
    void consume_throwsNonRecoverableForNullPayload() {
        assertThatThrownBy(() -> consumer.consume(null, 1, 42L))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("tombstone");

        verifyNoInteractions(blockchainWriterService);
    }
}

