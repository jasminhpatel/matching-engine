package com.solfini.integration;

import com.solfini.common.MessageType;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.user.UserCache;
import static com.solfini.common.Constants.ARRAY_ORDER_BOOK;
import static com.solfini.common.Constants.DEFAULT_TEST_ORDER_BOOK;
import static com.solfini.common.Constants.NO_PREORDER_CHECK;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;


public class CommonTests extends IntegrationTest {
  @Test
  public void createNewUser() throws Exception {
    publisher.send(userAdminMessage(USER_ID_ONE, "user100", "123user100", 0, 1, false));

    UserCache.add((UserAdminMessage) primary.expectMessage("UserAdminMessage", "userType=0, updateType=PUT, userId=100, username=user100, "
        + "firmId=0, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false"));

    if (!validatePrimaryOnly) {
      secondary.expectMessage("UserAdminMessage", "userType=0, updateType=PUT, userId=100, username=user100, "
          + "firmId=0, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false");
    }
  }

  @Test
  public void createNewUserWithBalance() throws Exception {
    publisher.send(userAdminMessage(101, "user101", "123user101", 0, 1, false));

    UserCache.add((UserAdminMessage) primary.expectMessage("UserAdminMessage", "userType=0, updateType=PUT, userId=101, username=user101, "
        + "firmId=0, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false"));

    if (!validatePrimaryOnly) {
      secondary.expectMessage("UserAdminMessage", "userType=0, updateType=PUT, userId=101, username=user101, "
          + "firmId=0, feeTier=1, status=0, accountType=0, lmm=false, routeToDestination=, useDiscountFeesCoin=false");
    }

    publisher.send(balanceAdminMessage(101, 1, 1, 1, new Balance(1, 50_000, 2, 0, 0, null)));
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=101, firmId=0, txType=0, txId=0, feeTier=0");
  }

  @Test
  public void createInstrument() throws Exception {
    publisher
        .send(securityDefinitionAdminMessage(1, AssetType.ASSET, "BTC", "BTC", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=1, symbol=BTC, name=BTC, "
            + "assetType=ASSET, triggerTimeMillis=0, baseId=0, quotedId=0, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));
  }

  @Test
  public void addOrder() throws Exception {
    publisher
        .send(securityDefinitionAdminMessage(11, AssetType.ASSET, "ABC", "ABC", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=11, symbol=ABC, name=ABC, "
            + "assetType=ASSET, triggerTimeMillis=0, baseId=0, quotedId=0, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));

    publisher
        .send(securityDefinitionAdminMessage(12, AssetType.ASSET, "PQR", "PQR", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=12, symbol=PQR, name=PQR, "
            + "assetType=ASSET, triggerTimeMillis=0, baseId=0, quotedId=0, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));

    publisher.send(
        securityDefinitionAdminMessage(13, AssetType.PAIR, "ABCPQR", "ABCPQR", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 11, 12));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=13, symbol=ABCPQR, name=ABCPQR, "
            + "assetType=PAIR, triggerTimeMillis=0, baseId=12, quotedId=11, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));

    createNewUser();

    publisher.send(order(0, 13, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));
    primary.expectMessage("BalanceAdminMessage", "userType=0, updateType=PUT, userId=100, firmId=0, txType=0, txId=0, feeTier=0");
    primary.expectMessage("Order", "securityId=13, price=10, price_scale=0, price2=0, price2_scale=0, qty=100, qty_scale=0, "
        + "side=BUY, orderPriority=0, marginCheckReferencePrice=0, clOrdId=ClOrdId, account=100, ordType=LIMIT, type=0, priceInt=1000, "
        + "quantityLong=10000, quantityOrigLong=10000, quantityOrig_scale=0, timeInForce=1, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false");
  }

  @Test
  public void massCancel() throws Exception {
    publisher
        .send(securityDefinitionAdminMessage(11, AssetType.ASSET, "ABC", "ABC", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=11, symbol=ABC, name=ABC, "
            + "assetType=ASSET, triggerTimeMillis=0, baseId=0, quotedId=0, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));

    publisher
        .send(securityDefinitionAdminMessage(12, AssetType.ASSET, "PQR", "PQR", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 0, 0));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=12, symbol=PQR, name=PQR, "
            + "assetType=ASSET, triggerTimeMillis=0, baseId=0, quotedId=0, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));

    publisher.send(
        securityDefinitionAdminMessage(13, AssetType.PAIR, "ABCPQR", "ABCPQR", 2, 2, DEFAULT_TEST_ORDER_BOOK, NO_PREORDER_CHECK, 11, 12));

    InstrumentCache.updateSecurityDefinition((SecurityDefinitionAdminMessage) primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=13, symbol=ABCPQR, name=ABCPQR, "
            + "assetType=PAIR, triggerTimeMillis=0, baseId=12, quotedId=11, priceScale=2, quantityScale=2, orderBookStrategy=2, "
            + "preOrderCheckStrategy=10, estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=250, requiredMarginPercent=500, minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, commissionType=0, indexFeedUsdMark=0.0"));

    createNewUser();

    primary.clearMessageQueues();

    publisher.send(order(0, 13, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    publisher.send(order(0, 13, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    publisher.send(order(0, 13, new DecimalFloat(10, 0), new DecimalFloat(100, 0), Side.BUY, 100, "ClOrdId"));
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    primary.expectMessage(MessageType.NEW_ORDER);

    publisher.send(massCancelOrder(USER_ID_ONE, "TEST"));
    primary.expectMessage(MessageType.BALANCE_ADMIN);

    primary.assertMessages();
  }
}
