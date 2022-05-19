package com.solfini.integration;

import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.user.User;
import com.solfini.user.UserCache;

import static com.solfini.common.Constants.DEFAULT_TEST_ORDER_BOOK;
import static com.solfini.common.Constants.NO_PREORDER_CHECK;

import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class AddOrderNoMarginCheckTest extends IntegrationTest {

  @Override
  public void setup() throws Exception {
    if (InstrumentCache.get(SECURITY_ID_QUOTED) == null) {
      createInstrument(SECURITY_ID_QUOTED, AssetType.ASSET, "ABC", "ABC", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0);
    }

    if (InstrumentCache.get(SECURITY_ID_QUOTE) == null) {
      createInstrument(SECURITY_ID_QUOTE, AssetType.ASSET, "PQR", "PQR", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0);
    }

    if (InstrumentCache.get(SECURITY_ID_PAIR) == null) {
      createInstrument(SECURITY_ID_PAIR, AssetType.PAIR, "ABCPQR", "ABCPQR", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK,
          SECURITY_ID_QUOTED, SECURITY_ID_QUOTE);
    }

    User user = null;
    try {
      user = UserCache.get(USER_ID_ONE);
    } catch (ArrayIndexOutOfBoundsException e) {
    }

    if (!user.isActive()) {
      createNewUser(USER_ID_ONE, "user100", "123user100", 0, 1, false);
    }
  }

  @Test
  public void addBuyOrder() throws Exception {
    publisher.send(order(1, 13, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));
    primary.expectMessage("BalanceAdminMessage",
        "senderCompId=me01, userType=0, updateType=PUT, " + "userId=100, firmId=0, txType=0, txId=0, feeTier=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, side=BUY, "
        + "orderId=1, orderPriority=0, marginCheckReferencePrice=0, clOrdId=ClOrdId, account=100, ordType=LIMIT, type=0, priceInt=1000,"
        + " quantityLong=10000, quantityOrigLong=10000, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");
  }
}
