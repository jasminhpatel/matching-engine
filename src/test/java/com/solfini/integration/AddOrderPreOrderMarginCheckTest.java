package com.solfini.integration;

import com.solfini.common.MessageType;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

import static com.solfini.common.Constants.DEFAULT_TEST_ORDER_BOOK;
import static com.solfini.common.Constants.MARGIN_PREORDER_CHECK;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AddOrderPreOrderMarginCheckTest extends IntegrationTest {
  private final AtomicInteger currentOrderID = new AtomicInteger();

  @Override
  public void setup() throws Exception {
    if (InstrumentCache.get(SECURITY_ID_QUOTED) == null) {
      createInstrument(SECURITY_ID_QUOTED, AssetType.ASSET, "BTC", "BTC", 0, 0, DEFAULT_TEST_ORDER_BOOK, MARGIN_PREORDER_CHECK, 0, 0);
    }

    if (InstrumentCache.get(SECURITY_ID_QUOTE) == null) {
      createInstrument(SECURITY_ID_QUOTE, AssetType.ASSET, "USDT", "USDT", 0, 0, DEFAULT_TEST_ORDER_BOOK, MARGIN_PREORDER_CHECK, 0, 0);
    }

    if (InstrumentCache.getPair(SECURITY_ID_PAIR) == null) {
      createInstrument(SECURITY_ID_PAIR, AssetType.PAIR, "BTC/USDT", "BTC/USDT", 0, 0, DEFAULT_TEST_ORDER_BOOK, MARGIN_PREORDER_CHECK,
          SECURITY_ID_QUOTED, SECURITY_ID_QUOTE);
    }

    User user = null;
    try {
      user = UserCache.get(USER_ID_ONE);
    } catch (ArrayIndexOutOfBoundsException e) {
    }

    if (!user.isActive()) {
      List<Balance> balanceList = createBalanceList(500, 500, 500);
      createNewUserWithBalance(USER_ID_ONE, "user100", "123user100", 0, 1, false, balanceList);
    }
  }

  @Test
  public void addBuyOrder() throws Exception {
    publisher.send(order(1, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));

    primary.expectMessage("BalanceAdminMessage",
        "senderCompId=me01, connectionId=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0");
    primary.expectMessage("Order",
        "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=BUY, orderPriority=0, marginCheckReferencePrice=0, ordType=LIMIT, type=0, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");
  }

  // @Test(timeout=10000)
  public void tradeExec() throws Exception {
    publisher.send(order(1, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));

    primary.expectMessage("BalanceAdminMessage",
        "senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null");
    primary.expectMessage("Order",
        "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=BUY, orderPriority=0, marginCheckReferencePrice=0, clOrdId=ClOrdId, account=100, ordType=LIMIT, type=0, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    publisher.send(order(2, SECURITY_ID_PAIR, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.SELL, 100, "ClOrdId"));

    primary.expectMessage("BalanceAdminMessage",
        "senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0");
    primary.expectMessage("Order",
        "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=SELL, orderPriority=0, marginCheckReferencePrice=0, clOrdId=ClOrdId, account=100, ordType=LIMIT, type=1, priceInt=10, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");

    primary.expectMessage("BalanceAdminMessage",
        "senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0");
    primary.expectMessage(MessageType.EXECUTION_REPORT);
    // primary.expectMessage("DRExecutionReport", "securityId=13, side=SELL, ordType=LIMIT, execId=1, orderQty=100, leavesQty=0, cumQty=100,
    // priceInOrderbook=10, qtyInOrderbook=0, price=10, avgPx=null, lastPx=10, lastQty=100, feeInstrumentId=0, feeQty=0, secondaryExecId=0,
    // timeInForce=1, execType=TRADE");

    primary.expectMessage("BalanceAdminMessage",
        "senderCompId=me01, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0");
    primary.expectMessage(MessageType.EXECUTION_REPORT);
    // primary.expectMessage("DRExecutionReport", "securityId=13, side=BUY, ordType=LIMIT, execId=1, orderQty=100, leavesQty=0, cumQty=100,
    // priceInOrderbook=10, qtyInOrderbook=0, price=10, avgPx=null, lastPx=10, lastQty=100, feeInstrumentId=0, feeQty=0, secondaryExecId=0,
    // timeInForce=1, execType=TRADE");
  }

}
