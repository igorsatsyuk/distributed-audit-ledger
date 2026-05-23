package lt.satsyuk.distributed.audit.contracts.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal HS256 JWT encoder/decoder shared by backend services.
 */
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String JWT_TYPE = "JWT";

    private final byte[] secret;
    private final String issuer;
    private final Duration expiration;
    private final ObjectMapper objectMapper;

    public JwtService(String secret, String issuer, Duration expiration) {
        this(secret, issuer, expiration, new ObjectMapper());
    }

    JwtService(String secret, String issuer, Duration expiration, ObjectMapper objectMapper) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.expiration = expiration;
        this.objectMapper = objectMapper;
    }

    public String generateToken(String subject, Collection<UserRole> roles, Instant issuedAt) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("JWT roles must not be empty");
        }
        Instant expiresAt = issuedAt.plus(expiration);

        Map<String, Object> header = Map.of("alg", JWT_ALGORITHM, "typ", JWT_TYPE);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", subject);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("roles", roles.stream().map(UserRole::name).sorted().toList());

        try {
            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = encode(sign(signingInput));
            return signingInput + "." + signature;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign JWT token", exception);
        }
    }

    public JwtClaims parseAndValidate(String token, Instant now) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("JWT token is missing");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException("JWT token format is invalid");
        }

        try {
            validateSignature(parts);
            Map<String, Object> header = decodeToMap(parts[0]);
            Map<String, Object> payload = decodeToMap(parts[1]);
            validateHeader(header);
            return toClaims(payload, now);
        } catch (JwtValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JwtValidationException("Failed to parse JWT token", exception);
        }
    }

    public Duration getExpiration() {
        return expiration;
    }

    public String getIssuer() {
        return issuer;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return encode(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JWT payload", exception);
        }
    }

    private void validateSignature(String[] parts) throws GeneralSecurityException {
        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = sign(signingInput);
        byte[] actualSignature = decode(parts[2]);
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new JwtValidationException("JWT signature is invalid");
        }
    }

    private void validateHeader(Map<String, Object> header) {
        if (!JWT_ALGORITHM.equals(header.get("alg"))) {
            throw new JwtValidationException("JWT algorithm is invalid");
        }
        if (!JWT_TYPE.equals(header.get("typ"))) {
            throw new JwtValidationException("JWT type is invalid");
        }
    }

    private JwtClaims toClaims(Map<String, Object> payload, Instant now) {
        String tokenIssuer = asString(payload.get("iss"), "iss");
        if (!issuer.equals(tokenIssuer)) {
            throw new JwtValidationException("JWT issuer is invalid");
        }

        String subject = asString(payload.get("sub"), "sub");
        Instant issuedAt = Instant.ofEpochSecond(asLong(payload.get("iat"), "iat"));
        Instant expiresAt = Instant.ofEpochSecond(asLong(payload.get("exp"), "exp"));
        if (!expiresAt.isAfter(now)) {
            throw new JwtValidationException("JWT token has expired");
        }

        Object rolesClaim = payload.get("roles");
        if (!(rolesClaim instanceof List<?> rolesList) || rolesList.isEmpty()) {
            throw new JwtValidationException("JWT roles claim is missing");
        }

        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (Object roleValue : rolesList) {
            try {
                roles.add(UserRole.valueOf(String.valueOf(roleValue)));
            } catch (IllegalArgumentException exception) {
                throw new JwtValidationException("JWT contains unsupported role: " + roleValue, exception);
            }
        }

        return new JwtClaims(subject, Set.copyOf(roles), issuedAt, expiresAt, tokenIssuer);
    }

    private Map<String, Object> decodeToMap(String tokenPart) {
        try {
            return objectMapper.readValue(decode(tokenPart), MAP_TYPE);
        } catch (Exception exception) {
            throw new JwtValidationException("JWT token payload is malformed", exception);
        }
    }

    private String asString(Object value, String claimName) {
        if (value == null) {
            throw new JwtValidationException("JWT claim '" + claimName + "' is missing");
        }
        String result = String.valueOf(value).trim();
        if (result.isEmpty()) {
            throw new JwtValidationException("JWT claim '" + claimName + "' is blank");
        }
        return result;
    }

    private long asLong(Object value, String claimName) {
        if (!(value instanceof Number number)) {
            throw new JwtValidationException("JWT claim '" + claimName + "' must be numeric");
        }
        return number.longValue();
    }

    private byte[] sign(String signingInput) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] input) {
        return URL_ENCODER.encodeToString(input);
    }

    private byte[] decode(String input) {
        try {
            return URL_DECODER.decode(input);
        } catch (IllegalArgumentException exception) {
            throw new JwtValidationException("JWT base64 encoding is invalid", exception);
        }
    }
}

