package com.solfini.binance.orderbook;

import java.io.IOException;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.After;
import org.junit.Before;
import org.slf4j.event.Level;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.CancelReplaceRequestHandler;
import com.solfini.matchengine.decoder.CancelRequestHandler;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class BinanceOrderBookTest extends BinanceModelTest {
  protected OrderBookValidator validator;
  protected OrderBookWrapper orderBook = null;
  protected InstrumentPair pair = null;
  protected PreOrderCheck preOrderCheck = null;
  protected User user = null;
  protected User user2 = null;
  protected User user3 = null;
  protected User user4 = null;
  protected User user5 = null;

  protected User user6 = null;
  protected User user7 = null;
  protected User user8 = null;
  protected User user9 = null;
  protected User user10 = null;
  public static final TimeInForce DAY = TimeInForce.DAY;
  public static final TimeInForce GOOD_TILL_CANCEL = TimeInForce.GOOD_TILL_CANCEL;
  public static final TimeInForce IMMEDIATE_OR_CANCEL = TimeInForce.IMMEDIATE_OR_CANCEL;
  public static final TimeInForce FILL_OR_KILL = TimeInForce.FILL_OR_KILL;
  public static final TimeInForce POST_ONLY = TimeInForce.POST_ONLY;


  protected class OrderBookWrapper {
    private final OrderBook orderBook;
    private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();
    private final CancelRequestHandler cancelRequestHandler = new CancelRequestHandler();

    public OrderBookWrapper(InstrumentPair instrumentPair) {
      orderBook = OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, instrumentPair);
    }

    public OrderBookWrapper(int orderBookStrategy, int preOrderCheckStrategy, InstrumentPair instrumentPair) {
      orderBook = OrderBookFactory.create(orderBookStrategy, preOrderCheckStrategy, instrumentPair);
    }

    public OrderBook orderBook() {
      return orderBook;
    }

    public NewOrderSingleHandler newOrderSingleHandler() {
      return newOrderSingleHandler;
    }

    public CancelRequestHandler cancelRequestHandler() {
      return cancelRequestHandler;
    }

    public void addOrder(final Order order) {
      Message message = NewOrderSingleHandler.parseOrder(order);
      if (message instanceof Order)
        message.onMatcher();
      else
        Context.getMatcherToPublisherQueue().add(message);
    }

    public void cancelOrder(final CancelOrder cancelOrder) {
      Message message = CancelRequestHandler.parseCancelOrder(cancelOrder);
      if (message instanceof CancelOrder)
        message.onMatcher();
      else
        Context.getMatcherToPublisherQueue().add(message);
    }

    public void cancelReplaceOrder(final CancelReplaceOrder cancelReplaceOrder) {
      cancelReplaceOrder.setOrder((Order) NewOrderSingleHandler.parseOrder(cancelReplaceOrder.getOrder()));
      Message message = CancelReplaceRequestHandler.parseCancelOrder(cancelReplaceOrder);
      if (message instanceof CancelReplaceOrder)
        message.onMatcher();
      else
        Context.getMatcherToPublisherQueue().add(message);
    }

    public void build() {
      orderBook.changeState(MarketStatus.OPEN, 0, null);
    }

    // switch output queue to a disabled queue so nothing is published
    public final void disableOutputQueue() {
      orderBook.disableOutputQueue();
    }

    // restore output to the actual publishing queue
    public final void restoreOutputQueue() {
      orderBook.restoreOutputQueue();
    }

    public final OrderBookValidator getOrderBookValidator() {
      return orderBook.getOrderBookValidator();
    }

    public final void clearOrderBook() {
      orderBook.clearOrderBook();
    }

    public String toString(final int priceLevel) {
      return orderBook.toString(priceLevel);
    }
  }

  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("QUEUE_CAPACITY", "32768");
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "65536");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "0");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    properties.setProperty("REJECT_DUP_CLORIDS_ENABLED", "FALSE");
    onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void onConfigure(final Properties properties) {}

  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 8, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 8, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 3));

    InstrumentCache.get(USDT).setIndexFeedUsdMark(1.0);
    InstrumentCache.get(BTC).setIndexFeedUsdMark(0.010000000000000002);

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PUT");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook =
        new BinanceOrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    (orderBook.orderBook).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));
    this.validator = orderBook.getOrderBookValidator();

    pair.setOrderBook(orderBook.orderBook);
    pair.setIndexFeedUsdMark(10.084);// 10084.510000000002);
  }

  protected void createUsers() {
    user = createUser(18);

    user.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=18");
    expectOutput("userId=18");

    user2 = createUser(19);
    user2.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=19");
    expectOutput("userId=19");

    user3 = createUser(20);
    user3.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=20");
    expectOutput("userId=20");

    user4 = createUser(21);
    user4.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=21");
    expectOutput("userId=21");

    user5 = createUser(22);
    user5.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=22");
    expectOutput("userId=22");

    user6 = createUser(23);
    user6.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=23");
    expectOutput("userId=23");

    user7 = createUser(24);
    user7.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=24");
    expectOutput("userId=24");

    user8 = createUser(25);
    user8.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=25");
    expectOutput("userId=25");

    user9 = createUser(26);
    user9.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=26");
    expectOutput("userId=26");

    user10 = createUser(27);
    user10.addPosition(USDT, 10000_000000L, null, 0, TokenType.ERC20);
    expectMessage("userId=27");
    expectOutput("userId=27");
  }

  @Before
  @Override
  public void before() {
    configure();
    clearQueues();
    createInstruments();
    createUsers();
    assertMessages();
  }

  @After
  public void after() {
    clearQueues();
  }

  protected void addBuyOrdersForSweep() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 200, Side.BUY, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1010, 200, Side.BUY, DAY));

    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=200, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=5, ordType=LIMIT, side=BUY, price=1010, orderQty=200, leavesQty=200, ordStatus=NEW");


    assertMessages();
  }

  protected void addSellOrdersForSweep() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 200, Side.SELL, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1011, 200, Side.SELL, DAY));

    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=200, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1011, orderQty=200, leavesQty=200, ordStatus=NEW");
    assertMessages();
  }
}
