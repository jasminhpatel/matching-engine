package com.solfini.util;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class PrivateKeyBasedSigner {

    // PKCS#8 prefix for a 32-byte Ed25519 private key
    private static final byte[] PKCS8_PREFIX = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03,
            0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };

    public static PrivateKey buildPrivateKeyFromRaw(byte[] raw32) throws Exception {
        if (raw32.length != 32)
            throw new IllegalArgumentException("Ed25519 private key must be 32 bytes");
        byte[] pkcs8 = new byte[PKCS8_PREFIX.length + 32];
        System.arraycopy(PKCS8_PREFIX, 0, pkcs8, 0, PKCS8_PREFIX.length);
        System.arraycopy(raw32, 0, pkcs8, PKCS8_PREFIX.length, 32);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
        return KeyFactory.getInstance("Ed25519").generatePrivate(spec);
    }

    public static String signEd25519(PrivateKey privateKey, String message) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(message.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();
        return Base64.getEncoder().encodeToString(signature); // Binance expects base64
    }

    public static String generateSignature(byte[] privateKey, String payload) {
        try {
            PrivateKey pk = buildPrivateKeyFromRaw(privateKey);
            String signature = signEd25519(pk, payload);
            return signature;
        } catch (Exception e) {
            System.out.println("Something went wrong while generating Signature");
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public static byte[] loadEd25519PrivateKey(String filepath) throws Exception {
        // Read whole file as string
        String pem = new String(Files.readAllBytes(Paths.get(filepath)));

        // Remove PEM headers/footers if present
        pem = pem.replaceAll("-----BEGIN (.*)-----", "").replaceAll("-----END (.*)-----", "").replaceAll("\\s", "");

        // Decode base64 to bytes
        byte[] keyBytes = java.util.Base64.getDecoder().decode(pem);

        // Check if this is PKCS#8 format (starts with specific header)
        if (keyBytes.length > 32) {
            // This is likely PKCS#8 format, extract the raw Ed25519 key
            // PKCS#8 Ed25519 private key has the raw 32-byte key at the end
            if (keyBytes.length >= 48) {
                // Extract last 32 bytes which should be the raw Ed25519 private key
                byte[] rawKey = new byte[32];
                System.arraycopy(keyBytes, keyBytes.length - 32, rawKey, 0, 32);
                return rawKey;
            }
        }

        return keyBytes;
    }


    public static byte[] getEd25519PrivateKey(String secret) {


        // Decode base64 to bytes
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);

        // Check if this is PKCS#8 format (starts with specific header)
        if (keyBytes.length > 32) {
            // This is likely PKCS#8 format, extract the raw Ed25519 key
            // PKCS#8 Ed25519 private key has the raw 32-byte key at the end
            if (keyBytes.length >= 48) {
                // Extract last 32 bytes which should be the raw Ed25519 private key
                byte[] rawKey = new byte[32];
                System.arraycopy(keyBytes, keyBytes.length - 32, rawKey, 0, 32);
                return rawKey;
            }
        }

        return keyBytes;
    }

    public static String signWithEd25519(final String payload, final byte[] privateKeyBytes) {
        // Create Ed25519 private key object from raw bytes
        final Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);

        // Create and initialize Ed25519 signer
        final Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);

        // Sign the payload
        final byte[] data = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        signer.update(data, 0, data.length);
        final byte[] signatureBytes = signer.generateSignature();

        // Return base64-encoded signature required by Binance
        return org.bouncycastle.util.encoders.Base64.toBase64String(signatureBytes);
    }

}
