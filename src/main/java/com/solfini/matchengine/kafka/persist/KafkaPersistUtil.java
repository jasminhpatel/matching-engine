package com.solfini.matchengine.kafka.persist;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.StringUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

public class KafkaPersistUtil implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaPersistUtil.class);

  private final ChronicleQueue queue;
  private final ByteBuffer ipcBuffer = ByteBuffer.allocate(32768);
  private final ExcerptAppender appender;
  private final String dir;
  private final long snapId;
  private long messageCount;

  public KafkaPersistUtil(final long snapId, final long replayId) {
    this.snapId = snapId;
    if (!Context.getChronicleEngineSnapCacheDirectory().isEmpty()) {
      dir = Context.getChronicleEngineSnapCacheDirectory() + "/" + snapId + "_replay" + replayId;
    } else {
      dir = Context.getChronicleEngineSnapQueueDirectory() + "/" + snapId + "_replay" + replayId;
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "KafkaPersistUtil started: snapId=", snapId, DIR_EQ, dir);
    }

    try {
      Files.createDirectories(Paths.get(dir));
    } catch (IOException e) {
      LOGGER.error(ERROR_LOG, e);
    }

    queue = SingleChronicleQueueBuilder.single(dir).blockSize(1048576).rollCycle(RollCycles.DAILY).build();
    appender = queue.acquireAppender();
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


  // persist with 25 byte header, 8+17
  public void persist(final long seqNum, final long sendTime, final long recordOffset, final byte messageType,
      final byte[] bytesWithKafkaOffset) {

    // recordOffset
    long tempRecordOffset = recordOffset;
    for (int i = 7; i >= 0; i--) {
      bytesWithKafkaOffset[i] = (byte) (tempRecordOffset & 0xFF);
      tempRecordOffset >>= 8;
    }

    // seqNum
    long tempSeqNum = seqNum;
    for (int i = 15; i >= 8; i--) {
      bytesWithKafkaOffset[i] = (byte) (tempSeqNum & 0xFF);
      tempSeqNum >>= 8;
    }

    // time
    long tempTime = sendTime;
    for (int i = 23; i >= 16; i--) {
      bytesWithKafkaOffset[i] = (byte) (tempTime & 0xFF);
      tempTime >>= 8;
    }

    // messageType
    bytesWithKafkaOffset[24] = messageType;

    ipcBuffer.clear();
    ipcBuffer.put(bytesWithKafkaOffset);
    Bytes<ByteBuffer> bbb = Bytes.wrapForWrite(ipcBuffer);
    messageCount++;

    LOGGER.debug(LOG_FMT_10, ">>>persist messageCount=", messageCount, BYTES_EQ, StringUtil.fixToString(bytesWithKafkaOffset),
        SEQNUMBYTES_EQ, tempSeqNum, SEQ_EQ, seqNum, MESSAGECOUNT_EQ, messageCount, KAFKA_OFFSET_EQ, recordOffset);

    appender.writeBytes(bbb);

    LOGGER.debug(LOG_FMT_10, "<<<persist messageCount=", messageCount, BYTES_EQ, StringUtil.fixToString(bytesWithKafkaOffset),
        SEQNUMBYTES_EQ, tempSeqNum, SEQ_EQ, seqNum, MESSAGECOUNT_EQ, messageCount, KAFKA_OFFSET_EQ, recordOffset);

  }
}
