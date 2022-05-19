package com.solfini.matchengine.message;

import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.common.Constants;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.ReusableLog;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.OrderObjectPool;
import com.solfini.user.User;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

// Thread.sleep isn't guarenteed to sync memory, yet it does
// https://stackoverflow.com/questions/42417636/what-is-the-relationship-between-thread-sleep-and-happens-
// https://docs.oracle.com/javase/specs/jls/se7/html/jls-17.html#jls-17.4.5
public class StringPoolTest implements Constants {
  private static final String CLASS_NAME = StringPoolTest.class.getName();

  private static final Logger LOGGER = LogManager.getLogger(StringPoolTest.class);

  final InstrumentPair instrument =
      new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 100, 200, 260, 0);
  final User user = new User(18);
  final ExecutionReportMessage executionReport = makeExecutionReport(user, instrument);
  final ManyToOneConcurrentArrayQueueCustom<ReusableLog> loggingQueue = LoggingThread.getLoggingQueue();

  private ExecutionReportMessage makeExecutionReport(final User user, final InstrumentPair instrument) {
    final Order order = makeOrder(user, instrument);
    final ExecutionReportMessage executionReport = ExecutionReportMessage.createAckNewOrderExecutionReport(order, instrument);
    OrderObjectPool.returnObject(order);

    return executionReport;
  }

  protected Order makeOrder(final User user, final InstrumentPair instrument) {
    final Random random = new Random();
    long orderId = 1;
    try {
      final int orderType = random.nextInt(2);
      final int side = orderType == Constants.BUY_LIMIT ? 1 : 2;
      final long quantity = 1 + random.nextInt(100_000);

      long price = 0;
      if (orderType == Constants.BUY_LIMIT) {
        price = 1 + random.nextInt(1_050_000);
      } else {
        price = 1_000_000 + random.nextInt(1_000_000);
      }

      final Order order = OrderObjectPool.get();
      order.setSenderCompId("test");
      order.setAccount(18);
      order.setUser(user);
      order.setOrderId(orderId);
      order.setOrderPriority(1);
      order.setType(orderType);
      order.setPriceInt((int) price);
      order.setQuantityLong(quantity);
      order.setQuantityOrigLong(quantity);
      order.setSecurityId(instrument.getId());
      order.setClOrdId("clientOrder0");

      final DecimalFloat priceDecimal = new DecimalFloat(price, instrument.getPriceScale());
      final DecimalFloat quantityDecimal = new DecimalFloat(quantity, instrument.getQuantityScale());
      final OrdType ordType = OrdType.LIMIT; // TODO: verify
      final Side sideType = side == 1 ? Side.SELL : Side.BUY; // TODO: verify
      order.setPrice(price, instrument.getPriceScale());
      order.setQty(quantity, instrument.getQuantityScale());
      order.setOrdType(ordType);
      order.setSide(sideType);

      return order;
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    return null;
  }

  @Test
  public void logTest() {

    for (int i = 0; i < 10_000; i++) {
      final String s = executionReport.toString();
    }

    long t0 = System.currentTimeMillis();

    for (int i = 0; i < 100_000; i++) {
      final String s = executionReport.toString();
    }

    System.out.println("logTest , t=" + (System.currentTimeMillis() - t0));
  }

  @Test
  public void logTest2() {

    for (int i = 0; i < 10_000; i++) {
      final String s = executionReport.toString();
      LOGGER.info(s);
    }

    long t0 = System.currentTimeMillis();

    for (int i = 0; i < 100_000; i++) {
      final String s = executionReport.toString();
      LOGGER.info(s);
    }

    System.out.println("logTest2 , t=" + (System.currentTimeMillis() - t0));
  }

  @Test
  public void logTest3() {

    for (int i = 0; i < 10_000; i++) {
      loggingQueue.addGuaranteed(ReusableLog.get(POOL_128, CLASS_NAME, ReusableLog.DEBUG).append(executionReport));
    }

    long t0 = System.currentTimeMillis();

    for (int i = 0; i < 100_000; i++) {
      loggingQueue.addGuaranteed(ReusableLog.get(POOL_128, CLASS_NAME, ReusableLog.DEBUG).append(executionReport));
    }

    System.out.println("logTest3 , t=" + (System.currentTimeMillis() - t0));
  }

  @Test
  public void logTest4() {
    for (int i = 0; i < 10_000; i++) {
      loggingQueue.addGuaranteed(ReusableLog.get(POOL_128, CLASS_NAME, ReusableLog.DEBUG).append(executionReport));
    }

    long t0 = System.currentTimeMillis();

    for (int i = 0; i < 100_000; i++) {
      loggingQueue.addGuaranteed(ReusableLog.get(POOL_128, CLASS_NAME, ReusableLog.DEBUG).append(executionReport));
    }

    System.out.println("logTest4 , t=" + (System.currentTimeMillis() - t0));
  }

  public static void main(String args[]) throws Exception {
    StringPoolTest test = new StringPoolTest();
    test.logTest();
    test.logTest2();
    test.logTest3();
    test.logTest4();
    test.logTest();
    test.logTest2();
    test.logTest3();
    test.logTest4();
    test.logTest();
    test.logTest2();
    test.logTest3();
    test.logTest4();
    test.logTest();
    test.logTest2();
    test.logTest3();
    test.logTest4();
  }
}
