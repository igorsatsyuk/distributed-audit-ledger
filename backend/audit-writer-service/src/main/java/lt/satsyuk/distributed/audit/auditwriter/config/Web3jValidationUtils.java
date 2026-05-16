package lt.satsyuk.distributed.audit.auditwriter.config;

import java.util.regex.Pattern;

/** Shared validation helpers for Web3j-related configuration values. */
public final class Web3jValidationUtils {

    private static final Pattern ETH_PRIVATE_KEY = Pattern.compile("^(0x)?[0-9a-fA-F]{64}$");
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private Web3jValidationUtils() {
    }

    public static boolean isValidPrivateKey(String value) {
        return value != null && !value.isBlank() && ETH_PRIVATE_KEY.matcher(value.trim()).matches();
    }

    public static boolean isValidContractAddress(String value) {
        return value != null && ETH_ADDRESS.matcher(value).matches();
    }

    public static boolean isZeroAddress(String value) {
        return value != null && ZERO_ADDRESS.equalsIgnoreCase(value);
    }
}

