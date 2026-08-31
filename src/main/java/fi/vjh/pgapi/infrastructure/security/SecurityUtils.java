package fi.vjh.pgapi.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecurityUtils {

    // generic, classic test secret
    public static final String SECRET = "SAIPPUAKAUPPIAS";

    private SecurityUtils() {
        throw new NoInstantiation();
    }

    public static String calculateHmac(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static boolean isValidSignature(String payload, String expectedSig, String secret) {
        try {
            String expected = expectedSig.trim();
            if (expected.startsWith("sha256=")) {
                expected = expected.substring("sha256=".length());
            }
            String actual = calculateHmac(payload, secret);
            return MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private static class NoInstantiation extends RuntimeException {}

}
