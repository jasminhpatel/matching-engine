package com.solfini.binance.risk;

import static org.junit.Assert.assertEquals;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.model.ExpectedMessage;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.risk.UserRiskCache;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class BinanceAutoLiquidationTest extends BinanceRiskTest {

  private void autoLiquidationNotTriggeredDueToDecreasedBalance(final int securityId, final boolean openOrders, final long change,
      final long... positions) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
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

    updateBalance(user, UpdateType.PATCH, new Balance(securityId, 0, 0, change, 2, null));
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
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, false, -10000, 100_00000000L, 200_00000000L, 200_000L);
  }

  // Move USDT position from positive to a smaller positive when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedUSDTBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, false, -10000, 200_00000000L, 100_00000000L, 200_000L);
  }

  // Move contract position from positive to a smaller positive when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedContractBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, false, -10000, 200_00000000L, 200_00000000L, 100_000L);
  }

  // Move asset position from positive to zero when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroAssetBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, false, -20000, 0, 200_00000000L, 200_000L);
  }

  // Move USDT position from positive to zero when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroUSDTBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, false, -20000, 200_00000000L, 0, 200_000L);
  }

  // Move contract position from positive to zero when there are no open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroContractBalanceWithoutOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, false, -20000, 200_00000000L, 200_00000000L, 0);
  }

  // Move asset position from positive to a smaller positive when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedAssetBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, true, -10000, 100_00000000L, 200_00000000L, 200_000L);
  }

  // Move USDT position from positive to a smaller positive when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedUSDTBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, true, -10000, 200_00000000L, 100_00000000L, 200_000L);
  }

  // Move contract position from positive to a smaller positive when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToDecreasedContractBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, true, -10000, 200_00000000L, 200_00000000L, 100_000L);
  }

  // Move asset position from positive to zero when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroAssetBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC, true, -20000, 0, 200_00000000L, 200_000L);
  }

  // Move USDT position from positive to zero when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroUSDTBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(USDT, true, -20000, 200_00000000L, 0, 200_000L);
  }

  // Move contract position from positive to zero when there are open orders
  // Assert auto liquidation is not triggered, assert open order value and count
  @Test
  public void autoLiquidationNotTriggeredDueToZeroContractBalanceWithOpenOrders() throws Exception {
    autoLiquidationNotTriggeredDueToDecreasedBalance(BTC_USDT_F, true, -20000, 200_00000000L, 200_00000000L, 0);
  }

  public void autoLiquidationTriggeredDueToNegativeAssetBalance(final boolean openOrders) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
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

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 0, -600_00, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC + ", balance=-400.00, balance_change=-600.00");
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    if (openOrders) {
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=CANCELED, ordStatus=CANCELED");
    }

    // assertPositions(user, -400_00000000L, 200_00000000L, 200_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, -400_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=SELL, price=507929, qty=20000")
        .verify(messages.get(1));

    match(messages);

    long orderId = NewOrderSingleHandler.getOrderId();
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=507929, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=507929, orderQty=20000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=507929, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=507929, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=507929, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=507929, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, -400_00000000L, 1012248_53250000L, 0);
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
    setLiquidationMode(false);

    User user = createUser(nextUserId++, new Balance(BTC, 0, 0, 0, 0, null), new Balance(USDT, 69, 0, 0, 0, null),
        new Balance(BTC_USDT_F, 2, 0, 0, 0, null));
    Position position = user.getPositionArr()[BTC_USDT_F];
    position.setUsdAvgCostBasisDouble(12_000);

    expectMessage("UserAdminMessage", "userId=" + user.getId());
    assertMessages();



    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 0, 69_00000000L, 2_000L);
    assertAutoLiquidationState(user, 0, 0);

    setIndexFeedUsdMark(11_500, 1, 11_000);

    // 2 contracts x 11_000 = 22_000 notional
    // 22_000 - 24_000 cost basis = -2000
    // -2000 unrealized+69 usdt = -1931 usdvalue
    preOrderCheck.updateRisk(user, null);

    System.out.println("user=" + user);
    assertEquals(-1931, user.getUsdValue(), 0.001);

    assertPositions(user, 0, 69_00000000L, 2_000L);
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
        "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=SELL, price=1203729, qty=200, qty_scale=3")
        .verify(messages.get(1));

    // sell 2 contracts at 12_075.50
    // net = 2x(12_075.50 - 12_000) = +151.00 minus 120.755 fees = +30.245
    // usdt = 69 + 30.25 = 99.25


    match(messages);

    long orderId = NewOrderSingleHandler.getOrderId();
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

    assertPositions(user, 0, 53_30032500L, 0);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);
  }

  public void autoLiquidationTriggeredDueToNegativeUSDTBalance(final boolean openOrders) throws Exception {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
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

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 0, -1000020000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + USDT + ", balance=-10000000.00, balance_change=-10000200.00");
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    if (openOrders) {
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=CANCELED, ordStatus=CANCELED");
    }

    // assertPositions(user, 200_00000000L, -10000000_00000000L, 200_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, -10000000_00000000L, 200_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order",
            "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=SELL, price=4029030, qty=20000")
        .verify(messages.get(1));

    match(messages);

    long orderId = NewOrderSingleHandler.getOrderId();
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=4029030, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 2) + ", price=4029030, orderQty=20000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=4029030, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=4029030, orderQty=20000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId="
        + (orderId - 1) + ", price=4029030, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId=" + orderId
        + ", price=4029030, orderQty=20000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 200_00000000L, -1972157_72500000L, 0);
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
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
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

    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");

    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());

    if (openOrders) {
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=PENDING_CANCEL, ordStatus=PENDING_CANCEL");
      expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=1, securityId=" + BTC_USDT_F
          + ", symbol=BTC/USDT[F], ordType=LIMIT, side=BUY, price=495000, orderQty=20000, execType=CANCELED, ordStatus=CANCELED");
    }

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=1, qty=40000")
        .verify(messages.get(1));

    match(messages);

    long orderId = NewOrderSingleHandler.getOrderId();
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=1, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + orderId
        + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=1, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=3, securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=SELL, orderId=" + orderId
        + ", price=1, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 200_00000000L, 195_98500000L, 0);
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
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(liquidationProvider, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    orderBook.addOrder(createOrder(0, liquidationProvider, BTC_USDT_F, 495000 / 3, 40000, Side.SELL, DAY));
    expectMessage("ExecutionReport", "account=" + liquidationProvider.getId() + ", orderId=0, securityId=" + BTC_USDT_F
        + ", symbol=BTC/USDT[F], ordType=LIMIT, side=SELL, price=165000, orderQty=40000, ordStatus=NEW");
    assertMessages();

    assertOpenOrders(liquidationProvider, BTC_USDT_F, 4950 * 400, 1);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
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

    assertPositions(user, 200_00000000L, -662275_00000000L, 0);

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
    User insuranceFundUser = createInsuranceFundUser(User.INSURANCE_FUND, new Balance(BTC, 0, 0, 0, 0, null),
        new Balance(USDT, (3 * 600 * 4950) + (4 * 40000 * 4950 * lossMargin), 0, 0, 0, null), new Balance(BTC_USDT_F, 600, 0, 0, 0, null));
    expectMessage("userId=3");
    assertMessages();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=1, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    match(messages);
    long orderId = NewOrderSingleHandler.getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=1, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=1, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=1, orderQty=4000, execType=CALCULATED, ordStatus=FILLED");
    assertMessages();

    assertPositions(user, 200_00000000L, 195_98500000L, 0);
    assertPositions(insuranceFundUser, 0, 800910004_01500000L, 200_000L);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    resetBalance(user);
  }

  @Test
  public void autoLiquidatePartiallyWithInsuranceFund() throws Exception {
    User user = nextUser();
    User insuranceFundUser = createInsuranceFundUser(User.INSURANCE_FUND, new Balance(BTC, 0, 0, 0, 0, null),
        new Balance(USDT, (3 * 600 * 4950) + 20000, 0, 0, 0, null), new Balance(BTC_USDT_F, 600, 0, 0, 0, null));
    expectMessage("userId=3");
    assertMessages();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    List<Message> messages = liquidate(user);
    Assert.assertEquals(2, messages.size());
    ExpectedMessage.make("MassCancelOrder", "userId=" + user.getId() + ", securityId=0").verify(messages.get(0));
    ExpectedMessage
        .make("Order", "account=" + user.getId() + ", clOrdId=autoclose, securityId=" + BTC_USDT_F + ", side=BUY, price=1, qty=40000")
        .verify(messages.get(1));
    assertMessages();

    match(messages);
    long orderId = NewOrderSingleHandler.getOrderId();
    expectMessage("MassCancelOrder", "securityId=0, userId=" + user.getId());
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=1, orderQty=4000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 2) + ", price=1, orderQty=4000, execType=EXPIRED, ordStatus=EXPIRED");

    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=1, orderQty=400000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=1, orderQty=400000, execType=NEW, ordStatus=NEW");
    expectMessage("ExecutionReport", "account=" + user.getId() + ", securityId=" + BTC_USDT_F + ", clOrdId=autoclose, side=BUY, orderId="
        + (orderId - 1) + ", price=1, orderQty=400000, execType=CALCULATED, ordStatus=FILLED");
    expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", securityId=" + BTC_USDT_F
        + ", clOrdId=autoclose, side=SELL, orderId=" + orderId + ", price=1, orderQty=400000, execType=CALCULATED, ordStatus=FILLED");

    // expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=" + (orderId - 1));
    // expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", orderId=" + orderId);
    // expectMessage("ExecutionReport", "account=" + user.getId() + ", orderId=" + (orderId - 1));
    // expectMessage("ExecutionReport", "account=" + insuranceFundUser.getId() + ", orderId=" + orderId);

    assertMessages();

    assertPositions(user, 200_00000000L, 195_98500000L, 0);
    assertPositions(insuranceFundUser, 0, 8930004_01500000L, 200_000L);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertAutoLiquidationState(user, 0, 0);

    resetBalance(user);
  }

  @Test
  public void autoLiquidateWithCounterParty() throws Exception {
    User user = nextUser();

    User userCounterParty = nextUser();
    updateBalance(userCounterParty, UpdateType.PATCH, new Balance(BTC, 0, 0, 400, 0, null), new Balance(USDT, 0, 0, 400, 0, null),
        new Balance(BTC_USDT_F, 0, 0, 400, 0, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + userCounterParty.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F
            + ", balance=600.00, balance_change=400, assetId=" + BTC + ", balance=600.00, balance_change=400, assetId=" + USDT
            + ", balance=600.00, balance_change=400");
    assertMessages();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterParty, BTC_USDT_F, 0, 0);
    assertPositions(userCounterParty, 600_00000000L, 600_00000000L, 600_000L);
    assertAutoLiquidationState(userCounterParty, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterParty, 600_00000000L, 600_00000000L, 600_000L);
    // assertAutoLiquidationState(userCounterParty, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterParty, 600_00000000L, 600_00000000L, 600_000L);
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
    long orderId = NewOrderSingleHandler.getOrderId();
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

    assertPositions(user, 200_00000000L, -997724_23500000L, 0);
    assertPositions(userCounterParty, 600_00000000L, 994796_00000000L, 200_000L);

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
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyOne, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyTwo, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterPartyOne, 200_00000000L, 200_00000000L, 200_000L);
    // assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    // assertPositions(userCounterPartyTwo, 200_00000000L, 200_00000000L, 200_000L);
    // assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterPartyOne, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyOne);

    assertPositions(userCounterPartyTwo, 200_00000000L, 200_00000000L, 200_000L);
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
    long orderId = NewOrderSingleHandler.getOrderId();
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

    assertPositions(user, 200_00000000L, -997724_23500000L, 0);
    assertPositions(userCounterPartyOne, 200_00000000L, 497298_00000000L, 0);
    assertPositions(userCounterPartyTwo, 200_00000000L, 497298_00000000L, 0);

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
        nextUser(new Balance(BTC, 400, 0, 0, 0, null), new Balance(USDT, 400, 0, 0, 0, null), new Balance(BTC_USDT_F, 400, 0, 0, 0, null));

    User userCounterPartyTwo =
        nextUser(new Balance(BTC, 400, 0, 0, 0, null), new Balance(USDT, 400, 0, 0, 0, null), new Balance(BTC_USDT_F, 400, 0, 0, 0, null));

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterPartyOne, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyOne, 400_00000000L, 400_00000000L, 400_000L);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);

    assertOpenOrders(userCounterPartyTwo, BTC_USDT_F, 0, 0);
    assertPositions(userCounterPartyTwo, 400_00000000L, 400_00000000L, 400_000L);
    assertAutoLiquidationState(userCounterPartyTwo, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterPartyOne, 400_00000000L, 400_00000000L, 400_000L);
    // assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    // assertPositions(userCounterPartyTwo, 400_00000000L, 400_00000000L, 400_000L);
    // assertAutoLiquidationState(userCounterPartyTwo, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterPartyOne, 400_00000000L, 400_00000000L, 400_000L);
    assertAutoLiquidationState(userCounterPartyOne, 0, 0);
    assertAutoLiquidationNotTriggered(userCounterPartyOne);

    assertPositions(userCounterPartyTwo, 400_00000000L, 400_00000000L, 400_000L);
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
    long orderId = NewOrderSingleHandler.getOrderId();
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

    assertPositions(user, 200_00000000L, -997724_23500000L, 0);
    // assertPositions(userCounterPartyOne, 40000, 98872000, 0);
    // assertPositions(userCounterPartyTwo, 400_00000000L, 400_00000000L, 400_000L);

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
  public void autoLiquidatePartiallyWithCounterParty() throws Exception {
    User user = nextUser();
    User userCounterParty = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertOpenOrders(user, BTC_USDT_F, 0, 0);
    assertPositions(user, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    assertOpenOrders(userCounterParty, BTC_USDT_F, 0, 0);
    assertPositions(userCounterParty, 200_00000000L, 200_00000000L, 200_000L);
    assertAutoLiquidationState(user, 0, 0);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, -60000, 2, null));
    expectMessage("BalanceAdminMessage",
        "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-400.00, balance_change=-600.00");
    assertMessages();

    // assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    // assertAutoLiquidationState(user, 0, 0);
    // assertPositions(userCounterParty, 200_00000000L, 200_00000000L, 200_000L);
    // assertAutoLiquidationState(userCounterParty, 0, 0);
    // preOrderCheck.updateRisk(user, null);

    assertPositions(user, 200_00000000L, 200_00000000L, -400_000L);
    assertAutoLiquidationState(user, 1, 0);
    assertAutoLiquidationTriggered(user);

    assertPositions(userCounterParty, 200_00000000L, 200_00000000L, 200_000L);
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
    long orderId = NewOrderSingleHandler.getOrderId();
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

    assertPositions(user, 200_00000000L, -997724_23500000L, 0);
    assertPositions(userCounterParty, 200_00000000L, 497298_00000000L, 0);

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
    Assert.assertEquals(1008, markIntPrice);

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
}
