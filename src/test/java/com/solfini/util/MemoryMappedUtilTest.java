package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;

public class MemoryMappedUtilTest {

  @Test public void memoryMappedFileCreation() throws Exception {
    MemoryMappedUtil util = new MemoryMappedUtil("largeFile.txt", 256, 64);
    util.getBuffer().put(0, (byte) 'A');
    util.getBuffer().put(1, (byte) 'A');
    util.getBuffer().put(2, (byte) 'A');
    util.getBuffer().put(3, (byte) 'A');

    util.getBuffer().put(2, (byte) 'B');

    long t0 = System.currentTimeMillis();

    long seq = 48274912332L;
    for (int i = 0; i < 1000000; i++) {
      util.getBuffer().putLong(8, seq);
      seq++;
      util.getBuffer().putLong(8, seq);
    }
    System.out.println("t0=" + (System.currentTimeMillis() - t0));
    Assert.assertEquals(48275912332L, util.getBuffer().getLong(8));
    System.out.println("seq=" + util.getBuffer().getLong(8));
  }

  public static void main(String[] args) throws Exception {
    MemoryMappedUtilTest test = new MemoryMappedUtilTest();
    test.memoryMappedFileCreation();
  }
}
