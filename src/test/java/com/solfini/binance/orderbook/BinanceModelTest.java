package com.solfini.binance.orderbook;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.event.Level;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.model.ExpectedMessage;
import com.solfini.matchengine.model.KeyValue;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.MessageDecoder;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

public class BinanceModelTest implements Constants {

  protected static final int USDC = 1;
  protected static final int USDT = 1;
  protected static final int BTC = 2;
  protected static final int BTC_USDC_F = 3;
  protected static final int BTC_USDT_F = 3;

  private final List<ExpectedMessage> expectedMessageList = new ArrayList<ExpectedMessage>();
  private final List<ExpectedMessage> expectedOutputMessageList = new ArrayList<ExpectedMessage>();
  private final List<Message> publisherInputMessageList = new ArrayList<>();

  @Before
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      PropertyReader.initialize(null, properties);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @After
  public void after() {
    clearMessages();
  }

  private ExpectedMessage makeExpectedMessage(final String type, final String message) {
    final String[] fields = message.split(",");
    final KeyValue[] keyValueArray = new KeyValue[fields.length];

    for (int i = 0; i < fields.length; i++) {
      final String[] tokens = fields[i].split("=");
      keyValueArray[i] = new KeyValue(tokens[0].trim(), tokens[1].trim());
    }

    return new ExpectedMessage(type, keyValueArray);
  }

  protected void expectMessage(final String type, final KeyValue... keyValueArray) {
    expectedMessageList.add(new ExpectedMessage(type, keyValueArray));
  }

  protected void expectMessage(final KeyValue... keyValueArray) {
    expectMessage(null, keyValueArray);
  }

  protected void expectMessage(final String type, final String message) {
    expectedMessageList.add(makeExpectedMessage(type, message));
  }

  protected void expectMessage(final String message) {
    expectMessage(null, message);
  }

  protected void expectMessage(final String type, final String... strings) {
    expectedMessageList.add(new ExpectedMessage(type, strings));
  }

  protected void clearMessages() {
    ArrayList<Message> messages = new ArrayList<>();
    do {
      messages.clear();
      Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
    } while (!messages.isEmpty());
  }

  protected void assertMessages() {
    ArrayList<Message> resultList = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(resultList, 4096);

    if (expectedMessageList.size() != resultList.size()) {
      for (int i = 0; i < resultList.size(); i++) {
        System.out.println("Message (" + i + "): " + resultList.get(i));
        if (expectedMessageList.size() > i)
          System.out.println("expected Message (" + i + "): " + expectedMessageList.get(i));
      }
    }

    Assert.assertEquals(expectedMessageList.size(), resultList.size());

    for (int i = 0; i < expectedMessageList.size(); i++) {
      System.out.println("Message (" + i + "): " + resultList.get(i));
      expectedMessageList.get(i).verify(resultList.get(i).toString());
    }

    expectedMessageList.clear();
    publisherInputMessageList.addAll(resultList);
  }

  protected void assertOutputExecutionReport(final byte[] data, final String message) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(16384);
    MutableAsciiBuffer buffer = new MutableAsciiBuffer(byteBuffer);

    int length = data.length - MessageDecoder.NORMAL_API_OFFSET;
    buffer.wrap(data, MessageDecoder.NORMAL_API_OFFSET, length);

    // final HeaderDecoder headerDecoder = new HeaderDecoder();
    /// headerDecoder.decode(buffer, 0, length);
    // Assert.assertEquals(MsgType.EXECUTION_REPORT, headerDecoder.msgTypeAsEnum());

    // final ExecutionReportDecoderSlimmed decoder = new ExecutionReportDecoderSlimmed();
    // decoder.decode(buffer, 0, length);

