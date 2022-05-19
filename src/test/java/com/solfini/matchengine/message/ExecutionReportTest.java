package com.solfini.matchengine.message;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.drmode.DecoderThreadCache;
import com.solfini.matchengine.drmode.KafkaDRFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.*;
import com.solfini.user.User;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Properties;

public class ExecutionReportTest extends MessageTest {

  private static final int KAFKA_OFFSET = 17;
  private static final short HEADER_LENGTH = 2;

  @BeforeClass public static void before() {
    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "1000000");
      properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "1000000");
      properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
      properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
      properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
      PropertyReader.initialize(null, properties);

      synchronized (properties) {
        createInstruments();
      }

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    DecoderThreadCache.start();
  }

  @AfterClass public static void after() {
    DecoderThreadCache.shutdown();
  }

  @Ignore
  @Test public void measureExecutionReportDecoderPerformance() {
    InstrumentPair pair = InstrumentCache.getPair(3);

    final User user = new User(18);
    final ExecutionReportMessage executionReport = makeExecutionReport(user, pair);
    final byte[] data = encode(executionReport);

    KafkaDRFixListener listener = null;
    try {
      listener = new KafkaDRFixListener(KafkaDRFixListener.LOAD_STRATEGY_NONE);
      listener.onMessage(0, 0, 0, KafkaPublisher.NORMAL_API, data);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
      return;
    }

    final int RUNS = 1_000_000;
    final long start = System.nanoTime();
    for (int i = 0; i < RUNS; ++i) {
      listener.onMessage(i + 1, 0, 0, KafkaPublisher.NORMAL_API, data);
    }

    final long elapsed = (System.nanoTime() - start) / RUNS;
    System.out.println("Done in " + format(elapsed) + " ns (" + format((1_000_000_000.0 * RUNS) / elapsed) + " msg/s)");

    final ManyToOneConcurrentArrayQueueCustom<Message> queue = Context.getReceiverToMatcherQueue();
    int count = -1;
    while (count < RUNS) {
      try {
        Message message = queue.remove();
        ++count;
        Assert.assertNotNull(message);
        Assert.assertTrue(message instanceof Order);
      } catch (Exception e) {
      }
    }

    Assert.assertTrue(queue.isEmpty());
  }

  // @Test public void measureExecutionReportDecoderResetPerformance() {
  //   ExecutionReportDecoder decoder = new ExecutionReportDecoder();

  //   final int RUNS = 1_000_000;
  //   final long start = System.nanoTime();
  //   for (int i = 0; i < RUNS; ++i) {
  //     //      decoder.reset();
  //   }
  //   Assert.fail(); // No decoder reset

  //   final long elapsed = (System.nanoTime() - start) / RUNS;
  //   System.out.println(
  //     "Done in " + format(elapsed) + " ns (" + format((1_000_000_000.0 * RUNS) / elapsed) + " calls/s, " + format(elapsed / RUNS)
  //       + " ns/call)");
  // }

  private ExecutionReportMessage makeExecutionReport(final User user, final InstrumentPair instrument) {
    final Order order = makeOrder(user, instrument);
    final ExecutionReportMessage executionReport = ExecutionReportMessage.createAckNewOrderExecutionReport(order, instrument);
    OrderObjectPool.returnObject(order);

    return executionReport;
  }

  private byte[] encode(final ExecutionReportMessage executionReport) {

    MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    short encodedLength = HEADER_LENGTH;
    final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer messageUnsafeBuffer = new UnsafeBuffer(messageBuffer);
    final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();

    executionReportEncoder.wrapAndApplyHeader(messageUnsafeBuffer, encodedLength, messageHeaderEncoder);
    encodedLength += messageHeaderEncoder.encodedLength();

    executionReportEncoder.clOrdID(executionReport.getClOrdId());
    executionReportEncoder.securityId(1_000_000_000 + executionReport.getSecurityId());
    executionReportEncoder.symbol(executionReport.getSymbol());
    executionReportEncoder.transactTime(executionReport.getTimestamp());
    executionReportEncoder.side(executionReport.getSide());
    executionReportEncoder.ordType(executionReport.getOrdType());

    // if (executionReport.getAccount() != 0) {
    //   long accountNum = 1_000_000_000 + executionReport.getAccount();

    //   executionReportEncoder.account(accountNum);
    // }
    Assert.fail(); //Execution report message has no accountId
    if (executionReport.getTimeInForce() != null) {
      executionReportEncoder.timeInForce(executionReport.getTimeInForce());
    }
    if (executionReport.getExpireTime() != 0) {
      executionReportEncoder.expireTime(executionReport.getExpireTime());
    }

    executionReportEncoder.orderId(1_000_000_000_000L + executionReport.getOrderId());
    executionReportEncoder.secondaryOrderId(1_000_000_000_000L + executionReport.getSecondaryOrderId());
    executionReportEncoder.execId(1_000_000_000_000L + executionReport.getExecId());
    executionReportEncoder.secondaryExecId(1_000_000_000_000L + executionReport.getSecondaryExecId());
    executionReportEncoder.leavesQty(executionReport.getLeavesQty());
    executionReportEncoder.leavesQtyScale(executionReport.getLeavesQtyScale());
    executionReportEncoder.cumQty(executionReport.getCumQty());
    executionReportEncoder.cumQtyScale(executionReport.getCumQtyScale());
    executionReportEncoder.lastQty(executionReport.getLastQty());
    executionReportEncoder.lastQtyScale(executionReport.getLastQtyScale());
    executionReportEncoder.avgPx(0);
    executionReportEncoder.price(executionReport.getPrice());
    executionReportEncoder.priceScale(executionReport.getPriceScale());

    if (executionReport.getPrice2() > 0)
      executionReportEncoder.price2(executionReport.getPrice2());
    executionReportEncoder.price2(executionReport.getPrice2Scale());

    if (executionReport.getStopPx() != 0) {
      executionReportEncoder.stopPx(executionReport.getStopPx());
      executionReportEncoder.stopPxScale((short) executionReport.getStopPxScale());
    }

    executionReportEncoder.execType(executionReport.getExecType());
    executionReportEncoder.ordStatus(executionReport.getOrdStatus());

    executionReportEncoder.orderQty(executionReport.getOrderQty());
    executionReportEncoder.orderQtyScale((short) executionReport.getOrderQtyScale());

    final Instrument feeInstrument = InstrumentCache.get(executionReport.getFeePositionId());
    if (feeInstrument != null) {
      executionReportEncoder.feePositionQuantityChange(executionReport.getFeePositionQuantityChange());
      executionReportEncoder.feePositionQuantityChangeScale(feeInstrument.getQuantityScale());
      if (executionReport.isPaidToInsurance())
        executionReportEncoder.isPaidToInsurance(BooleanType.TRUE);
      else
        executionReportEncoder.isPaidToInsurance(BooleanType.FALSE);
    }

    if (executionReport.getOrdType() == OrdType.STOP_LIMIT) {
      executionReportEncoder.stopPx(executionReport.getStopPx());
      executionReportEncoder.stopPxScale(executionReport.getStopPxScale());
    }


    if (ExecType.TRADE.equals(executionReport.getExecType())) {
      executionReportEncoder.lastPx(executionReport.getLastPx());
      executionReportEncoder.lastPxScale(executionReport.getLastPxScale());
      executionReportEncoder.avgPx(executionReport.getAvgPx());
      executionReportEncoder.avgPxScale(executionReport.getAvgPxScale());
      if (executionReport.getAggressorSide() != null)
        executionReportEncoder.aggresorSide(executionReport.getAggressorSide());

      executionReportEncoder.settlePositionId(executionReport.getSettlePositionId());
      Instrument settleInstrument = InstrumentCache.get(executionReport.getSettlePositionId());
      executionReportEncoder.settlePositionQuantityChange(executionReport.getSettlePositionQuantityChange());
      executionReportEncoder.settlePositionQuantityChangeScale(settleInstrument.getQuantityScale());
      executionReportEncoder.counterPartyId(executionReport.getCounterpartyId());
    }

    encodedLength += executionReportEncoder.encodedLength();
    messageBuffer.limit(encodedLength);
    messageUnsafeBuffer.putShort(0, encodedLength);
    return StringUtil.bufferToArrayBulk(messageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }
}
