package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuditLedgerContractTest {

    @Test
    void load_returnsContractBoundToGivenAddress() {
        Web3j web3j = mock(Web3j.class);
        Credentials credentials = Credentials.create(ECKeyPair.create(BigInteger.ONE));

        AuditLedgerContract contract = AuditLedgerContract.load(
                "0x0000000000000000000000000000000000000001",
                web3j,
                credentials,
                new DefaultGasProvider()
        );

        assertThat(contract.getContractAddress()).isEqualTo("0x0000000000000000000000000000000000000001");
    }

    @Test
    void executeContractCall_wrapsCompletionExceptionCause() {
        IllegalStateException rootCause = new IllegalStateException("rpc failed");

        assertThatThrownBy(() -> invokeExecuteContractCall(() -> {
            throw new CompletionException(rootCause);
        }, "Failed to append AuditLedger record"))
                .isInstanceOf(AuditLedgerContract.ContractOperationException.class)
                .hasMessage("Failed to append AuditLedger record")
                .hasCause(rootCause);
    }

    @Test
    void executeContractCall_wrapsCompletionExceptionWithoutCauseUsingOriginalException() {
        CompletionException completionException = new CompletionException("timeout", null);

        assertThatThrownBy(() -> invokeExecuteContractCall(() -> {
            throw completionException;
        }, "Failed to query AuditLedger owner"))
                .isInstanceOf(AuditLedgerContract.ContractOperationException.class)
                .hasMessage("Failed to query AuditLedger owner")
                .hasCause(completionException);
    }

    @Test
    void executeContractCall_wrapsRuntimeException() {
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

