package com.solfini.integration;

import com.solfini.common.MessageType;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

import static com.solfini.common.Constants.ARRAY_ORDER_BOOK;
import static com.solfini.common.Constants.MARGIN_PREORDER_CHECK;

import java.util.List;

public class AutoLiquidationWithOpenOrdersTest extends IntegrationTest {
  @Override
  public void setup() throws Exception {
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

    List<Balance> balanceListOne = createBalanceList(20_000, 20_000, 20_000);
    createNewUserWithBalance(USER_ID_SEVEN, "user106", "123user106", 0, 1, false, balanceListOne);

    List<Balance> balanceListTwo = createBalanceList(40_000, 40_000, 40_000);
    createNewUserWithBalance(USER_ID_EIGHT, "user107", "123user107", 0, 1, false, balanceListTwo);

  }

  //@Test
  public void alDueToNegativeBalanceNoOpenOrder() {
  }

  @Test(timeout = 250_000)
  public void alDueToNegativeBalanceWhenOpenOrderByLiquidizedUser() throws Exception {

    System.out.println("XX Starting Order Test");

    primary.clearMessageQueues();
    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(10, 0), Side.BUY, USER_ID_ONE, "ClOrdId"));

    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    publisher.send(globalStateAdminMessage(1));

    List<Balance> balanceList = createBalanceList(-50, -50, -50);
    publisher.send(balanceAdminMessage(USER_ID_SEVEN, 1, 1, 1, balanceList));
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);
    primary.expectMessage(MessageType.CANCEL_ORDER);

    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    primary.assertMessages();

    drainAll();
  }

  //@Test
  public void alDueToNegativeBalanceWhenOpenOrderByNonLiquidizedUser() throws Exception {
    publisher.send(order(1, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(10, 0), Side.BUY, 101, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.4, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=40.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=4.0, usdUnrealized=4.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=10, qty_scale=0, side=BUY, orderId=1, orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=[1, 0, 1], ordType=LIMIT, type=0, priceInt=10, quantityLong=10, quantityOrigLong=10, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(globalStateAdminMessage(1));
    //    primary.expect("GlobalStateAdminMessage []"); //TODO an ack not published

    List<Balance> balanceList = createBalanceList(-50, -50, -50);
    publisher.send(balanceAdminMessage(USER_ID_ONE, 1, 1, 1, balanceList));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=-50, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=-50, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=-50, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
  }

  //@Test
  public void alDueToNegativeBalanceOpenOrder() throws Exception {
    publisher.send(order(1, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(10, 0), Side.BUY, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=10, qty_scale=0, side=BUY, orderId=1, orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=10, quantityOrigLong=10, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(order(1, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(10, 0), Side.BUY, 101, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.4, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=40.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=4.0, usdUnrealized=4.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=10, qty_scale=0, side=BUY, orderId=2, orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=[1, 0, 1], ordType=LIMIT, type=0, priceInt=10, quantityLong=10, quantityOrigLong=10, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(globalStateAdminMessage(1));
    //    primary.expect("GlobalStateAdminMessage []"); //TODO an ack not published

    List<Balance> balanceList = createBalanceList(-50, -50, -50);
    publisher.send(balanceAdminMessage(USER_ID_ONE, 1, 1, 1, balanceList));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=-50, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=-50, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=-50, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");

    drainAll();
  }

  //@Test
  public void alDueToLeverageRatioExceedSell() throws Exception {
    publisher.send(globalStateAdminMessage(1));
    //    primary.expect("GlobalStateAdminMessage []"); //TODO an ack not published
    int orderIdOne = currentOrderID.incrementAndGet();
    publisher.send(order(orderIdOne, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(225, 0), Side.SELL, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=225, qty_scale=0, side=SELL, orderId="+orderIdOne+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=225, quantityOrigLong=225, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=SELL, qty=225, origOrderId="+orderIdOne+", cancelId="+orderIdOne+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    int orderIdFour = currentOrderID.incrementAndGet();
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=0, price_scale=0, price2=0, price2_scale=0, qty=20, qty_scale=0, side=SELL, orderId=2, orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=autoclose, account=100, ordType=LIMIT, type=1, priceInt=0, quantityLong=20, quantityOrigLong=20, quantityOrig_scale=0, timeInForce=IMMEDIATE_OR_CANCEL, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    //TODO Why cancel order
    primary.expect("CancelOrder [securityId=13, price=0, side=SELL, qty=20, origOrderId=2, cancelId=2, cancelPriority=0, clOrdId=autoclose, senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=0, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");
  }

  //@Test
  public void alDueToLeverageRatioExceedBuyStopLimit() throws Exception {
  }

  //@Test
  public void alDueToLeverageRatioExceedMultipleBuy() throws Exception {
    publisher.send(globalStateAdminMessage(1));

    int orderIdOne = currentOrderID.incrementAndGet();
    publisher.send(order(orderIdOne, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=BUY, orderId="+orderIdOne+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    int orderIdTwo = currentOrderID.incrementAndGet();
    publisher.send(order(2, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(80, 0), Side.BUY, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=80, qty_scale=0, side=BUY, orderId="+orderIdTwo+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=80, quantityOrigLong=80, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    int orderIdThree = currentOrderID.incrementAndGet();
    publisher.send(order(3, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(22, 0), Side.BUY, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=22, qty_scale=0, side=BUY, orderId="+orderIdThree+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=22, quantityOrigLong=22, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=BUY, qty=22, origOrderId="+orderIdThree+", cancelId="+orderIdThree+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=BUY, qty=80, origOrderId="+orderIdTwo+", cancelId="+orderIdTwo+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=BUY, qty=100, origOrderId="+orderIdOne+", cancelId="+orderIdOne+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    int orderIdFour = currentOrderID.incrementAndGet();
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=8, price_scale=0, price2=0, price2_scale=0, qty=20, qty_scale=0, side=SELL, orderId="+orderIdFour+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=autoclose, account=100, ordType=LIMIT, type=1, priceInt=8, quantityLong=20, quantityOrigLong=20, quantityOrig_scale=0, timeInForce=IMMEDIATE_OR_CANCEL, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    //TODO Why cancel order
    primary.expect("CancelOrder [securityId=13, price=8, side=SELL, qty=20, origOrderId="+orderIdFour+", cancelId="+orderIdFour+", cancelPriority=0, clOrdId=autoclose, senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=8, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");
  }

  //@Test
  public void alDueToLeverageRatioExceedMultipleSell() throws Exception {
    publisher.send(globalStateAdminMessage(1));
    //    primary.expect("GlobalStateAdminMessage []"); //TODO an ack not published
    int orderIdOne = currentOrderID.incrementAndGet();
    publisher.send(order(orderIdOne, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.SELL, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=SELL, orderId="+orderIdOne+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    int orderIdTwo = currentOrderID.incrementAndGet();
    publisher.send(order(orderIdTwo, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(80, 0), Side.SELL, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=80, qty_scale=0, side=SELL, orderId="+orderIdTwo+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=80, quantityOrigLong=80, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    int orderIdThree = currentOrderID.incrementAndGet();
    publisher.send(order(orderIdThree, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(45, 0), Side.SELL, 100, "ClOrdId"));
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=45, qty_scale=0, side=SELL, orderId="+orderIdThree+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=[C, l, O, r, d, I, d], account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=45, quantityOrigLong=45, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=2.0, usdUnrealized=2.0, usdRealized=0.0, quotedUsdMark=10.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=SELL, qty=45, origOrderId="+orderIdThree+", cancelId="+orderIdThree+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=SELL, qty=80, origOrderId="+orderIdTwo+", cancelId="+orderIdTwo+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    primary.expectMessage(MessageType.EXECUTION_REPORT); //TODO pending cancel Execution parse error
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("CancelOrder [securityId=13, price=10, side=SELL, qty=100, origOrderId="+orderIdOne+", cancelId="+orderIdOne+", cancelPriority=0, clOrdId=[C, l, O, r, d, I, d], senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");

    int orderIdFour = currentOrderID.incrementAndGet();
    primary.expect("BalanceAdminMessage [senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance [assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.2, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.010000000000000002, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=20.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=1.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0], Balance [assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]]");
    primary.expect("Order [securityId=13, price=8, price_scale=0, price2=0, price2_scale=0, qty=20, qty_scale=0, side=SELL, orderId="+orderIdFour+", orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, clOrdId=autoclose, account=100, ordType=LIMIT, type=1, priceInt=8, quantityLong=20, quantityOrigLong=20, quantityOrig_scale=0, timeInForce=IMMEDIATE_OR_CANCEL, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    //TODO Why cancel order
    primary.expect("CancelOrder [securityId=13, price=8, side=SELL, qty=20, origOrderId="+orderIdFour+", cancelId="+orderIdFour+", cancelPriority=0, clOrdId=autoclose, senderCompId=me01, account=100, ordType=LIMIT, type=0, priceInt=8, quantityLong=0, quantityOrigLong=0, secondaryOrderId=0");
  }

  //@Test(timeout = 25000)
  public void alDueToNegativeBalanceWhenOpenOrderFromOtherUsers() throws Exception {
    int orderIdOne = currentOrderID.incrementAndGet();
    publisher.send(order(1, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(10, 0), Side.SELL, 101, "ClOrdId"));
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=10, qty_scale=0, "
      + "side=SELL, orderPriority=0, marginCheckReferencePrice=0, "
      + "clOrdId=ClOrdId, account=101, ordType=LIMIT, type=1, priceInt=10, quantityLong=10, quantityOrigLong=10, quantityOrig_scale=0, "
      + "timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(globalStateAdminMessage(1));

    int orderIdTwo = currentOrderID.incrementAndGet();
    List<Balance> balanceList = createBalanceList(-1, -1, -1);
    publisher.send(balanceAdminMessage(USER_ID_ONE, 1, 1, 1, balanceList));
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=8, price_scale=0, price2=0, price2_scale=0, qty=1, qty_scale=0, "
      + "side=BUY, orderPriority=0, marginCheckReferencePrice=0, "
      + "clOrdId=autoclose, account=100, ordType=LIMIT, type=0, priceInt=8, quantityLong=1, quantityOrigLong=1, quantityOrig_scale=0, "
      + "timeInForce=IMMEDIATE_OR_CANCEL,  stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    primary.expectMessage("CancelOrder", "securityId=13, price=8, side=BUY, qty=1, origOrderId=" + orderIdTwo + ", cancelId=" + orderIdTwo +
      ", cancelPriority=0, clOrdId=autoclose, account=100, ordType=LIMIT, type=0, priceInt=8, quantityLong=0, "
      + "quantityOrigLong=0, secondaryOrderId=" + orderIdTwo );

    int orderIdThree = currentOrderID.incrementAndGet();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + " assetId=13, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=8, price_scale=0, price2=0, price2_scale=0, qty=1, qty_scale=0, "
      + "side=BUY, orderPriority=0, marginCheckReferencePrice=0, "
      + "clOrdId=autoclose, account=100, ordType=LIMIT, type=0, priceInt=8, quantityLong=1, quantityOrigLong=1, quantityOrig_scale=0, "
      + "timeInForce=IMMEDIATE_OR_CANCEL,  stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    int orderIdFour = currentOrderID.incrementAndGet();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + " assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=8, price_scale=0, price2=0, price2_scale=0, qty=1, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdFour + ", orderPriority=0, secondaryOrderId=" + orderIdFour + ", marginCheckReferencePrice=0, "
      + "clOrdId=autoclose, account=101, ordType=LIMIT, type=1, priceInt=8, quantityLong=1, quantityOrigLong=1, quantityOrig_scale=0, "
      + "timeInForce=IMMEDIATE_OR_CANCEL,  stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=-9, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0, "
      + " assetId=13, balance=-1, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("DRExecutionReport", "securityId=13, side=BUY, ordType=LIMIT, " //clOrdId=autoclose, account=100,
      + "execId=1, secondaryExecId=1, feeQty=0, timeInForce=IMMEDIATE_OR_CANCEL, qtyInOrderbook=0, feeInstrumentId=1, "
      + "isPaidToInsurance=true, execType=CALCULATED, ordStatus=null");

    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=40008, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=40000, balance_change=0, eventType=0, orderId=0, execId=0, "
      + " assetId=13, balance=39999, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("DRExecutionReport", "securityId=13, symbol=null, side=SELL, ordType=LIMIT, " //clOrdId=autoclose,
      + "execId=1, secondaryExecId=1, feeQty=0, timeInForce=IMMEDIATE_OR_CANCEL, qtyInOrderbook=0, feeInstrumentId=0, " //account=[1, 0, 1],
      + "isPaidToInsurance=false, execType=CALCULATED, ordStatus=null");
  }

  //@Test(timeout = 25000)
  public void alNotTriggeredDueToLeverageRatioWhenMultipleOpenOrdersFromOtherUsers() throws Exception {
    publisher.send(globalStateAdminMessage(1));
    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.SELL, 101, "ClOrdId"));

    int orderIdOne = currentOrderID.get();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdOne + ", orderPriority=0, secondaryOrderId="+ orderIdOne +", marginCheckReferencePrice=0, clOrdId=ClOrdId, "
      + "account=101, ordType=LIMIT, type=1, priceInt=10, quantityLong=100, "
      + "quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1,stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");


    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(80, 0), Side.SELL, 101, "ClOrdId"));
    int orderIdTwo = currentOrderID.get();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=80, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdTwo + ", orderPriority=0, secondaryOrderId=" + orderIdTwo + ", marginCheckReferencePrice=0, clOrdId=ClOrdId, "
      + "account=101, ordType=LIMIT, type=1, priceInt=10, quantityLong=80, quantityOrigLong=80, quantityOrig_scale=0, timeInForce=1, "
      + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(45, 0), Side.SELL, 101, "ClOrdId"));
    int orderIdThree = currentOrderID.get();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=40, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=40, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=45, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdThree + ", orderPriority=0, secondaryOrderId="+ orderIdThree +", marginCheckReferencePrice=0, clOrdId=ClOrdId, "
      + "account=101, ordType=LIMIT, type=1, priceInt=10, quantityLong=45, quantityOrigLong=45, quantityOrig_scale=0, timeInForce=1, "
      + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.SELL, 100, "ClOrdId"));
    int orderIdFour = currentOrderID.get();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdFour + ", orderPriority=0, secondaryOrderId=" + orderIdFour + ", marginCheckReferencePrice=0, clOrdId=ClOrdId, "
      + "account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, "
      + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(80, 0), Side.SELL, 100, "ClOrdId"));
    int orderIdFive = currentOrderID.get();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=80, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdFive + ", orderPriority=0, secondaryOrderId=" + orderIdFive + ", marginCheckReferencePrice=0, clOrdId=ClOrdId, "
      + "account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=80, quantityOrigLong=80, quantityOrig_scale=0, timeInForce=1, "
      + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(order(0, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(45, 0), Side.SELL, 100, "ClOrdId"));
    int orderIdSix = currentOrderID.get();
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, "
      + "assetId=1, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=12, balance=20, balance_change=0, eventType=0, orderId=0, execId=0, "
      + "assetId=13, balance=20, balance_change=0, eventType=0, orderId=0, execId=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=45, qty_scale=0, "
      + "side=SELL, orderId=" + orderIdSix + ", orderPriority=0, secondaryOrderId=" + orderIdSix + ", marginCheckReferencePrice=0, clOrdId=ClOrdId, "
      + "account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=45, quantityOrigLong=45, quantityOrig_scale=0, timeInForce=1, "
      + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");
  }

  //@Test
  public void alDueToLeverageRatioWhenMultipleBuyAndSellOpenOrdersFromOtherUsers() throws Exception {
  }
}

