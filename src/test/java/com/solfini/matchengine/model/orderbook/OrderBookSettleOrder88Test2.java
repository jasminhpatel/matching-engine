package com.solfini.matchengine.model.orderbook;

import java.io.IOException;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.sbe.encoder.Side;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookSettleOrder88Test2 extends OrderBookTest {

  @Override
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
    properties.setProperty("DEFAULT_SETTLE_INSTRUMENT_PRICE_SCALE_MULT", "100000000");

    onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void createInstruments() {
    // Asset scale is 8.
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 8, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 8, 8));
    // Symbol price & quantity scale both 2
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookWrapper(pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(5000);
  }

  @Override
  protected void createUsers() {
    user = createUser(18);
    user.addPosition(pair.getQuotedId(), 10000_00000000L);
    expectMessage("userId=18");
    expectOutput("userId=18");

    user2 = createUser(19);
    user2.addPosition(pair.getQuotedId(), 20000_00000000L);
    expectMessage("userId=19");
    expectOutput("userId=19");

    user3 = createUser(20);
    user3.addPosition(pair.getQuotedId(), 30000_00000000L);
    expectMessage("userId=20");
    expectOutput("userId=20");
  }

  @Test
  public void checkBalanceUpdateAfterSettlement() {
    Instrument instrumentUSDT = InstrumentCache.get(USDT);
    Instrument instrumentBTC = InstrumentCache.get(BTC);



    // Initial USDT balance = 10_000
    Assert.assertEquals(10000_00000000L, user.getPositionArr()[pair.getQuotedId()].getQuantity());

    orderBook.addOrder(createOrder(1, user, pair.getId(), 5000_25, 1_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=500025, orderQty=1, leavesQty=1, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 5000_25, 1_00, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=500025, orderQty=1, leavesQty=1, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=500025, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=500025, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    // Bought BTC/USDT 1 at 5000, position = 1
    Assert.assertEquals(1_00, user.getPositionArr()[pair.getId()].getQuantity());
    Assert.assertEquals(10000_00000000L, user.getPositionArr()[pair.getQuotedId()].getQuantity());

    orderBook.addOrder(createOrder(3, user3, pair.getId(), 6000_00, 1_00, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=6000, orderQty=1, leavesQty=1, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 6000_00, 1_00, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=600000, orderQty=1, leavesQty=1, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=600000, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=600000, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();


    Assert.assertEquals(0, user.getPositionArr()[pair.getId()].getQuantity());
    // Buy at 5000.25
    // Sold BTC/USDT 1 at 6000, position = 0, profit is +999.75
    // Final USDT balance should be 10000 + 1000 = 11000
    Assert.assertEquals(10999_75000000L, user.getPositionArr()[pair.getQuotedId()].getQuantity());
  }
}
