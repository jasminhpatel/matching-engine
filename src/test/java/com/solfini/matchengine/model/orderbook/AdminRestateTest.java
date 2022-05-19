package com.solfini.matchengine.model.orderbook;

import java.io.IOException;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Test;

import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.sbe.encoder.Side;

import static com.solfini.sbe.encoder.TimeInForce.DAY;
import static com.solfini.sbe.encoder.TimeInForce.GOOD_TILL_CANCEL;

/**
 *
 * @author Chris Mack
 *
 */
public class AdminRestateTest extends ModelTest {

  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "8");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    // onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
   * Setup security pair with PATCH to use NO_PREORDER_CHECK. Add DAY and GTC order bids and asks. Add crossing bid, creating a partial
   * fill. Assert restatement orders with partially filled order.
   */
  @Test
  public void testTradeStateAdminRestate() {
    configure();

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDC", 2, 2));
    expectMessage("securityId=" + USDT + ", symbol=USDC");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    expectMessage("securityId=" + BTC + ", symbol=BTC");

    final SecurityDefinitionAdminMessage securityDefinitionAdminMessage =
        createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT", BTC, USDT, 2, 2);
    securityDefinitionAdminMessage.setPreOrderCheckStrategy(NO_PREORDER_CHECK);
    InstrumentCache.updateSecurityDefinition(securityDefinitionAdminMessage);
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT, updateType=PATCH");

    final User user = createUser(19);
    expectMessage("userId=19");

    int orderId = 0;
    final Order buyOrder1 = createOrder(orderId++, user, BTC_USDT_F, 9_999, 10_000, Side.BUY, DAY);
    final Order buyOrder2 = createOrder(orderId++, user, BTC_USDT_F, 9_999, 10_000, Side.BUY, GOOD_TILL_CANCEL);
    final Order buyOrder3 = createOrder(orderId++, user, BTC_USDT_F, 9_999, 10_000, Side.BUY, DAY);
    final Order buyOrder4 = createOrder(orderId++, user, BTC_USDT_F, 10_000, 1_000, Side.BUY, DAY);
    final Order sellOrder1 = createOrder(orderId++, user, BTC_USDT_F, 10_000, 10_000, Side.SELL, DAY);
    final Order sellOrder2 = createOrder(orderId++, user, BTC_USDT_F, 10_000, 10_000, Side.SELL, GOOD_TILL_CANCEL);
    final Order sellOrder3 = createOrder(orderId++, user, BTC_USDT_F, 10_000, 10_000, Side.SELL, DAY);

    final InstrumentPair instrumentPair = InstrumentCache.getPair(BTC_USDT_F);
    instrumentPair.getOrderBook().addOrder(sellOrder1);
    instrumentPair.getOrderBook().addOrder(sellOrder2);
    instrumentPair.getOrderBook().addOrder(sellOrder3);
    instrumentPair.getOrderBook().addOrder(buyOrder1);
    instrumentPair.getOrderBook().addOrder(buyOrder2);
    instrumentPair.getOrderBook().addOrder(buyOrder3);
    instrumentPair.getOrderBook().addOrder(buyOrder4);

    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=NEW, ordStatus=NEW");
    expectMessage("execType=TRADE, ordStatus=FILLED");
    expectMessage("execType=TRADE, ordStatus=PARTIALLY_FILLED");

    instrumentPair.changeState(MarketStatus.RESTATE, 0, buyOrder4);
    expectMessage("execType=RESTATED, ordType=PREVIOUSLY_INDICATED");



    expectMessage("execType=RESTATED, ordStatus=NEW");
    expectMessage("execType=RESTATED, ordStatus=NEW");
    expectMessage("execType=RESTATED, ordStatus=NEW");
    expectMessage("execType=RESTATED, cumQty=10, leavesQty=90, symbol=BTC/USD, ordStatus=PARTIALLY_FILLED");
    expectMessage("execRestatementReason=OTHER, ordStatus=NEW");
    expectMessage("execRestatementReason=OTHER, ordStatus=NEW");

    assertMessages();
  }
}
