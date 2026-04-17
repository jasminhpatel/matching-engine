package com.solfini.util;

import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class HMAC {
    public static final String hmacSha256(final String data, final String key) throws Exception {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        final StringBuilder sb = new StringBuilder(2 * rawHmac.length);
        for (final byte b : rawHmac) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static final String hmacSha256Hex(final byte[] secret, final String data) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            final byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            final char[] HEX = "0123456789abcdef".toCharArray();
            final char[] chars = new char[out.length * 2];
            for (int i = 0, j = 0; i < out.length; i++) {
                final int v = out[i] & 0xFF;
                chars[j++] = HEX[v >>> 4];
                chars[j++] = HEX[v & 0x0F];
            }
            return new String(chars);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates HMAC-SHA512 signature from secret key and message bytes
     * Used for Kraken API authentication and other HMAC-SHA512 based signing
     *
     * @param secret       The secret key in bytes
     * @param messageBytes The message data in bytes
     * @return HMAC-SHA512 signature as byte array
     * @throws Exception if HMAC-SHA512 algorithm is not available or key is invalid
     */
    public static final byte[] hmacSha512(final byte[] secret, final byte[] messageBytes) throws Exception {
        try {
            final Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret, "HmacSHA512"));
            return mac.doFinal(messageBytes);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static String signHmacSHA256(String preHash, String secretKey) throws Exception {
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      mac.init(secretKeySpec);
      byte[] rawHmac = mac.doFinal(preHash.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(rawHmac);
    }

}
