package com.solfini.util;

import java.nio.ByteBuffer;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

public class IpcTest {
  private static final String DIR = "chronicle-test";

  public static final void startProducer() {
    new Thread() {
      @Override
      public void run() {
        System.out.println("starting producer...");
        ChronicleQueue queue = SingleChronicleQueueBuilder.single(DIR).blockSize(65536).rollCycle(RollCycles.MINUTELY).build();
        ExcerptAppender appender = queue.acquireAppender();
        ByteBuffer ipcBuffer = ByteBuffer.allocate(8192);

        for (int i = 0; i < Integer.MAX_VALUE; i++) {
          ipcBuffer.clear();
          ipcBuffer.put(("data" + i).getBytes());
          Bytes<ByteBuffer> bbb = Bytes.wrapForWrite(ipcBuffer);
          appender.writeBytes(bbb);
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }.start();
  }

  public static final void startConsumer() {
    new Thread() {
      @Override
      public void run() {
        System.out.println("starting consumer...");
        ChronicleQueue queue = SingleChronicleQueueBuilder.single(DIR).blockSize(65536).rollCycle(RollCycles.MINUTELY).build();
        ExcerptTailer tailer = queue.createTailer().toEnd(); // skip to end, don't read old messages
        Bytes bytes = Bytes.allocateDirect(8192);

        while (true) {
          try {
            long ipcIndex = tailer.index();
            boolean read = tailer.readBytes(bytes);
            int len = bytes.length();
            byte[] data = new byte[len];
            bytes.read(data);
            if (read) {
              System.out.println("read " + data);
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

      }
    }.start();
  }

  public static void main(final String[] args) {
    if ("producer".equals(args[0]))
      startProducer();
    else
      startConsumer();
  }
}