    // final ExpectedMessage expected = makeExpectedMessage(null, message);
    // expected.verify(decoder.toString().replaceAll("\": \"", "="));
  }

  protected void expectOutput(final String type, final String message) {
    expectedOutputMessageList.add(makeExpectedMessage(type, message));
  }

  protected void expectOutput(final String message) {
    expectOutput(null, message);
  }

  protected void expectOutput(final String type, final String... strings) {
    expectedOutputMessageList.add(new ExpectedMessage(type, strings));
  }

  protected void clearExpectedOutputMessages() {
    expectedOutputMessageList.clear();
  }

  protected List<byte[]> assertOutputMessages(boolean forcePublish) {
    // Setup publisher
    Context.setKafkaPublisher(new KafkaPublisher());

    // Publish all messages if prompted

    if (forcePublish) {
      for (Message msg : publisherInputMessageList) {
        try {
          msg.onPublish();
        } catch (Exception e) {
          System.err.println("Exception thrown while publishing message. message: " + msg + " error: " + e);
        }
      }
    }

    // Read from the publisher output queue
    final ArrayList<byte[]> outputList = new ArrayList<>();
    Context.getPublisherToKafkaPublisherQueue().drainTo(outputList, 1_000);

    final ArrayList<Message> resultList = new ArrayList<>();
    final ArrayList<Message> baseMessages = new ArrayList<>();
    final MessageDecoder decoder = new MessageDecoder();
    for (byte[] data : outputList) {
      try {
        Message msg = decoder.decode(data, 0, baseMessages);
        if (msg == null) {
          System.err.println("Decoder returned null while decoding");
          continue;
        }

        resultList.add(msg);
      } catch (Exception e) {
        System.err.println("Exception thrown while decoding message. error: " + e);
        continue;
      }
    }


    // Validate expectations
    if (expectedOutputMessageList.size() != resultList.size()) {
      for (int i = 0; i < resultList.size(); i++) {
        System.out.println("[PUBLISHER] Message (" + i + "): " + resultList.get(i));
        if (expectedOutputMessageList.size() > i)
          System.out.println("[PUBLISHER] expected Message (" + i + "): " + expectedOutputMessageList.get(i));
      }
    }

    Assert.assertEquals(expectedOutputMessageList.size(), resultList.size());

    for (int i = 0; i < expectedOutputMessageList.size(); i++) {
      System.out.println("[PUBLISHER]  Message (" + i + "): " + resultList.get(i));
      expectedOutputMessageList.get(i).verify(resultList.get(i).toString());
    }

    expectedOutputMessageList.clear();
    publisherInputMessageList.clear();

    return outputList;
  }

  // Verifies all messages from the publisher
  protected List<byte[]> assertOutputMessages() {
    return assertOutputMessages(true);
  }

  protected void clearQueues() {
    expectedMessageList.clear();
    expectedOutputMessageList.clear();
    publisherInputMessageList.clear();

    final ArrayList<byte[]> outputList = new ArrayList<>();
    Context.getPublisherToKafkaPublisherQueue().drainTo(outputList, 1_000);

    ArrayList<Message> resultList = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(resultList, 1_000);
  }

  protected SecurityDefinitionAdminMessage createInstrumentDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int priceScale, final int quantityScale) {

    SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage();
    message.setUpdateType(updateType);
    message.setAssetType(AssetType.ASSET);
    message.setSymbol(symbol);
    message.setName(symbol);
    message.setSecurityId(securityId);
    message.setQuantityScale((short) quantityScale);
    message.setPriceScale((short) priceScale);
    message.setOrderBookStrategy(DEFAULT_TEST_ORDER_BOOK);
    message.setPreOrderCheckStrategy(CASH_PREORDER_CHECK);
    message.setMaintMarginBasisPoints(250);
    message.setRequiredMarginBasisPoints(500);

    return message;
  }

  protected SecurityDefinitionAdminMessage createInstrumentPairDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int baseId, final int quotedId, final int priceScale, final int quantityScale) {

    SecurityDefinitionAdminMessage message = createInstrumentDefinition(securityId, updateType, symbol, priceScale, quantityScale);
    message.setAssetType(AssetType.PERPETUAL_SWAP);
    message.setPreOrderCheckStrategy(MARGIN_PREORDER_CHECK);
    message.setBaseId(baseId);
    message.setQuotedId(quotedId);

    return message;
  }

  protected User createUser(final int userId, final int userType, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    message.setUserType(userType);
    message.setUsername("user_" + userId);
    message.setPassword("pass_" + userId);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }

  protected User createUser(final int userId, final Balance... balances) {
    return createUser(userId, User.TRADER, balances);
  }

  protected Order createOrder(final int orderId, final User user, final int securityId, final long price, final long quantity,
      final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  protected Order createOrder(final int orderId, final User user, final int securityId, final long price, final int priceScale,
      final long quantity, final int quantityScale, final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.LIMIT, securityId, price, priceScale, quantity, quantityScale, side, timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  protected Order createMarketOrder(final int orderId, final User user, final int securityId, final long quantity, final Side side,
      final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.MARKET, securityId, 0, quantity, side, timeInForce);
    order.setOrdType(OrdType.MARKET);
    if (side == Side.SELL)
      order.setType(Constants.SELL_MARKET);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_MARKET);

    return order;
  }

  protected Order createStopLimitOrder(final int orderId, final User user, final int securityId, final long price, final long stopPx,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.STOP_LIMIT, securityId, price, quantity, side, timeInForce);
    order.setStopPx(stopPx, (short) 2);
    order.setStopPxInt((int) stopPx);
    if (side == Side.SELL)
      order.setType(Constants.STOP_SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.STOP_BUY_LIMIT);

    return order;
  }

  private int clOrdIdCounter = 0;

  private Order createOrder(final int orderId, final User user, final OrdType orderType, final int securityId, final long price,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    return createOrder(orderId, user, orderType, securityId, price, 2, quantity, 2, side, timeInForce);
  }

  private static long scale(final long value, final int scale1, final int scale2) {
    long result = value;
    for (int i = 0; i < Math.abs(scale1 - scale2); i++) {
      if (scale1 > scale2)
        result /= 10;
      else if (scale2 > scale1)
        result *= 10;
    }
    return result;
  }

  private Order createOrder(final int orderId, final User user, final OrdType orderType, final int securityId, final long price,
      final int priceScale, final long quantity, final int quantityScale, final Side side, final TimeInForce timeInForce) {
    final int pScale = InstrumentCache.getPair(securityId) == null ? priceScale : InstrumentCache.getPair(securityId).getPriceScale();
    final int qScale = InstrumentCache.getPair(securityId) == null ? quantityScale : InstrumentCache.getPair(securityId).getQuantityScale();
    Order order = OrderObjectPool.get();
    order.setOrderId(orderId);
    order.setUser(user);
    order.setAccount(user.getId());
    order.setClOrdId(("ClOrdId" + ++clOrdIdCounter));
    order.setOrdType(orderType);
    order.setSecurityId(securityId);
    order.setSide(side);
    order.setPrice(price, (short) priceScale);
    order.setPriceInt((int) scale(price, priceScale, pScale));
    order.setQty(quantity, (short) quantityScale);
    order.setQuantityLong(scale(quantity, quantityScale, qScale));
    order.setQuantityOrigLong(scale(quantity, quantityScale, qScale));
    order.setTimeInForce(timeInForce);

    return order;
  }

  protected static CancelOrder createCancelOrder(int cancelId, final int orderId, final User user, final int securityId, final long price,
      final long quantity, Side side, TimeInForce timeInForce) {
    return createCancelOrder(cancelId, orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
  }

  protected static CancelOrder createCancelOrder(int cancelId, final int orderId, final User user, OrdType orderType, final int securityId,
      final long price, final long quantity, Side side, TimeInForce timeInForce) {
    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.setCancelId(cancelId);
    cancelOrder.setUser(user);
    cancelOrder.setAccount(user.getId());
    cancelOrder.setOrigOrderId(orderId);
    cancelOrder.setClOrdId("ClOrdId");
    cancelOrder.setOrdType(orderType);
    cancelOrder.setSecurityId(securityId);
    cancelOrder.setSide(side);
    cancelOrder.setPrice(price, (short) 2);
    cancelOrder.setPriceInt((int) price);
    cancelOrder.setQty(quantity, (short) 2);
    cancelOrder.setQuantityLong(quantity);
    cancelOrder.setQuantityOrigLong(quantity);

    return cancelOrder;
  }

  protected static CancelReplaceOrder createCancelReplaceOrder(int cancelId, final long orderId, final User user, final int securityId,
      final long price, final long quantity, Side side, TimeInForce timeInForce, Order order) {
    CancelReplaceOrder cancelReplaceOrder = new CancelReplaceOrder();
    cancelReplaceOrder.setOrigOrderId(orderId);
    cancelReplaceOrder.setOrder(order);
    cancelReplaceOrder.setCancelId(cancelId);
    cancelReplaceOrder.setUser(user);
    cancelReplaceOrder.setAccount(user.getId());
    cancelReplaceOrder.setClOrdId("ClOrdId");
    cancelReplaceOrder.setOrdType(OrdType.LIMIT);
    cancelReplaceOrder.setSecurityId(securityId);
    cancelReplaceOrder.setSide(side);
    cancelReplaceOrder.setPrice(price, (short) 2);
    cancelReplaceOrder.setPriceInt((int) price);
    cancelReplaceOrder.setQty(quantity, (short) 2);
    cancelReplaceOrder.setQuantityLong(quantity);
    cancelReplaceOrder.setQuantityOrigLong(quantity);

    return cancelReplaceOrder;
  }

  protected static MassCancelOrder createMassCancelOrder(final long cancelId, final Order order) {
    MassCancelOrder massCancelOrder = new MassCancelOrder(order, cancelId, 1);

    return massCancelOrder;
  }
}
