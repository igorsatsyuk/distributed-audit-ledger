package lt.satsyuk.distributed.audit.auditwriter.config;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Shared validation helpers for Web3j-related configuration values. */
public final class Web3jValidationUtils {

    private static final Pattern ETH_PRIVATE_KEY = Pattern.compile("^(0x)?[0-9a-fA-F]{64}$");
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final BigInteger SECP256K1_ORDER = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

    private Web3jValidationUtils() {
    }

    public static boolean isValidPrivateKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        if (!ETH_PRIVATE_KEY.matcher(trimmed).matches()) {
            return false;
        }
        String hex = trimmed.startsWith("0x") ? trimmed.substring(2) : trimmed;
        BigInteger numeric = new BigInteger(hex, 16);
        return numeric.signum() > 0 && numeric.compareTo(SECP256K1_ORDER) < 0;
    }

    public static boolean isValidContractAddress(String value) {
        return value != null && ETH_ADDRESS.matcher(value).matches();
    }

    public static boolean isZeroAddress(String value) {
        return value != null && ZERO_ADDRESS.equalsIgnoreCase(value);
    }
}

