package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;

public class TestTransactionUtil {

  @Test
  public void conversionTest1() {
    final String encoded = TransactionUtil.compose(10, false);
    final TransactionData decoded = TransactionUtil.decompose(encoded);

    Assert.assertEquals(10, decoded.id);
    Assert.assertFalse(decoded.end);
  }

  @Test
  public void conversionTest2() {
    final long id = System.nanoTime();
    final String encoded = TransactionUtil.compose(id, true);
    final TransactionData decoded = TransactionUtil.decompose(encoded);

    Assert.assertEquals(id, decoded.id);
    Assert.assertTrue(decoded.end);
  }
}
