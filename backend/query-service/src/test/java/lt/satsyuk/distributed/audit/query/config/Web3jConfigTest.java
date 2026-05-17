package lt.satsyuk.distributed.audit.query.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class Web3jConfigTest {

    @Test
    void web3jBeanRejectsBlankClientAddress() {
        Web3jConfig config = new Web3jConfig();
        Web3jProperties props = new Web3jProperties();
        props.setClientAddress("   ");

        assertThrows(IllegalStateException.class, () -> config.web3j(props));
    }
}

