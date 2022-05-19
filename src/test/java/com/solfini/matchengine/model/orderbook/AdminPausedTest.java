package com.solfini.matchengine.model.orderbook;

import java.io.IOException;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.common.Constants;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
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
public class AdminPausedTest extends ModelTest {

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
   * Setup security pair with PATCH to use NO_PREORDER_CHECK. Add DAY and GTC order. PAUSE. Assert DAY order is cancelled, GTC order
   * remains. Assert book is paused. Attempt to add new order and assert business reject. Cancel GTC order while paused, and assert.
   */
  @Test
  public void testTradeStateAdminPaused() {
    configure();

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    expectMessage("securityId=" + USDT + ", symbol=USDT");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(ETH, UpdateType.PUT, "ETH", 4, 3));
    expectMessage("securityId=" + ETH + ", symbol=ETH");

    final SecurityDefinitionAdminMessage securityDefinitionAdminMessage =
        createInstrumentPairDefinition(ETH_USDT_F, UpdateType.PATCH, "ETH/USDT", ETH, USDT, 2, 2);
    securityDefinitionAdminMessage.setPreOrderCheckStrategy(NO_PREORDER_CHECK);
    InstrumentCache.updateSecurityDefinition(securityDefinitionAdminMessage);
    expectMessage("securityId=" + ETH_USDT_F + ", symbol=ETH/USDT, updateType=PATCH");

    final User user = createUser(18);
    expectMessage("userId=18");

    final Order sellOrder1 = createOrder(1, user, ETH_USDT_F, 10_000, 10_000, Side.SELL, DAY);
    final Order sellOrder2 = createOrder(2, user, ETH_USDT_F, 10_000, 10_000, Side.SELL, GOOD_TILL_CANCEL);
    final Order sellOrder3 = createOrder(3, user, ETH_USDT_F, 10_000, 10_000, Side.SELL, DAY);

    final InstrumentPair instrumentPair = InstrumentCache.getPair(ETH_USDT_F);
    instrumentPair.getOrderBook().addOrder(sellOrder1);
    expectMessage("execType=NEW, symbol=ETH/USDT, ordStatus=NEW");
    instrumentPair.getOrderBook().addOrder(sellOrder2);
    expectMessage("execType=NEW, symbol=ETH/USDT, ordStatus=NEW");

    instrumentPair.changeState(MarketStatus.PAUSE, 0, sellOrder2);
    Assert.assertEquals(MarketStatus.PAUSE, (instrumentPair.getOrderBook()).getMarketStatus());

    instrumentPair.getOrderBook().addOrder(sellOrder3);
    //expectMessage("execType=PENDING_CANCEL, symbol=ETH/USDT, ordStatus=PENDING_CANCEL");
    //expectMessage("execType=CANCELED, symbol=ETH/USDT, ordStatus=CANCELED");
    expectMessage("businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=" + Constants.MARKET_IS_PAUSED_OR_CLOSED);

    final CancelOrder cancelOrder = createCancelOrder(4, 2, user, ETH_USDT_F, 10_000, 10_000, Side.SELL, GOOD_TILL_CANCEL);
    instrumentPair.getOrderBook().cancelOrder(cancelOrder);
    expectMessage("execType=PENDING_CANCEL, symbol=ETH/USDT, ordStatus=PENDING_CANCEL");
    expectMessage("execType=CANCELED, symbol=ETH/USDT, ordStatus=CANCELED");

    assertMessages();
  }
}
