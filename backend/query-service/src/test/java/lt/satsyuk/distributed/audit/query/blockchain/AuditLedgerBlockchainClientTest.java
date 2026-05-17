package lt.satsyuk.distributed.audit.query.blockchain;

import lt.satsyuk.distributed.audit.query.api.BlockchainIntegrityException;
import lt.satsyuk.distributed.audit.query.config.Web3jProperties;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AuditLedgerBlockchainClientTest {

    @Test
    void resolveFromBlockParameterUsesEarliestForLocalGanache() {
        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L);

        DefaultBlockParameter result = client.resolveFromBlockParameter();

        assertEquals(DefaultBlockParameterName.EARLIEST, result);
    }

    @Test
    void resolveFromBlockParameterRejectsZeroBlockForNonLocalRpc() {
        AuditLedgerBlockchainClient client = newClient("https://example.com", 0L);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                client::resolveFromBlockParameter);
        assertEquals("web3j.contract-deployment-block must be configured for non-local RPC endpoints",
                ex.getMessage());
    }

    private AuditLedgerBlockchainClient newClient(String clientAddress, long deploymentBlock) {
        Web3jProperties props = new Web3jProperties();
        props.setClientAddress(clientAddress);
        props.setContractDeploymentBlock(deploymentBlock);
        props.setContractAddress("0x1111111111111111111111111111111111111111");
        return new AuditLedgerBlockchainClient(mock(Web3j.class), props);
    }

}

