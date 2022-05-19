package com.solfini.matchengine;

import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.util.FastArrayList;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaPublisherThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaPublisherThread.class);

  private final OneToOneConcurrentArrayQueueCustom<byte[]> publisherToKafkaPublisherQueue;
  private final IdleStrategy idleStrategy;
  private final FastArrayList<byte[]> list = new FastArrayList<>(1024);

  public KafkaPublisherThread(final IdleStrategy idleStrategy) {
    this.publisherToKafkaPublisherQueue = Context.getPublisherToKafkaPublisherQueue();
    this.idleStrategy = idleStrategy;
  }

  public void run() {
    process();
  }

  private void process() {
    while (true) {
      try {
        final int count = publisherToKafkaPublisherQueue.drainTo(list, 512);
        for (int i = 0; i < count; i++) {
          final byte[] bytes = list.get(i);
          if (bytes != null && bytes.length > 0) {
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(LOG_FMT_2, "process bytes=", bytes);
            }
            Context.getKafkaPublisher().send(bytes);
          }
        }

        list.clear();
        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

}
