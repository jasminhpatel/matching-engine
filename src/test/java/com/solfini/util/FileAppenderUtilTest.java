package com.solfini.util;

public class FileAppenderUtilTest {

  public static void main(String[] args) throws Exception {
    FileAppenderUtil util = new FileAppenderUtil("file", "test.data");

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 10000; i++)
      util.appendWrite(5, ("the text" + i + "\n").getBytes());

    System.out.println("t=" + (System.currentTimeMillis() - t0));


    t0 = System.currentTimeMillis();
    for (int i = 0; i < 10000; i++)
      util.appendWrite(5, ("the text" + i + "\n").getBytes());

    System.out.println("t=" + (System.currentTimeMillis() - t0));
    Thread.sleep(500000000);

  }
}
