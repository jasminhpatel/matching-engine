
package com.solfini.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

/**
 *
 * @author Chris Mack
 *
 */
public class EncryptDecrypt2 implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(EncryptDecrypt2.class);

  public static final String AES = "AES";
  public static final String CIPHER = "AES/CBC/PKCS5PADDING";
  public static final String DEFAULT_KEY = "Bar12345Bar12345"; // 128 bit key
  private static byte[] initVector = "YDr78dzpkDs8f98H".getBytes(); // must be 16 bytes long

  private EncryptDecrypt2() {
    // do nothing
  }

  public static final void setInitVector(final String initVector_) {
    initVector = initVector_.getBytes();
  }

  public static final String encrypt(final String value) {
    return encrypt(DEFAULT_KEY, value);
  }

  public static final String encrypt(final String key, final String value) {
    try {
      IvParameterSpec iv = new IvParameterSpec(initVector);
      SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), AES);


      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);


      byte[] encrypted = cipher.doFinal(value.getBytes());
      String encryptedStr = Base64.getUrlEncoder().encodeToString(encrypted);
      LOGGER.debug(LOG_FMT_2, "encrypted string: ", encryptedStr);

      return encryptedStr;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  public static final String decrypt(final String encrypted) {
    return decrypt(DEFAULT_KEY, encrypted);
  }

  public static final String decrypt(final String key, final String encrypted) {
    try {
      IvParameterSpec iv = new IvParameterSpec(initVector);
      SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), AES);


      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);


      byte[] original = cipher.doFinal(Base64.getUrlDecoder().decode(encrypted));


      return new String(original);
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_4, "key=", key, ", encrypted=", encrypted);
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  public static final byte[] generateInitVector() {
    SecureRandom random = new SecureRandom();
    byte[] initialVector = new byte[16];
    // generate random 16 byte IV AES is always 16bytes
    random.nextBytes(initialVector);
    return initialVector;
  }

  public static void main(String[] args) {
    System.out.println(decrypt("46sA6z4XseDCLMD6BrLe2QlSOIA_O-6-NSIQ2936xZYs0VEgRjpf0EilQKWwKI9ibrDATFug323cD8yh4rL0aZnjIIMX2con0A7s_I898wA=") + " " + decrypt("Wg3gCAVSdM3dOkXhi1v1-uxBqQ4ANAv9rZu0rX-z0oBo-gmRREtQU4aII8Ej6Td7E7PetWI3LQlQsDlCFbC3SezDbDS_Uw3whNywoICs1rY="));
    System.out.println(decrypt("49cNEKAoDvAPkmpq1cpCNb_5GmQE2k8AhhbnDtcMGeg4ZDnKIIBjvYd4IX4SwK5DP4aI0BttmAjg7gmIIiedSkeApG4kFTOjdJJlVRgEIr4="));
    System.out.println(decrypt("-IT3P-czVJiGW9pAfRzhJnMzihIojrRze0KLNyenlZOCmkEjWePorWxkUebWnnKmn5gukYy7quOgwxRbrlYATZqnBx8Juhhx4W-UxXlvPpo="));
    System.out.println(decrypt("UquFxcPREp_sLSh1LdDIYqhwM8WsLv62RYMa97soIxc_KghNygKHe56iexltxLdMW_N8_sCSy20jPdAOqDw2zz7cusA0FZeUwjaMpInzLaU="));
    System.out.println(decrypt("bQScGiRalv4jtsTM6BD9YsSbZULAFs7MBzFxy-RS_6pRbmhv0by4aptOswDRDrQziWFTNLAkVRF3gmNV3mBiFfgnDJtV9omzptsQiOjdlXI="));
  }
}
