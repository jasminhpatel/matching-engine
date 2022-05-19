package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;

public class FileStoreTest {

  @Test public void writeToFile() {

    FileStore.writeFile(55, "test".getBytes());
    byte[] b = FileStore.readFile(55);

    Assert.assertEquals(4, b.length);
    Assert.assertEquals("test", new String(b));

  }
}
