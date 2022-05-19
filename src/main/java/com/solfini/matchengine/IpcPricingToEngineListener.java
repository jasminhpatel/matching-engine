package com.solfini.matchengine;

import java.nio.ByteBuffer;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.util.StringUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

/**
 *
 * @author Chris Mack
 *
 */
public class IpcPricingToEngineListener implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(IpcPricingToEngineListener.class);

  private static ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
  private ChronicleQueue queue;
  private ExcerptTailer tailer;
  private static final int TIMEOUT = 10_000;

  private InstrumentPair BTC_USDC = null;
  private InstrumentPair BTC_USDC_F = null;
  private InstrumentPair ETH_USDC = null;
  private InstrumentPair ETH_USDC_F = null;

  private long lastSequenceNumber = 0;
  private long lastIpcIndex = 0;

  private void connect() {
    try {
      if (queue != null)
        queue.close();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    queue = SingleChronicleQueueBuilder.single(Context.getChroniclePricingOutputQueueDirectory()).blockSize(1048576)
        .rollCycle(RollCycles.MINUTELY).build();
    tailer = queue.createTailer().toEnd(); // skip to end, don't read old messages
    BTC_USDC = InstrumentCache.getPairBySymbol("BTC/USDC");
    BTC_USDC_F = InstrumentCache.getPairBySymbol("BTC/USDC[F]");
    ETH_USDC = InstrumentCache.getPairBySymbol("ETH/USDC");
    ETH_USDC_F = InstrumentCache.getPairBySymbol("ETH/USDC[F]");
  }

  public IpcPricingToEngineListener(final IdleStrategy idleStrategy) {
    connect();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, IPCPRICINGTOENGINELISTENER_LOADED_LASTSEQUENCENUMBER, lastSequenceNumber, LASTIPCINDEX_EQ, lastIpcIndex);
    }
  }

  @Override
  public void run() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, ">>> IpcPricingToEngineListener run");
    }

    try {
      long lastReadTime = System.currentTimeMillis();
      long counter = 0;
      Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);
      boolean read;
      while (true) {
        try {
          final long ipcIndex = tailer.index();
          bytes.clear();
          read = tailer.readBytes(bytes);

          if (read) {
            lastReadTime = System.currentTimeMillis();
            final byte[] data = bytes.underlyingObject().array();
            final int len = (int) bytes.readRemaining();

            final long seqNum = bytesToLong(data, 0);
            final int pairId = (int) bytesToLong(data, 8);
            final double price = bytesToDouble(data, 16);

            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_14, ">>> ipc pricing0 read=", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data), SEQNUM_EQ,
                  seqNum, IPCINDEX_EQ, ipcIndex, PAIRID_EQ, pairId, PRICE_EQ, price);
            }

            // skip already read messages
            if (lastSequenceNumber >= seqNum) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_FMT_14, ">>> SKIPPING ipc read=", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data), SEQNUM_EQ,
                    seqNum, IPCINDEX_EQ, ipcIndex, PAIRID_EQ, pairId, PRICE_EQ, price);
              }
              continue;
            }
            if (lastSequenceNumber == 0)
              lastSequenceNumber = seqNum;
            else if (seqNum != lastSequenceNumber + 1) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_FMT_12, ">>> Unexpected seq. ipc read=", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data),
                    SEQNUM_EQ, seqNum, LASTSEQUENCENUMBER_EQ, lastSequenceNumber, IPCINDEX_EQ, ipcIndex);
              }
              continue;
            }
            lastSequenceNumber++;
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_14, ">>> ipc pricing read=", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data), SEQNUM_EQ, seqNum,
                  IPCINDEX_EQ, ipcIndex, PAIRID_EQ, pairId, PRICE_EQ, price);
            }

            final InstrumentPair pair = InstrumentCache.getPair(pairId);
            if (pair != null)
              pair.setIndexFeedUsdMark(price);
            else {
              final Instrument instrument = InstrumentCache.get(pairId);
              instrument.setIndexFeedUsdMark(price);

              if ("BTC".equals(instrument.getSymbol())) {
                if (BTC_USDC != null)
                  BTC_USDC.setIndexFeedUsdMark(price);
                if (BTC_USDC_F != null)
                  BTC_USDC_F.setIndexFeedUsdMark(price);
              } else if ("ETH".equals(instrument.getSymbol())) {
                if (ETH_USDC != null)
                  ETH_USDC.setIndexFeedUsdMark(price);
                if (ETH_USDC_F != null)
                  ETH_USDC_F.setIndexFeedUsdMark(price);
              }
            }

            // set LIQUIDATON_MODE
            if ((!MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE())
                && (!SnapLoader.isSnapLoaderMode() && BTC_USDC != null && BTC_USDC.getIndexFeedUsdMark() > 0)) {
              MarginPreOrderCheckAndSettle.setLIQUIDATON_MODE(true);
            }
          } else {
            // reconnect queue on timeout
            if (System.currentTimeMillis() - lastReadTime > TIMEOUT) {
              connect();
              lastReadTime = System.currentTimeMillis();
            }
          }
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }

        if (counter % 10 == 0)
          Thread.sleep(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final byte[] longToBytes(final long x) {
    longBuffer.putLong(0, x);
    return longBuffer.array();
  }

  public final long bytesToLong(final byte[] bytes, final int offset) {
    longBuffer.clear();
    for (int i = offset; i < offset + 8; i++)
      longBuffer.put(bytes[i]);
    longBuffer.flip();// need flip
    return longBuffer.getLong();
  }

  public final double bytesToDouble(final byte[] bytes, final int offset) {
    longBuffer.clear();
    for (int i = offset; i < offset + 8; i++)
      longBuffer.put(bytes[i]);
    longBuffer.flip();// need flip
    return longBuffer.getDouble();
  }

  public final void setBytes(final byte[] target, final byte[] source, final int offset) {
    for (int i = 0; i < source.length; i++) {
      target[i + offset] = source[i];
    }
  }

}
