package com.solfini.matchengine.publisher;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.queue.rollcycles.LegacyRollCycles;


public class SnapUtil implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(SnapUtil.class);
  public static final byte NORMAL_API = (byte) 2;
  public static final byte ADMIN_API = (byte) 4;

  private final ChronicleQueue queue;
  private final ByteBuffer ipcBuffer = ByteBuffer.allocate(32768);
  private final ExcerptAppender appender;
  private final String dir;
  private final long snapId;
  private long messageCount;

  public SnapUtil(final long snapId) {
    this.snapId = snapId;
    if (!Context.getChronicleEngineSnapCacheDirectory().isEmpty()) {
      dir = Context.getChronicleEngineSnapCacheDirectory() + "/" + snapId;
    } else {
      dir = Context.getChronicleEngineSnapQueueDirectory() + "/" + snapId;
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "Snapshot started: snapId=", snapId, DIR_EQ, dir);
    }

    try {
      Files.createDirectories(Paths.get(dir));
    } catch (IOException e) {
      LOGGER.error(ERROR_LOG, e);
    }

    queue = SingleChronicleQueueBuilder.single(dir).blockSize(1048576).rollCycle(LegacyRollCycles.DAILY).build();
    appender = queue.createAppender();
    appender.singleThreadedCheckDisabled(true);
  }

  public final long getSnapId() {
    return snapId;
  }

  public void close() {
    queue.close();

    String target = dir;
    if (!Context.getChronicleEngineSnapCacheDirectory().isEmpty()) {
      try {
        target = Context.getChronicleEngineSnapQueueDirectory() + "/" + snapId;
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_4, "Snapshot move: snapId=", snapId, DIR_EQ, dir, TARGET_EQ, target);
        }

        try {
          Path dirPath = Files.createDirectories(Paths.get(target));

          // check that directory exists
          if (!Files.exists(dirPath)) {
            for (int i = 0; i < 100; i++) {
              final Path path = Paths.get(target);
              if (Files.exists(path))
                break;

              Thread.sleep(1);
            }
          }
        } catch (Exception e) {
          LOGGER.error("Error writing snap dir=" + target, e);
        }

        File source = new File(dir);
        for (File file : source.listFiles()) {
          if (file.isFile()) {
            Files.copy(Paths.get(dir).resolve(file.getName()), Paths.get(target).resolve(file.getName()));
            Files.delete(Paths.get(dir).resolve(file.getName()));
          }
        }
      } catch (IOException e) {
        LOGGER.error(ERROR_LOG, e);
        return;
      }
    }



    try {
      final Path path = Paths.get(target + "/done");
      if (!Files.exists(path))
        Files.createFile(path);
    } catch (Exception e) {
      LOGGER.error("Error writing snap, path=" + target + "/done", e);
      return;
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "Snapshot completed: snapId=", snapId, DIR_EQ, target);
    }
  }

  public void snap(final byte[] bytesWithKafkaOffset, final byte messageType, final Message message) {
    // seqNum
    long tempSeqNum = messageCount;
    for (int i = 7; i >= 0; i--) {
      bytesWithKafkaOffset[i] = (byte) (tempSeqNum & 0xFF);
      tempSeqNum >>= 8;
    }

    // time
    long now = TimeUtil.getTime();
    for (int i = 15; i >= 8; i--) {
      bytesWithKafkaOffset[i] = (byte) (now & 0xFF);
      now >>= 8;
    }

    // messageType
    bytesWithKafkaOffset[16] = messageType;

    ipcBuffer.clear();
    ipcBuffer.put(bytesWithKafkaOffset);
    Bytes<ByteBuffer> bbb = Bytes.wrapForWrite(ipcBuffer);
    messageCount++;

    if (LOGGER.isDebugEnabled()) {
      if (message.getUser() == null)
        LOGGER.debug(LOG_FMT_12, ">>> 18publishnull40 snap messageCount=", messageCount, MESSAGE_EQ, message, BYTES_EQ,
            StringUtil.fixToString(bytesWithKafkaOffset), SEQNUMBYTES_EQ, tempSeqNum, SEQ_EQ, message.getSequenceNumber(), MESSAGECOUNT_EQ,
            messageCount);
      else if (message.getUser().getId() == 15)
        LOGGER.debug(LOG_FMT_12, ">>> 18publish40 snap messageCount=", messageCount, MESSAGE_EQ, message, BYTES_EQ,
            StringUtil.fixToString(bytesWithKafkaOffset), SEQNUMBYTES_EQ, tempSeqNum, SEQ_EQ, message.getSequenceNumber(), MESSAGECOUNT_EQ,
            messageCount);
      LOGGER.debug(LOG_FMT_12, ">>>snap messageCount=", messageCount, MESSAGE_EQ, message, BYTES_EQ,
          StringUtil.fixToString(bytesWithKafkaOffset), SEQNUMBYTES_EQ, tempSeqNum, SEQ_EQ, message.getSequenceNumber(), MESSAGECOUNT_EQ,
          messageCount);
    }

    appender.writeBytes(bbb);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "<<<snap message=", message, MESSAGECOUNT_EQ, messageCount);
    }
  }
}
