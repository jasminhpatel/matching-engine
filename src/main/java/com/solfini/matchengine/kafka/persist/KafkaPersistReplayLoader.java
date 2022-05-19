package com.solfini.matchengine.kafka.persist;

import java.io.File;
import java.nio.ByteBuffer;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.util.StringUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

public class KafkaPersistReplayLoader implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaPersistReplayLoader.class);

  private final KafkaListener kafkaListener;
  private final ChronicleQueue queue;
  private final ExcerptTailer tailer;
  private long lastSequenceNumber = 0;
  private long lastKafkaInputOffset = -1;

  public KafkaPersistReplayLoader(final KafkaListener kafkaListener) {
    this.kafkaListener = kafkaListener;
    String dir = Context.getReplayFromFileLocation();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, ">>> ReplayLoader dir=", dir);
    }
    try {

      for (int i = 0; i < 1_000_000; i++) {
        File folder = new File(dir + "/done");
        if (folder.exists()) {
          break;
        }

        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(LOG_FMT_4, ">>> ReplayLoader waiting dir=", dir);
        }
        Thread.sleep(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    queue = SingleChronicleQueueBuilder.single(dir).blockSize(1048576).rollCycle(RollCycles.DAILY).build();
    tailer = queue.createTailer(); // read queue from beginning
    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(LOG_FMT_4, "<<< ReplayLoader dir=", dir);
    }
  }

  public void close() {
    queue.close();
  }



  public long replay() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "Loading snapshot: dir=", Context.getReplayFromFileLocation());
    }

    try {
      Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);
      boolean read;
      while (true) {
        try {
          bytes.clear();
          read = tailer.readBytes(bytes);

          if (read) {
            byte[] data = bytes.underlyingObject().array();
            int len = (int) bytes.readRemaining();

            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_6, "read+", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data, len));
            }

            // recordOffset
            long recordOffset = 0;
            for (int i = 0; i < 8; i++) {
              recordOffset <<= 8;
              recordOffset |= (data[i] & 0xFF);
            }
            // seqNum
            long seqNum = 0;
            for (int i = 8; i < 16; i++) {
              seqNum <<= 8;
              seqNum |= (data[i] & 0xFF);
            }
            // sendTime
            long sendTime = 0;
            for (int i = 16; i < 24; i++) {
              sendTime <<= 8;
              sendTime |= (data[i] & 0xFF);
            }
            // messageType
            byte messageType = data[24];

            // copy the data back to 17 byte buffer
            byte[] dest = new byte[len-8];
            System.arraycopy(data, 8, dest, 0, dest.length);


            kafkaListener.onMessage(seqNum, sendTime, recordOffset, messageType, dest);
          }
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }

      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return -1;
  }


  public final long getLastKafkaInputOffset() {
    return lastKafkaInputOffset;
  }

  public final long getLastSequenceNumber() {
    return lastSequenceNumber;
  }

}
