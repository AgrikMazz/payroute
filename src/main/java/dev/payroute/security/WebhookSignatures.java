package dev.payroute.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class WebhookSignatures {
    private WebhookSignatures() {}
    public static String hmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("HMAC is unavailable", e); }
    }
    public static boolean verify(String secret, String body, String signature) {
        if (signature == null) return false;
        try {
            byte[] expected = HexFormat.of().parseHex(hmacSha256(secret, body));
            byte[] actual = HexFormat.of().parseHex(signature.trim());
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) { return false; }
    }
}
