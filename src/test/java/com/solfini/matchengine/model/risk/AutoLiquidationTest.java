package com.solfini.matchengine.model.risk;

import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.model.ExpectedMessage;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.risk.UserRiskCache;
import com.solfini.user.User;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;
import java.util.List;
import java.util.Properties;
import static com.solfini.sbe.encoder.TimeInForce.DAY;
import static org.junit.Assert.assertEquals;

public class AutoLiquidationTest extends RiskTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
  }

  private void autoLiquidationNotTriggeredDueToDecreasedBalance(final int securityId, final boolean openOrders, final long change,
      final long... positions) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    if (openOrders) {
      orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 495000, 20000, Side.BUY, DAY));
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, ordStatus=NEW");
      assertMessages();

      assertOpenOrders(user, BTC_USDT_F, 4950 * 200, 1);
    } else {
      assertOpenOrders(user, BTC_USDT_F, 0, 0);
    }

    updateBalance(user, UpdateType.PATCH, new Balance(securityId, 0, 0, change, 2));
    expectMessage("BalanceAdminMessage", "userId=" + user.getId() + ", updateType=PATCH, assetId=" + securityId + ", balance="
        + new DecimalFloat(20000 + change, 2) + ", balance_change=" + new DecimalFloat(change, 2));
    assertMessages();

    assertPositions(user, positions[0], positions[1], positions[2]);
    assertAutoLiquidationState(user, 0, 0);

    preOrderCheck.updateRisk(user, null);
    assertPositions(user, positions[0], positions[1], positions[2]);
    assertAutoLiquidationState(user, 0, 0);
    assertAutoLiquidationNotTriggered(user);
  }

  // Move asset position from positive to a smaller positive when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedAssetBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, false, -10000, 10000, 20000, 20000);
  }

  // Move USDT position from positive to a smaller positive when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedUSDTBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, false, -10000, 20000, 10000, 20000);
  }

  // Move contract position from positive to a smaller positive when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedContractBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, false, -10000, 20000, 20000, 10000);
  }

  // Move asset position from positive to zero when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroAssetBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, false, -20000, 0, 20000, 20000);
  }

  // Move USDT position from positive to zero when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroUSDTBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, false, -20000, 20000, 0, 20000);
  }

  // Move contract position from positive to zero when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroContractBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, false, -20000, 20000, 20000, 0);
  }

  // Move asset position from positive to a smaller positive when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedAssetBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, true, -10000, 10000, 20000, 20000);
  }

  // Move USDT position from positive to a smaller positive when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedUSDTBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, true, -10000, 20000, 10000, 20000);
  }

  // Move contract position from positive to a smaller positive when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedContractBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, true, -10000, 20000, 20000, 10000);
  }

  // Move asset position from positive to zero when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroAssetBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, true, -20000, 0, 20000, 20000);
  }

  // Move USDT position from positive to zero when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroUSDTBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, true, -20000, 20000, 0, 20000);
  }

  // Move contract position from positive to zero when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroContractBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, true, -20000, 20000, 20000, 0);
  }

  public void autoLiquidationTriggeredDueToNegativeAssetBalance(final boolean openOrders) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    if (openOrders) {
      orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 495000, 20000, Side.BUY, DAY));
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, ordStatus=NEW");
      assertMessages();

      assertOpenOrders(user, BTC_USDT_F, 4950 * 200, 1);
    } else {
      assertOpenOrders(user, BTC_USDT_F, 0, 0);
    }

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC + ", balance=-400.00, balance_change=-600.00");
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    if (openOrders) {
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=CANCELED, ordStatus=CANCELED");
    }

    // assertPositions(user, -40000, 20000, 20000);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, -40000, 20000, 20000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=SELL, price=1005899, qty=20000")
        .verify(messages.get(1));

    match(messages);

    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=1005899, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=1005899, orderQty=20000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=1005899, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=1005899, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=1005899, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=1005899, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, -40000, 200445376, 0);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);
  }

  // Move asset position from positive to negative when there are no open orders
  // Assert auto liquidation is triggered, assert open order value and count
  @Test
  public void autoLiquidationTriggeredDueToNegativeAssetBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationTriggeredDueToNegativeAssetBalance(false);
  }

  // Move asset position from positive to negative when there are open orders
  // Assert auto liquidation is triggered, assert open order value and count
  @Test
  public void autoLiquidationTriggeredDueToNegativeAssetBalanceWithOpenOrders() throws Exception {
    autoLiquidationTriggeredDueToNegativeAssetBalance(true);
  }

  @Test
  public void autoLiquidationTriggeredDueToNegativeUSDTBalance2() throws Exception {
    final boolean openOrders = false;
    setLiquidationMode(false);

    User user = createUser(nextUserId++, new Balance(BTC, 0, 0, 0, 0), new Balance(USDT, 69, 0, 0, 0), new Balance(BTC_USDT_F, 2, 0, 0, 0));
    Position position = user.getPositionArr()[BTC_USDT_F];
    position.setUsdAvgCostBasisDouble(12_000);

    expectMessage("UserAdminMessage", "userId=" + user.getId());
    assertMessages();



    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 0, 6900, 200);
    assertAutoLiquidationState(user, 0, 0);

    setIndexFeedUsdMark(11_500, 1, 11_000);

    // 2 contracts x 11_000 = 22_000 notional
    // 22_000 - 24_000 cost basis = -2000
    // -2000 unrealized+69 usdt = -1931 usdvalue
    preOrderCheck.updateRisk(user, null);

    System.out.println("user=" + user);
    assertEquals(-1931, user.getUsdValue(), 0.001);

    assertPositions(user, 0, 6900, 200);
    assertAutoLiquidationState(user, 0, 0);

    // value is -1931, trigger liquidation
    setLiquidationMode(true);
    preOrderCheck.updateRisk(user, null);


    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage.make("Order",
        "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=SELL, price=1203729, qty=200, qty_scale=2")
        .verify(messages.get(1));

    // sell 2 contracts at 12_075.50
    // net = 2x(12_075.50 - 12_000) = +151.00 minus 120.755 fees = +30.245
    // usdt = 69 + 30.25 = 99.25


    match(messages);

    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=1203729, orderQty=200, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=1203729, orderQty=200, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=1203729, orderQty=200, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=1203729, orderQty=200, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=1203729, orderQty=200, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=1203729, orderQty=200, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 0, 5331, 0);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);
  }

  @Test
  public void autoLiquidationTriggeredDueToNegativeUSDTBalance3() throws Exception {
    final boolean openOrders = false;
    setLiquidationMode(false);

    User user =
        createUser(nextUserId++, new Balance(BTC, 0, 0, 0, 0), new Balance(USDT, 69, 0, 0, 0), new Balance(BTC_USDT_F, -2, 0, 0, 0));
    Position position = user.getPositionArr()[BTC_USDT_F];
    position.setUsdAvgCostBasisDouble(10_000);

    expectMessage("UserAdminMessage", "userId=" + user.getId());
    assertMessages();



    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 0, 6900, -200);
    assertAutoLiquidationState(user, 0, 0);

    setIndexFeedUsdMark(11_500, 1, 11_000);

    // 2 contracts x 11_000 = 22_000 notional
    // 22_000 - 24_000 cost basis = -2000
    // -2000 unrealized+69 usdt = -1931 usdvalue
    preOrderCheck.updateRisk(user, null);

    System.out.println("user=" + user);
    assertEquals(-1931, user.getUsdValue(), 0.001);

    assertPositions(user, 0, 6900, -200);
    assertAutoLiquidationState(user, 0, 0);

    // value is -1931, trigger liquidation
    setLiquidationMode(true);
    preOrderCheck.updateRisk(user, null);


    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order",
            "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=997429, qty=200, qty_scale=2")
        .verify(messages.get(1));

    // sell 2 contracts at 12_075.50
    // net = 2x(12_075.50 - 12_000) = +151.00 minus 120.755 fees = +30.245
    // usdt = 69 + 30.25 = 99.25


    match(messages);

    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=997429, orderQty=200, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=997429, orderQty=200, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=997429, orderQty=200, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + orderId
        + ", price=997429, orderQty=200, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=997429, orderQty=200, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + orderId
        + ", price=997429, orderQty=200, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 0, 4562, 0);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);
  }

  public void autoLiquidationTriggeredDueToNegativeUSDTBalance(final boolean openOrders) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    if (openOrders) {
      orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 495000, 20000, Side.BUY, DAY));
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, ordStatus=NEW");
      assertMessages();

      assertOpenOrders(user, BTC_USDT_F, 4950 * 200, 1);
    } else {
      assertOpenOrders(user, BTC_USDT_F, 0, 0);
    }

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 0, -1000020000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + USDT + ", balance=-10000000.00, balance_change=-10000200.00");
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    if (openOrders) {
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=CANCELED, ordStatus=CANCELED");
    }

    // assertPositions(user, 20000, -1000000000, 20000);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, -1000000000, 20000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=SELL, price=4527000, qty=20000")
        .verify(messages.get(1));

    match(messages);

    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=4527000, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=4527000, orderQty=20000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=4527000, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=4527000, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=4527000, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=4527000, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -97995250, 0);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);
  }

  // Move USDT position from positive to negative when there are no open orders
  // Assert auto liquidation is triggered, assert open order value and count
  @Test
  public void autoLiquidationTriggeredDueToNegativeUSDTBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationTriggeredDueToNegativeUSDTBalance(false);
  }

  // Move USDT position from positive to negative when there are no open orders
  // Assert auto liquidation is triggered, assert open order value and count
  @Test
  public void autoLiquidationTriggeredDueToNegativeUSDTBalanceWithOpenOrders() throws Exception {
    autoLiquidationTriggeredDueToNegativeUSDTBalance(true);
  }

  private void autoLiquidationTriggeredDueToNegativeContractBalance(final boolean openOrders) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    if (openOrders) {
      orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 495000, 20000, Side.BUY, DAY));
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, ordStatus=NEW");
      assertMessages();

      assertOpenOrders(user, BTC_USDT_F, 4950 * 200, 1);
    } else {
      assertOpenOrders(user, BTC_USDT_F, 0, 0);
    }

    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    if (openOrders) {
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=CANCELED, ordStatus=CANCELED");
    }

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));

    match(messages);

    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + orderId
        + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + orderId
        + ", price=248549, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -99772423, 0);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);
  }

  // Move contract position from positive to negative when there are no open orders
  // Assert auto liquidation is triggered, assert open order value and count
  @Test
  public void autoLiquidationTriggeredDueToNegativeContractBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationTriggeredDueToNegativeContractBalance(false);
  }

  // Move contract position from positive to negative when there are open orders
  // Assert auto liquidation is triggered, assert open order value and count
  @Test
  public void autoLiquidationTriggeredDueToNegativeContractBalanceWithOpenOrders() throws Exception {
    autoLiquidationTriggeredDueToNegativeContractBalance(true);
  }

  @Test
  public void autoLiquidateWithOpenOrders() throws Exception {
    User user = nextUser();
    User liquidationProvider = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(liquidationProvider, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    orderBook.addOrder(createOrder(0, liquidationProvider, BTC_USDT_F, 495000 / 3, 40000, Side.SELL, DAY));
    expectMessage("ExecutionReport", "account=" + liquidationProvider.getId() + ", orderId=0, securityId=" + BTC_USDT_F
        + ", symbol=BTC/USDT[F], ordType=LIMIT, side=SELL, price=165000, orderQty=40000, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(liquidationProvider, BTC_USDT_F, 4950 * 400 / 3, 1);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    match(messages);
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + orderId() + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport",
        "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId()
            + ", price=248549, orderQty=4000, execType=TRADE, ordStatus=FILLED, basePositionQuantity=0, basePositionQuantityChange=40000");
    expectMessage("ExecutionReport", "account=" + liquidationProvider.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=ClOrdId, side=SELL, orderId=0, price=165000, orderQty=4000, execType=TRADE, ordStatus=FILLED, basePositionQuantity=0, basePositionQuantityChange=-20000");
    expectMessage("ExecutionReport", "account=" + liquidationProvider.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=ClOrdId, side=SELL, orderId=0, price=165000, orderQty=4000, execType=TRADE, ordStatus=FILLED, basePositionQuantity=-20000, basePositionQuantityChange=-20000");
    assertMessages();

    assertPositions(user, 20000, -66227500, 0);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);
    assertOpenOrders(liquidationProvider, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(liquidationProvider, 0, 0);

    resetBalance(user);
  }

  @Test
  public void autoLiquidateWithInsuranceFund() throws Exception {
    User user = nextUser();
    long lossMargin = 1;
    User insuranceFundUser = createInsuranceFundUser(User.INSURANCE_FUND, new Balance(BTC, 0, 0, 0, 0),
        new Balance(USDT, (3 * 600 * 4950) + (4 * 40000 * 4950 * lossMargin), 0, 0, 0), new Balance(BTC_USDT_F, 600, 0, 0, 0));
    expectMessage("userId=3");
    assertMessages();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    match(messages);
    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=248549, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -99772423, 0);
    assertPositions(insuranceFundUser, 0, 80190792423L, 60000 - 40000);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    resetBalance(user);
  }

  @Test
  public void autoLiquidatePartiallyWithInsuranceFund() throws Exception {
    User user = nextUser();
    long lossMargin = 1;
    User insuranceFundUser = createInsuranceFundUser(User.INSURANCE_FUND, new Balance(BTC, 0, 0, 0, 0),
        new Balance(USDT, (3 * 600 * 4950) + 20000, 0, 0, 0), new Balance(BTC_USDT_F, 600, 0, 0, 0));
    expectMessage("userId=3");
    assertMessages();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    match(messages);
    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 4) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 4) + ", price=248549, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");

    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 3) + ", price=248549, orderQty=201, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId - 2) + ", price=248549, orderQty=201, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 3) + ", price=248549, orderQty=201, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport",
        "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId - 2)
            + ", price=248549, orderQty=201, execType=CALCULATED, ordStatus=FILLED");

    expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=" + (orderId - 1));
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", orderId=" + orderId);
    expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=" + (orderId - 1));
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", orderId=" + orderId);

    assertMessages();

    assertPositions(user, 20000, -99772422, 0);
    assertPositions(insuranceFundUser, 0, 992792422, 20000);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    resetBalance(user);
  }

  @Test
  public void autoLiquidateWithCounterParty() throws Exception {
    User user = nextUser();

    User userCounterParty = nextUser();
    updateBalance(userCounterParty, UpdateType.PATCH, new Balance(BTC, 0, 0, 400, 0), new Balance(USDT, 0, 0, 400, 0),
        new Balance(BTC_USDT_F, 0, 0, 400, 0));
    expectMessage("BalanceAdminMessage",
        "userId=" + userCounterParty.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F
            + ", balance=600.00, balance_change=400, assetId=" + BTC + ", balance=600.00, balance_change=400, assetId=" + USDT
            + ", balance=600.00, balance_change=400");
    assertMessages();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterParty, BTC_USDT_F, 0, 0);
    assertPositions(userCounterParty, 60000, 60000, 60000);
    assertAutoLiquidationState(userCounterParty, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterParty, 60000, 60000, 60000);
    // assertAutoLiquidationState(userCounterParty, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterParty, 60000, 60000, 60000);
    assertAutoLiquidationState(userCounterParty, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterParty);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    UserRiskCache.reIndex(user);
    UserRiskCache.reIndex(userCounterParty);

    match(messages);
    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + userCounterParty.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=" + userCounterParty.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=248549, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -99772423, 0);
    assertPositions(userCounterParty, 60000, 99479600, 60000 - 40000);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterParty, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterParty, 0, 0);

    resetBalance(user);
    resetBalance(userCounterParty);
  }

  @Test
  public void autoLiquidateWithMultipleCounterParties() throws Exception {
    User user = nextUser();
    User userCounterPartyOne = nextUser();
    User userCounterPartyTwo = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyOne, 20000, 20000, 20000);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyTwo, 20000, 20000, 20000);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterPartyOne, 20000, 20000, 20000);
    // assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    // assertPositions(userCounterPartyTwo, 20000, 20000, 20000);
    // assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterPartyOne, 20000, 20000, 20000);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyOne);

    assertPositions(userCounterPartyTwo, 20000, 20000, 20000);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyTwo);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    UserRiskCache.reIndex(user);
    UserRiskCache.reIndex(userCounterPartyOne);
    UserRiskCache.reIndex(userCounterPartyTwo);

    match(messages);
    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 4) + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 4) + ", price=248549, orderQty=40000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 3) + ", price=248549, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId - 2)
        + ", price=248549, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 3) + ", price=248549, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId - 2)
        + ", price=248549, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId)
        + ", price=248549, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId)
        + ", price=248549, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -99772422, 0);
    assertPositions(userCounterPartyOne, 20000, 49729800, 0);
    assertPositions(userCounterPartyTwo, 20000, 49729800, 0);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    resetBalance(user);
    resetBalance(userCounterPartyOne);
    resetBalance(userCounterPartyTwo);
  }

  @Test
  public void autoLiquidateWithSingleCounterPartyWhenMultipleCounterPartyPresent() throws Exception {
    User user = nextUser();
    User userCounterPartyOne =
        nextUser(new Balance(BTC, 400, 0, 0, 0), new Balance(USDT, 400, 0, 0, 0), new Balance(BTC_USDT_F, 400, 0, 0, 0));

    User userCounterPartyTwo =
        nextUser(new Balance(BTC, 400, 0, 0, 0), new Balance(USDT, 400, 0, 0, 0), new Balance(BTC_USDT_F, 400, 0, 0, 0));

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyTwo, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    // assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    // assertPositions(userCounterPartyTwo, 40000, 40000, 40000);
    // assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyOne);

    assertPositions(userCounterPartyTwo, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyTwo);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    UserRiskCache.reIndex(user);
    UserRiskCache.reIndex(userCounterPartyOne);
    UserRiskCache.reIndex(userCounterPartyTwo);

    match(messages);
    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=40000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId)
        + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=40000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId)
        + ", price=248549, orderQty=40000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -99772423, 0);
    // assertPositions(userCounterPartyOne, 40000, 98872000, 0);
    // assertPositions(userCounterPartyTwo, 40000, 40000, 40000);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    resetBalance(userCounterPartyOne);
    resetBalance(userCounterPartyTwo);
  }

  @Test
  public void autoLiquidateWithHighRiskCounterPartyWhenMultipleCounterPartyPresent() throws Exception {
    User user = nextUser();
    User userCounterPartyOne =
        nextUser(new Balance(BTC, 400, 0, 0, 0), new Balance(USDT, 400, 0, 0, 0), new Balance(BTC_USDT_F, 400, 0, 0, 0));

    User userCounterPartyTwo =
        nextUser(new Balance(BTC, 400, 0, 0, 0), new Balance(USDT, 400, 0, 0, 0), new Balance(BTC_USDT_F, 400, 0, 0, 0));

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);
    // To set mark price for the orderbook, to give usdValue for the userCounterPartyTwo
    orderBook.addOrder(createOrder(1, userCounterPartyTwo, BTC_USDT_F, 1011, 20000, Side.BUY, DAY));
    expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=" + userCounterPartyTwo.getId()
        + ", orderId=1, ordStatus=NEW, " + "timeInForce=DAY");
    assertMessages();

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 2022.00, 1);
    assertPositions(userCounterPartyTwo, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    // assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    // assertPositions(userCounterPartyTwo, 40000, 40000, 40000);
    // assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyOne);

    assertPositions(userCounterPartyTwo, 40000, 40000, 40000);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyTwo);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    UserRiskCache.reIndex(user);
    UserRiskCache.reIndex(userCounterPartyOne);
    UserRiskCache.reIndex(userCounterPartyTwo);

    match(messages);
    User liquidationProvider = null;
    if (userCounterPartyOne.getPositionArr()[USDT].getQuantity() == 40000) {
      liquidationProvider = userCounterPartyTwo;
    } else {
      liquidationProvider = userCounterPartyOne;
    }

    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=248549, orderQty=40000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + liquidationProvider.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId) + ", price=248549, orderQty=40000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=248549, orderQty=40000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=" + liquidationProvider.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId) + ", price=248549, orderQty=40000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 20000, -99772423, 0);
    assertPositions(userCounterPartyOne, 40000, 40000, 40000);
    assertPositions(userCounterPartyTwo, 40000, 99459600, 0);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 2022.00, 1);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    resetBalance(user);
    resetBalance(userCounterPartyOne);
    resetBalance(userCounterPartyTwo);
  }

  @Test
  public void autoLiquidatePartiallyWithCounterParty() throws Exception {
    User user = nextUser();
    User userCounterParty = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterParty, BTC_USDT_F, 0, 0);
    assertPositions(userCounterParty, 20000, 20000, 20000);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 20000, 20000, -40000);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterParty, 20000, 20000, 20000);
    // assertAutoLiquidationState(userCounterParty, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 20000, 20000, -40000);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterParty, 20000, 20000, 20000);
    assertAutoLiquidationState(userCounterParty, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterParty);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=248549, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    Assert.assertTrue("Risk Buckets-\n".equals(UserRiskCache.stringValue()));

    UserRiskCache.reIndex(user);
    UserRiskCache.reIndex(userCounterParty);

    match(messages);
    long orderId = orderBook.newOrderSingleHandler().getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 4) + ", price=248549, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 4) + ", price=248549, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");

    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 3) + ", price=248549, orderQty=2000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + userCounterParty.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId - 2) + ", price=248549, orderQty=2000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 3) + ", price=248549, orderQty=2000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport",
        "account=" + userCounterParty.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + (orderId - 2)
            + ", price=248549, orderQty=2000, execType=CALCULATED, ordStatus=FILLED");

    expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=" + (orderId - 1));
    expectMessage("ExecutionReport", "account=3, orderId=" + orderId);
    expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=" + (orderId - 1));
    expectMessage("ExecutionReport", "account=3, orderId=" + orderId);
    assertMessages();

    assertPositions(user, 20000, -99772422, 0);
    assertPositions(userCounterParty, 20000, 49729800, 0);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterParty, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(userCounterParty, 0, 0);

    resetBalance(user);
    resetBalance(userCounterParty);
  }


  @Test
  public void markIntPriceCalculation() {

    double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];
    usdMarkPricesToSet[instrumentPair.getId()] = 0;
    int markIntPrice;

    markIntPrice = RiskAutoLiquidationThread.getMarkIntPrice(pair, usdMarkPricesToSet, Side.SELL);
    Assert.assertEquals(500, markIntPrice);

    instrumentPair.getOrderBook().setMark(213);
    markIntPrice = RiskAutoLiquidationThread.getMarkIntPrice(pair, usdMarkPricesToSet, Side.SELL);
    Assert.assertEquals(213, markIntPrice);

  }

  @Test
  public void markIntPriceCalculationForBuyOrderWhenAsksAndBidsGreaterThanZero() {

    double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];
    usdMarkPricesToSet[instrumentPair.getId()] = 0;
    int markIntPrice;

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    markIntPrice = RiskAutoLiquidationThread.getMarkIntPrice(pair, usdMarkPricesToSet, Side.BUY);
    Assert.assertEquals(1012, markIntPrice);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 900, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=900, orderQty=500, leavesQty=500, ordStatus=NEW");

    markIntPrice = RiskAutoLiquidationThread.getMarkIntPrice(pair, usdMarkPricesToSet, Side.BUY);
    Assert.assertEquals(900, markIntPrice);
  }

  @Test
  public void markIntPriceCalculationForSellOrderWhenAsksAndBidsGreaterThanZero() {

    double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];
    usdMarkPricesToSet[instrumentPair.getId()] = 0;
    int markIntPrice;


    orderBook.addOrder(createOrder(1, user, pair.getId(), 900, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=900, orderQty=500, leavesQty=500, ordStatus=NEW");
    markIntPrice = RiskAutoLiquidationThread.getMarkIntPrice(pair, usdMarkPricesToSet, Side.SELL);
    Assert.assertEquals(900, markIntPrice);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    markIntPrice = RiskAutoLiquidationThread.getMarkIntPrice(pair, usdMarkPricesToSet, Side.SELL);
    Assert.assertEquals(1012, markIntPrice);

  }


  /*
   * @Test public void testALTriggeredWithInvalidPrice() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
   * InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 0, 0));
   * instrumentPair = InstrumentCache.getPair(BTC_USDT_F); Assert.assertNotNull(instrumentPair);
   * expectMessage("securityId=12, symbol=BTC/USDT[F]"); assertMessages();
   *
   * assertPositions(user100, 20000, 20000, 20000); assertAutoLiquidationState(user100, 0, 0);
   *
   * updateBalance(USER_ID_ONE, UpdateType.PUT, new Balance(BTC, 20, 0, 0, 0), new Balance(USDT, 20, 0, 0, 0), new Balance(BTC_USDT_F, 20,
   * 0, 0, 0)); expectMessage("updateType=PUT, userId=100"); assertMessages();
   *
   * instrumentPair.setIndexFeedUsdMark(10); preOrderCheck.setLIQUIDATON_MODE(true);
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0.0, 0); // validateUserOpenOrders(user100,0.0, 2,0);
   *
   * assertPositions(user100, 2000, 2000, 20); assertAutoLiquidationState(user100, 0, 0);
   *
   * updateBalance(USER_ID_ONE, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -30, 0)); expectMessage("updateType=PATCH, userId=100");
   * assertMessages();
   *
   * assertPositions(user100, 2000, 2000, -10); assertAutoLiquidationState(user100, 0, 0);
   *
   * preOrderCheck.updateRisk(user100, null);
   *
   * assertPositions(user100, 2000, 2000, -10); assertAutoLiquidationState(user100, 1, 0);
   *
   * Assert.assertEquals(1, riskToAutoLiquidatorQueue.size()); User user100Liq = riskToAutoLiquidatorQueue.poll(); ArrayList<Order>
   * autoLiquidatedOrders = new ArrayList<>(); autoLiquidateMethod.invoke(riskAutoLiquidationThread, user100Liq, autoLiquidatedOrders);
   *
   * ArrayList<Message> messages = new ArrayList<>(); riskToMatcherQueue.drainTo(messages, 1_000);
   *
   * Assert.assertEquals(2, messages.size()); Assert.assertEquals(MessageType.MASS_CANCEL_ORDER, messages.get(0).getMessageType());
   * Assert.assertEquals(MessageType.LIQUIDATION_ORDER, messages.get(1).getMessageType());
   *
   * MassCancelOrder massCancelOrder = (MassCancelOrder) messages.get(0); Assert.assertEquals(USER_ID_ONE, massCancelOrder.getUserId());
   *
   * massCancelOrder.onMatcher();
   *
   * LiquidationOrder liquidationOrder = (LiquidationOrder) messages.get(1); Assert.assertEquals("autoclose",
   * String.valueOf(liquidationOrder.getClOrdId())); Assert.assertEquals(Side.BUY, liquidationOrder.getSide()); Assert.assertEquals(1,
   * liquidationOrder.getPriceInt()); Assert.assertEquals(1, liquidationOrder.getPrice()); Assert.assertEquals(10,
   * liquidationOrder.getQty());
   *
   * liquidationOrder.onMatcher(); long orderId = orderBook.newOrderSingleHandler().getOrderId();
   * expectMessage("securityId=12, clOrdId=autoclose, account=100, execType=NEW, ordStatus=NEW"); // orderId=1,
   * expectMessage("securityId=12, clOrdId=autoclose, account=100, execType=EXPIRED, ordStatus=EXPIRED"); // orderId=1,
   * expectMessage("securityId=12, clOrdId=autoclose, account=100, execType=NEW, ordStatus=NEW"); // orderId=2,
   * expectMessage("securityId=12, clOrdId=autoclose, account=3, execType=NEW, ordStatus=NEW"); // orderId=3,
   * expectMessage("securityId=12, clOrdId=autoclose, account=100, execType=CALCULATED, ordStatus=FILLED"); // orderId=2,
   * expectMessage("securityId=12, clOrdId=autoclose, account=3, execType=CALCULATED, ordStatus=FILLED"); // orderId=3, assertMessages();
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0.0, 0); // validateUserOpenOrders(user100,0.0,2, 0);
   *
   * assertPositions(user100, 2010, 2000, -10); assertAutoLiquidationState(user100, 1, 0); }
   *
   * @Test public void testALTriggeredDueToNegativeBalance() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
   * { preOrderCheck.setLIQUIDATON_MODE(true);
   *
   * assertOpenOrders(user100, BTC_USDT_F, 0, 0); // assertOpenOrders(user100, 0, 0); assertPositions(user100, 20000, 20000, 20000);
   * assertAutoLiquidationState(user100, 0, 0);
   *
   * orderBook.addOrder(createOrder(1, user100, BTC_USDT_F, 1011, 20000, Side.BUY, DAY));
   * expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=100, orderId=1, ordStatus=NEW, " +
   * "timeInForce=DAY"); assertMessages();
   *
   * assertPositions(user100, 20000, 20000, 20000); assertAutoLiquidationState(user100, 0, 0);
   *
   * updateBalance(USER_ID_ONE, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2));
   *
   * assertPositions(user100, 20000, 20000, -40000); assertAutoLiquidationState(user100, 0, 0);
   *
   * preOrderCheck.updateRisk(user100, null);
   *
   * Assert.assertEquals(1, riskToAutoLiquidatorQueue.size()); User user100Liq = riskToAutoLiquidatorQueue.poll();
   *
   * assertPositions(user100, 20000, 20000, -40000); assertAutoLiquidationState(user100, 1, 0);
   *
   * ArrayList<Order> autoLiquidatedOrders = new ArrayList<>(); autoLiquidateMethod.invoke(riskAutoLiquidationThread, user100Liq,
   * autoLiquidatedOrders);
   *
   * ArrayList<Message> messages = new ArrayList<>(); riskToMatcherQueue.drainTo(messages, 1_000);
   *
   * Assert.assertEquals(2, messages.size());
   *
   * Assert.assertEquals(MessageType.MASS_CANCEL_ORDER, messages.get(0).getMessageType()); MassCancelOrder massCancelOrder =
   * (MassCancelOrder) messages.get(0); massCancelOrder.onMatcher(); expectMessage("updateType=PATCH, userId=100");
   * expectMessage("side=BUY, ordType=LIMIT, account=100, orderId=1, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL,");
   * expectMessage("side=BUY, ordType=LIMIT, account=100, orderId=1, execType=CANCELED, ordStatus=CANCELED,"); assertMessages();
   * Assert.assertEquals(USER_ID_ONE, massCancelOrder.getUserId());
   *
   * Assert.assertEquals(MessageType.LIQUIDATION_ORDER, messages.get(1).getMessageType()); LiquidationOrder liquidationOrder =
   * (LiquidationOrder) messages.get(1); liquidationOrder.onMatcher(); expectMessage("execType=NEW"); expectMessage("execType=EXPIRED");
   * assertMessages(); Assert.assertEquals("autoclose", String.valueOf(liquidationOrder.getClOrdId()));
   *
   * Assert.assertEquals(Side.BUY, liquidationOrder.getSide()); assertPositions(user100, 20000, 20000, -40000);
   * assertAutoLiquidationState(user100, 0, 0);
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0.0, 0); // validateUserOpenOrders(user100,0.0, 2,-1);//TODO incorrect openOrderCount
   * }
   *
   * @Test public void testALTriggeredWhenOpenOrdersExist() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
   * { preOrderCheck.setLIQUIDATON_MODE(true);
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0, 0); // validateUserOpenOrders(user100,0, 2,-1);//TODO incorrect openOrderCount
   *
   * assertPositions(user100, 20000, 20000, 20000); assertAutoLiquidationState(user100, 0, 0);
   *
   * orderBook.addOrder(createOrder(1, user100, BTC_USDT_F, 1011, 202000, Side.BUY, DAY));
   * expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=100, orderId=1, ordStatus=NEW, " +
   * "timeInForce=DAY"); assertMessages();
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 20422.20, 1); // validateUserOpenOrders(user100,0.0, 2,1);
   *
   * Assert.assertEquals(1, riskToAutoLiquidatorQueue.size()); User user100Liq = riskToAutoLiquidatorQueue.poll();
   *
   * assertPositions(user100Liq, 20000, 20000, 20000); assertAutoLiquidationState(user100Liq, 1, 0);
   *
   * ArrayList<Order> autoLiquidatedOrders = new ArrayList<>(); autoLiquidateMethod.invoke(riskAutoLiquidationThread, user100,
   * autoLiquidatedOrders);
   *
   * ArrayList<Message> messages = new ArrayList<>(); riskToMatcherQueue.drainTo(messages, 1_000);
   *
   * Assert.assertEquals(2, messages.size());
   *
   * Assert.assertEquals(MessageType.MASS_CANCEL_ORDER, messages.get(0).getMessageType()); MassCancelOrder massCancelOrder =
   * (MassCancelOrder) messages.get(0); massCancelOrder.onMatcher(); expectMessage("execType=PENDING_CANCEL");
   * expectMessage("execType=CANCELED"); assertMessages(); Assert.assertEquals(USER_ID_ONE, massCancelOrder.getUserId());
   *
   * Assert.assertEquals(MessageType.LIQUIDATION_ORDER, messages.get(1).getMessageType()); LiquidationOrder liquidationOrder =
   * (LiquidationOrder) messages.get(1); liquidationOrder.onMatcher(); expectMessage("execType=NEW"); expectMessage("execType=EXPIRED");
   * Assert.assertEquals("autoclose", String.valueOf(liquidationOrder.getClOrdId()));
   *
   * Assert.assertEquals(Side.SELL, liquidationOrder.getSide()); Assert.assertEquals(20, liquidationOrder.getPriceInt());
   * Assert.assertEquals(20, liquidationOrder.getPrice()); Assert.assertEquals(20000, liquidationOrder.getQty());
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0.0, 0); // validateUserOpenOrders(user100,0.0, 2, 0);
   *
   * assertPositions(user100Liq, 20000, 20000, 20000); assertAutoLiquidationState(user100Liq, 0, 0); }
   *
   * @Test public void testALTriggeredWhenMultipleBuyOpenOrdersExist() throws NoSuchMethodException, InvocationTargetException,
   * IllegalAccessException { preOrderCheck.setLIQUIDATON_MODE(true);
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0.0, 0); // validateUserOpenOrders(user100,0.0, 2,0); assertPositions(user100, 20000,
   * 20000, 20000); assertAutoLiquidationState(user100, 0, 0);
   *
   * orderBook.addOrder(createOrder(1, user100, BTC_USDT_F, 1011, 1000, Side.BUY, DAY));
   * expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=100, orderId=1, ordStatus=NEW, " +
   * "timeInForce=DAY"); assertOpenOrders(user100, instrumentPair.getId(), 101.10, 1); // validateUserOpenOrders(user100,0.0, 2,1);
   *
   * orderBook.addOrder(createOrder(2, user100, BTC_USDT_F, 1011, 1000, Side.BUY, DAY));
   * expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=100, orderId=2, ordStatus=NEW, " +
   * "timeInForce=DAY"); assertOpenOrders(user100, instrumentPair.getId(), 202.20, 2); // validateUserOpenOrders(user100,0.0, 2,2);
   *
   * orderBook.addOrder(createOrder(3, user100, BTC_USDT_F, 1011, 200000, Side.BUY, DAY));
   * expectMessage("securityId=12, clOrdId=ClOrdId, symbol=BTC/USDT[F], side=BUY, ordType=LIMIT, account=100, orderId=3, ordStatus=NEW, " +
   * "timeInForce=DAY"); assertOpenOrders(user100, instrumentPair.getId(), 20422.20, 3); // validateUserOpenOrders(user100,0.0, 2,3); //TODO
   * open order value is not correct
   *
   * assertMessages();
   *
   * Assert.assertEquals(1, riskToAutoLiquidatorQueue.size()); User user100Liq = riskToAutoLiquidatorQueue.poll();
   *
   * assertPositions(user100Liq, 20000, 20000, 20000); assertAutoLiquidationState(user100Liq, 1, 0);
   *
   * ArrayList<Order> autoLiquidatedOrders = new ArrayList<>(); autoLiquidateMethod.invoke(riskAutoLiquidationThread, user100,
   * autoLiquidatedOrders);
   *
   * ArrayList<Message> messages = new ArrayList<>(); riskToMatcherQueue.drainTo(messages, 1_000);
   *
   * Assert.assertEquals(2, messages.size());
   *
   * Assert.assertEquals(MessageType.MASS_CANCEL_ORDER, messages.get(0).getMessageType()); MassCancelOrder massCancelOrder =
   * (MassCancelOrder) messages.get(0); massCancelOrder.onMatcher(); expectMessage("execType=PENDING_CANCEL, orderId=3");
   * expectMessage("execType=CANCELED, orderId=3"); expectMessage("execType=PENDING_CANCEL, orderId=2");
   * expectMessage("execType=CANCELED, orderId=2"); expectMessage("execType=PENDING_CANCEL, orderId=1");
   * expectMessage("execType=CANCELED, orderId=1"); assertMessages();
   *
   * Assert.assertEquals(USER_ID_ONE, massCancelOrder.getUserId()); Assert.assertEquals(0, massCancelOrder.getSecurityId()); // Cancel all
   * orders from all the order books
   *
   * Assert.assertEquals(MessageType.LIQUIDATION_ORDER, messages.get(1).getMessageType()); LiquidationOrder liquidationOrder =
   * (LiquidationOrder) messages.get(1); liquidationOrder.onMatcher(); expectMessage("execType=NEW"); expectMessage("execType=EXPIRED");
   * assertMessages(); Assert.assertEquals("autoclose", String.valueOf(liquidationOrder.getClOrdId()));
   *
   * Assert.assertEquals(Side.SELL, liquidationOrder.getSide()); Assert.assertEquals(20, liquidationOrder.getPriceInt());
   * Assert.assertEquals(20, liquidationOrder.getPrice()); Assert.assertEquals(20000, liquidationOrder.getQty());
   *
   * assertOpenOrders(user100, instrumentPair.getId(), 0.0, 0); // validateUserOpenOrders(user100, 0.0, 0, 0); }
   */
}
