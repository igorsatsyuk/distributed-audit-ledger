package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuditLedgerContractTest {

    @Test
    void load_returnsContractBoundToGivenAddress() {
        Web3j web3j = mock(Web3j.class);
        Credentials credentials = Credentials.create("0x59c6995e998f97a5a0044966f094538f33f9f7f5f09e4f9b2d71f88f5b4f4d87");

        AuditLedgerContract contract = AuditLedgerContract.load(
                "0x0000000000000000000000000000000000000001",
                web3j,
                credentials,
                new DefaultGasProvider()
        );

        assertThat(contract.getContractAddress()).isEqualTo("0x0000000000000000000000000000000000000001");
    }

    @Test
    void executeContractCall_wrapsCompletionExceptionCause() throws Exception {
        IllegalStateException rootCause = new IllegalStateException("rpc failed");

        assertThatThrownBy(() -> invokeExecuteContractCall(() -> {
            throw new CompletionException(rootCause);
        }, "Failed to append AuditLedger record"))
                .isInstanceOf(AuditLedgerContract.ContractOperationException.class)
                .hasMessage("Failed to append AuditLedger record")
                .hasCause(rootCause);
    }

    @Test
    void executeContractCall_wrapsCompletionExceptionWithoutCauseUsingOriginalException() throws Exception {
        CompletionException completionException = new CompletionException("timeout", null);

        assertThatThrownBy(() -> invokeExecuteContractCall(() -> {
            throw completionException;
        }, "Failed to query AuditLedger owner"))
                .isInstanceOf(AuditLedgerContract.ContractOperationException.class)
                .hasMessage("Failed to query AuditLedger owner")
                .hasCause(completionException);
    }

    @Test
    void executeContractCall_wrapsRuntimeException() throws Exception {
        RuntimeException runtimeException = new RuntimeException("boom");

        assertThatThrownBy(() -> invokeExecuteContractCall(() -> {
            throw runtimeException;
        }, "Failed to query AuditLedger isHashExists"))
                .isInstanceOf(AuditLedgerContract.ContractOperationException.class)
                .hasMessage("Failed to query AuditLedger isHashExists")
                .hasCause(runtimeException);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeExecuteContractCall(Supplier<T> supplier, String message) throws Exception {
        Method method = AuditLedgerContract.class.getDeclaredMethod("executeContractCall", Supplier.class, String.class);
        method.setAccessible(true);
        try {
            return (T) method.invoke(null, supplier, message);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }
}

