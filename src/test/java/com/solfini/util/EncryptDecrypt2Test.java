package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;
import static com.solfini.util.EncryptDecrypt2.*;

public class EncryptDecrypt2Test {

  @Test
  public void encryptAndDecryptWithDefaultKey() {
    String key = DEFAULT_KEY;

    String encrypted = encrypt(key, "password");
    String decrypted = decrypt(key, encrypted);

    String decrypted2 = decrypt(key, "ckaZfQKgx_mqnYpEBYGl0Q==");

    Assert.assertEquals("69aMIYHssutAdOxY3Q3ZLQ==", encrypted);
    Assert.assertEquals("password", decrypted);

  }
}
