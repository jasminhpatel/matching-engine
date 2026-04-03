package com.solfini.util.blockchain.util;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.SignatureException;


/*
 * This application demonstrates the use of RSA Security's JCE provider to create an HMAC (Hash-based message authentication code).
 *
 * The JsafeJCE provider can create an HMAC based on any of the following: MD5 RIPEMD160 SHA1 SHA224 SHA256 SHA384 SHA512 This sample
 * demonstrates how to do this with the SHA384 digest.
 */
public class HmacSHA384 {

  private static final String HMAC_TYPE = "SHA384";
  private static final String HMAC_SHA384_ALGORITHM = "HmacSHA384";
  /*
   * public static byte[] getRandomKey(String s) throws Exception { // Start by creating a pseudo random number generator,
   * Security.addProvider(new com.rsa.jsafe.provider.JsafeJCE()); SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "JsafeJCE");
   * 
   * // Seeding is the most important aspect of dealing with a secure // random number generator. It is extremely important that you seed //
   * the PRNG with a value that contains sufficient entropy. // The following example uses a seed generator.
   * random.setSeed(random.generateSeed(48));
   * 
   * // The key data should be the same size as the digest algorithm. If the // key data is too small, zeros will be appended to the end of
   * the key // which will make the HMAC not as strong. byte[] keyData = new byte[48]; random.nextBytes(keyData); return keyData; }
   * 
   * public void runSample() throws Exception {
   * 
   * System.err.println("JCE HMAC Example: " + HMAC_TYPE);
   * 
   * // Add the JsafeJCE provider. // addJsafeJCE(); Security.addProvider(new com.rsa.jsafe.provider.JsafeJCE());
   * 
   * // Print out a list of the current providers. // printRegisteredProviders();
   * 
   * // For the purposes of the sample, // the message data to HMAC is an array of zeros. byte[] message = new byte[25]; //
   * Print.message(message);
   * 
   * // Start by creating a pseudo random number generator, // then generate a block of random bytes as the plaintext. // SecureRandom
   * random = SecureRandom.getInstance("ECDRBG", "JsafeJCE"); SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "JsafeJCE");
   * 
   * // Seeding is the most important aspect of dealing with a secure // random number generator. It is extremely important that you seed //
   * the PRNG with a value that contains sufficient entropy. // The following example uses a seed generator. //
   * random.setSeed(generateSeed());
   * 
   * // The key data should be the same size as the digest algorithm. If the // key data is too small, zeros will be appended to the end of
   * the key // which will make the HMAC not as strong. byte[] keyData = new byte[48]; random.nextBytes(keyData);
   * 
   * SecretKey key = null; Mac mac = null;
   * 
   * try { key = new SecretKeySpec(keyData, "Hmac" + HMAC_TYPE); mac = Mac.getInstance("Hmac" + HMAC_TYPE, "JsafeJCE"); mac.init(key);
   * mac.update(message); byte[] hmac = mac.doFinal();
   * 
   * System.err.println("hmac="+new String(hmac)); // Print.mac(hmac); // Print.successfulEnding(); } finally { // Cryptographic objects
   * should be cleared once they are no longer // needed. // SensitiveData.clear(key); // SensitiveData.clear(mac); //
   * SensitiveData.clear(random); // SensitiveData.clear(keyData); } }
   */

  public final static String hexdigest(String s, String key) throws Exception {
    return hexdigest(s.getBytes(), key.getBytes());
  }

  // convert data to HMAC-SHA384 as hexadecimal
  public final static String hexdigest(byte[] message, byte[] keyData) throws Exception {
    // Security.addProvider(new com.rsa.jsafe.provider.JsafeJCE());
    SecretKey key = null;
    Mac mac = null;

    try {
      key = new SecretKeySpec(keyData, HMAC_SHA384_ALGORITHM);
      mac = Mac.getInstance(HMAC_SHA384_ALGORITHM);
      mac.init(key);
      mac.update(message);
      byte[] hmac = mac.doFinal();
      // System.out.println("hmac="+new String(hmac));
      // convert to hex
      String hd;
      BigInteger hash = new BigInteger(1, hmac);
      hd = hash.toString(16); // BigInteger strips leading 0's
      while (hd.length() < 32) {
        hd = "0" + hd;
      } // pad with leading 0's
      return hd;
    } finally {
      // Cryptographic objects should be cleared once they are no longer
      // needed.
      // SensitiveData.clear(key);
      // SensitiveData.clear(mac);
      // SensitiveData.clear(random);
      // SensitiveData.clear(keyData);
    }
  }

  public static String calculateRFC2104HMAC(String key, String data) throws SignatureException {
    return calculateRFC2104HMAC(key.getBytes(), data.getBytes());
  }

  /**
   * Computes RFC 2104-compliant HMAC signature. * @param data The data to be signed.
   * 
   * @param key The signing key.
   * @return The Base64-encoded RFC 2104-compliant HMAC signature.
   * @throws SignatureException when signature generation fails
   */
  public static String calculateRFC2104HMAC(byte[] key, byte[] data) throws SignatureException {
    String result;
    try {
      // get an hmac_sha1 key from the raw key bytes
      SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA384_ALGORITHM);

      // get an hmac_sha1 Mac instance and initialize with the signing key
      Mac mac = Mac.getInstance(HMAC_SHA384_ALGORITHM);
      mac.init(signingKey);
      mac.update(data);
      // compute the hmac on input data bytes
      byte[] rawHmac = mac.doFinal();
      // System.out.println("hmac="+new String(rawHmac));
      // base64-encode the hmac
      result = new String(Base64.encodeBase64(rawHmac));
    } catch (Exception e) {
      throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
    }
    return result;
  }


  public static void main(String[] argv) throws Exception {
    // HmacSHA384 macInstance = new HmacSHA384();
    // macInstance.runSample();

    String key = "Yc0fNZRxKX4ULCuc";
    String data =
        "{\"id\":0,\"instrumentId\":4,\"symbol\":\"ETH/USD\",\"userId\":18,\"side\":1,\"ordType\":2,\"price\":100,\"price_scale\":2,\"quantity\":100,\"quantity_scale\":1}";
    String result = hexdigest(data, key);
    System.out.println("result=" + result);

    result = calculateRFC2104HMAC(key, data);
    System.out.println("result=" + result);


  }

}
