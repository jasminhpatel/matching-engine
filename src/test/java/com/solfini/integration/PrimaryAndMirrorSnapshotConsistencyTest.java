package com.solfini.integration;

import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.snapshot.JsonReader;
import com.solfini.util.snapshot.SnapConverter;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.JUnitCore;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

import static com.solfini.common.Constants.ARRAY_ORDER_BOOK;
import static com.solfini.common.Constants.MARGIN_PREORDER_CHECK;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


public class PrimaryAndMirrorSnapshotConsistencyTest extends IntegrationTest {
  private static final AtomicInteger currentOrderID = new AtomicInteger();
  private static final int NUMBER_OF_ORDERS = 1;

  @Before
  public void setUp() throws Exception {
    LogLevel.setLevel(Level.TRACE);

    validatePrimaryOnly = false;

    if(InstrumentCache.get(SECURITY_ID_QUOTED) == null) {
      createInstrument(SECURITY_ID_QUOTED, AssetType.ASSET, "BTC", "BTC", 0, 0,
        ARRAY_ORDER_BOOK, MARGIN_PREORDER_CHECK, 0, 0);
    }

    if(InstrumentCache.get(SECURITY_ID_QUOTE) == null) {
      createInstrument(SECURITY_ID_QUOTE, AssetType.ASSET, "USDT", "USDT", 0, 0,
        ARRAY_ORDER_BOOK, MARGIN_PREORDER_CHECK, 0, 0);
    }

    if(InstrumentCache.getPair(SECURITY_ID_PAIR) == null) {
      createInstrument(SECURITY_ID_PAIR, AssetType.PAIR, "BTC/USDT", "BTC/USDT", 0, 0,
        ARRAY_ORDER_BOOK, MARGIN_PREORDER_CHECK, SECURITY_ID_QUOTED, SECURITY_ID_QUOTE);
    }

    User user = null;
    try{
      user = UserCache.get(USER_ID_ONE);
    }
    catch (ArrayIndexOutOfBoundsException e) {}

    if(!user.isActive()) {
      List<Balance> balanceList = createBalanceList(2000, 2000, 2000);
      createNewUserWithBalance(USER_ID_ONE, "user100", "123user100", 0, 1, false, balanceList);
    }

  }

  /*
  1.Create instrument
  2.Create N number of users with balance, without balance
  3.Submit M number of orders (limit, stop limit, market) (buy, sell)
  4.trigger snapshot for primary and secondary
  5.convert snapshots primary and secondary to JSON
  6.Compare JSON
   */
  //@Test
  public void submitOrder() throws Exception {
    Message order = null;
    for (int i = 0; i < NUMBER_OF_ORDERS; i++) {
      int orderIdOne = currentOrderID.incrementAndGet();
      publisher.send(order(orderIdOne, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));

      primary.expectMessage(MessageType.BALANCE_ADMIN);
      order = primary.expectMessage(MessageType.NEW_ORDER);

      if(!validatePrimaryOnly){
        secondary.expect("BalanceAdminMessage [senderCompId=EXEC, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.1, usdUnrealized=0.1, usdRealized=0.0, quotedUsdMark=5147.510000000001, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
        secondary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=BUY, orderId="+orderIdOne+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], senderCompIdCharArr=null, senderCompAsString=null, account=[1, 0, 0], ordType=LIMIT, type=0, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, expireTime=null, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inputTime=0, decodedTime=0]");
      }
    }

    TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage();
    tradeStateAdminMessage.setRouteToDestination("primary");
    publisher.send(tradeStateAdminMessage);
    primary.expectMessage(MessageType.TRADE_STATE_ADMIN);
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    SnapResponseAdminMessage snapResponseAdminMessage = (SnapResponseAdminMessage) primary.expectMessage(MessageType.SNAP_RESPONSE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    PropertyReader.initialize(null, properties);
    SnapConverter converter = new SnapConverter();

    String primaryDir = Context.getChronicleEngineSnapQueueDirectory() + "/" + snapResponseAdminMessage.getSnapId();
    converter.exportSnapshot(primaryDir, "Output_primary.json");

    tradeStateAdminMessage.setRouteToDestination("secondary");
    drainAll(secondary);
    secondary.expectMessage(MessageType.SECURITY_DEFINITION);
    secondary.expectMessage(MessageType.TRADE_STATE_ADMIN);
    secondary.expectMessage(MessageType.BALANCE_ADMIN);
    SnapResponseAdminMessage snapResponseSecondary = (SnapResponseAdminMessage) secondary.expectMessage(MessageType.SNAP_RESPONSE);

    String secondaryDir =  Context.getChronicleEngineSnapQueueDirectory() + "/" + snapResponseSecondary.getSnapId();
    converter.exportSnapshot(secondaryDir, "Output_secondary.json");

    JsonReader jsonReaderPrimary = new JsonReader("Output_primary.json");
    JsonReader jsonReaderSecondary = new JsonReader("Output_secondary.json");

    JsonObject expected = jsonReaderPrimary.next();
    JsonObject actual = jsonReaderSecondary.next();

    while (actual != null && expected != null) {
      Assert.assertEquals(expected, actual);

      actual = jsonReaderPrimary.next();
      expected = jsonReaderSecondary.next();
    }

    Assert.assertNull(expected);
    Assert.assertNull(actual);
  }

  public static void main(String[] args) {
    try {
      for (final String arg: args) {
        if (arg.equals("--debug")) {
          Log.enableDebug();
        }
      }

      PropertyReader.initialize(Resources.getResource("integration.properties").openStream(), null);
      JUnitCore.main(PrimaryAndMirrorSnapshotConsistencyTest.class.getName());
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    }
  }

}

