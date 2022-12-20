package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Balance;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class MassCancelTest extends OrderBookTest {
  private static int nextUserId = 2000;

  protected void resetBalance(User user) {
    updateBalance(user.getId(), UpdateType.PUT, new Balance(BTC_USDT_F, 0, 0, 0, 0, null, 0, TokenType.ERC20));
    expectMessage("userId=" + user.getId());
    assertMessages();
    Assert.assertEquals(0, user.getPositionArr()[BTC_USDT_F].getQuantity());
  }

  protected void assertOpenOrders(User user, int securityId, double expectedOpenOrderValue, int expectedOpenOrderCount) {
    double openOrderValue = user.getPositionArr()[securityId].getUserOpenOrdersByPair().getAsksNotional()
        + user.getPositionArr()[securityId].getUserOpenOrdersByPair().getBidsNotional();
    int openOrderCount = user.getPositionArr()[securityId].getUserOpenOrdersByPair().getAsksCount()
        + user.getPositionArr()[securityId].getUserOpenOrdersByPair().getBidsCount();
    Assert.assertEquals(expectedOpenOrderValue, openOrderValue, 0.001);
    Assert.assertEquals(expectedOpenOrderCount, openOrderCount);
    Assert.assertEquals(expectedOpenOrderCount, user.getOpenOrderCount());
  }

  protected static void updateBalance(final int userId, final UpdateType updateType, final Balance... balances) {
    BalanceAdminMessage message = new BalanceAdminMessage();
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }
    message.setUpdateType(updateType);
    message.setUserId(userId);
    message.setTxType(TX_ADJUSTMENT);

    UserCache.addBalance(message);
  }

  protected User nextUser() {
    User user = createUser(nextUserId++, new Balance(BTC, 200, 0, 0, 0, null, 0, TokenType.ERC20), new Balance(USDT, 200, 0, 0, 0, null, 0, TokenType.ERC20),
        new Balance(BTC_USDT_F, 200, 0, 0, 0, null, 0, TokenType.ERC20));

    expectMessage("UserAdminMessage", "userId=" + user.getId());
    assertMessages();

    return user;
  }

  @Test
  public void massCancelSingleOrder() {
    User user = nextUser();

    // Add sell order to be canceled via mass cancel
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 505.50, 1);

    MassCancelOrder massCancelOrder = new MassCancelOrder();
    massCancelOrder.setUser(user);
    massCancelOrder.setSenderCompId("massCancelSingleOrder");
    massCancelOrder.setSecurityId(pair.getId()); // All order books = 0,

    massCancelOrder.onMatcher();
    expectMessage("MassCancelOrder", "securityId=" + pair.getId());
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=CANCELED, ordStatus=CANCELED");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 0, 0);
    resetBalance(user);
  }

  @Test
  public void massCancelMultipleOrder() {
    User user = nextUser();

    // Add sell order to be canceled via mass cancel
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 505.50, 1);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 811, 100, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 513.61, 2);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 711, 5000, Side.BUY, DAY));
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 869.11, 3);

    MassCancelOrder massCancelOrder = new MassCancelOrder();
    massCancelOrder.setUser(user);
    massCancelOrder.setSenderCompId("massCancelSingleOrder");
    massCancelOrder.setSecurityId(pair.getId()); // All order books = 0,
    massCancelOrder.getAccount();
    massCancelOrder.getCancelId();
    massCancelOrder.getCancelPriority();
    massCancelOrder.getSenderCompId();
    massCancelOrder.getClOrdId();
    massCancelOrder.getMessageType();
    massCancelOrder.getOrdType();
    massCancelOrder.getOrigOrderId();
    massCancelOrder.getPayloadType();
    massCancelOrder.getSecondaryOrderId();
    massCancelOrder.getSecurityId();
    massCancelOrder.getType();
    massCancelOrder.getUser();
    massCancelOrder.toJSON();
    massCancelOrder.toString();


    massCancelOrder.onMatcher();
    expectMessage("MassCancelOrder", "securityId=" + pair.getId());
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=" + user.getId() + ", orderId=3, "
        + "execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=" + user.getId() + ", orderId=3, "
        + "execType=CANCELED, ordStatus=CANCELED");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=2, "
        + "execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=2, "
        + "execType=CANCELED, ordStatus=CANCELED");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=CANCELED, ordStatus=CANCELED");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 0, 0);
    resetBalance(user);
  }

  @Test
  public void massCancelSingleOrderAfterBalanceUpdate() {
    User user = nextUser();

    // Add sell order to be canceled via mass cancel
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 505.50, 1);

    Assert.assertEquals(20_000, user.getPositionArr()[BTC_USDT_F].getQuantity());

    updateBalance(user.getId(), UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, 30, 2, null, 0, TokenType.ERC20));
    expectMessage("userId=" + user.getId()
        + ", updateType=PATCH, requestStatus=SUCCESS, balanceList=[Balance [assetId=12, balance=200.30, balance_change=.30, "
        + "eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0,"
        + " settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]], sourceSeqNum=0, sourceSendTime=0, "
        + "sourceSeqNum=0, sourceSendTime=0, balanceTransferToUserId=0]"); // baseUsdMark=0.0,
    assertMessages();

    Assert.assertEquals(20_030, user.getPositionArr()[BTC_USDT_F].getQuantity());

    MassCancelOrder massCancelOrder = new MassCancelOrder();
    massCancelOrder.setUser(user);
    massCancelOrder.setSenderCompId("massCancelSingleOrder");
    massCancelOrder.setSecurityId(pair.getId()); // All order books = 0,

    massCancelOrder.onMatcher();
    expectMessage("MassCancelOrder", "securityId=" + pair.getId());
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=CANCELED, ordStatus=CANCELED");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 0, 0);
    resetBalance(user);
  }

  @Test
  public void massCancelSingleOrderAfterBalanceOverride() {
    User user = nextUser();

    // Add sell order to be canceled via mass cancel
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 505.50, 1);

    Assert.assertEquals(20_000, user.getPositionArr()[BTC_USDT_F].getQuantity());

    updateBalance(user.getId(), UpdateType.PUT, new Balance(BTC_USDT_F, 30, 2, 0, 0, null, 0, TokenType.ERC20));
    expectMessage("userId=" + user.getId()
        + ", updateType=PUT, requestStatus=SUCCESS, balanceList=[Balance [assetId=12, balance=.30, balance_change=0, eventType=0,"
        + " orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0, usdValue=0.0, usdUnrealized=0.0, usdRealized=0.0, "
        + "settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0]]"); // baseUsdMark=0.0
    assertMessages();

    Assert.assertEquals(30, user.getPositionArr()[BTC_USDT_F].getQuantity());

    MassCancelOrder massCancelOrder = new MassCancelOrder();
    massCancelOrder.setUser(user);
    massCancelOrder.setSenderCompId("massCancelSingleOrder");
    massCancelOrder.setSecurityId(0); // All order books = 0,

    massCancelOrder.onMatcher();
    expectMessage("MassCancelOrder", "securityId=0");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=SELL, ordType=LIMIT, account=" + user.getId() + ", orderId=1, "
        + "execType=CANCELED, ordStatus=CANCELED");
    assertMessages();

    assertOpenOrders(user, pair.getId(), 0, 0);
    resetBalance(user);
  }
}
