package lt.satsyuk.distributed.audit.auditwriter.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Web3jValidationUtilsTest {

    @Test
    void validatesPrivateKeyFormatAndRange() {
        String validPrivateKey = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String zeroPrivateKey = "0x0000000000000000000000000000000000000000000000000000000000000000";

        assertThat(Web3jValidationUtils.isValidPrivateKey(validPrivateKey)).isTrue();
        assertThat(Web3jValidationUtils.isValidPrivateKey(validPrivateKey.substring(2))).isTrue();
        assertThat(Web3jValidationUtils.isValidPrivateKey(zeroPrivateKey)).isFalse();
        assertThat(Web3jValidationUtils.isValidPrivateKey("0xXYZ")).isFalse();
        assertThat(Web3jValidationUtils.isValidPrivateKey(" ")).isFalse();
    }

    @Test
    void validatesContractAddressAndZeroAddress() {
        String validAddress = "0x1234567890123456789012345678901234567890";
        String zeroAddress = "0x0000000000000000000000000000000000000000";

        assertThat(Web3jValidationUtils.isValidContractAddress(validAddress)).isTrue();
        assertThat(Web3jValidationUtils.isValidContractAddress("1234567890")).isFalse();

        assertThat(Web3jValidationUtils.isZeroAddress(zeroAddress)).isTrue();
        assertThat(Web3jValidationUtils.isZeroAddress(zeroAddress.toUpperCase())).isTrue();
        assertThat(Web3jValidationUtils.isZeroAddress(validAddress)).isFalse();
    }

    @Test
    void validatesClientAddress() {
        assertThat(Web3jValidationUtils.isValidClientAddress("http://127.0.0.1:8545")).isTrue();
        assertThat(Web3jValidationUtils.isValidClientAddress("https://example.org/rpc")).isTrue();
        assertThat(Web3jValidationUtils.isValidClientAddress("ftp://example.org/rpc")).isFalse();
        assertThat(Web3jValidationUtils.isValidClientAddress("not-a-uri")).isFalse();
        assertThat(Web3jValidationUtils.isValidClientAddress(" ")).isFalse();
    }
}

