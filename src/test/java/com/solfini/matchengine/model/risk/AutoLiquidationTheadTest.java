package com.solfini.matchengine.model.risk;

import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.Test;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.risk.RiskAutoLiquidationThread;

public class AutoLiquidationTheadTest extends RiskTest {


  // createAutoLiquidationLong
  @Test
  public void createAutoLiquidationLong() throws Exception {
    RiskAutoLiquidationThread autoLiquidationThread = new RiskAutoLiquidationThread(new NoOpIdleStrategy());

    final InstrumentPair instrumentPair = InstrumentCache.getPair(BTC_USDT_F);

    instrumentPair.setIndexFeedUsdMark(5000.00);

    Position position = user.setPosition(BTC_USDT_F, 10_000, null); // scale 3
    position.setUsdAvgCostBasisDouble(9000.00);
    MarginPreOrderCheckAndSettle marginPreOrderCheck = new MarginPreOrderCheckAndSettle();
    marginPreOrderCheck.updateRisk(user, null);

    final List<Order> liquidationOrders = new ArrayList<Order>();
    autoLiquidationThread.autoLiquidate(user, liquidationOrders);
  }

  // createAutoLiquidationShort
  @Test
  public void createAutoLiquidationShort() throws Exception {
    RiskAutoLiquidationThread autoLiquidationThread = new RiskAutoLiquidationThread(new NoOpIdleStrategy());

    final InstrumentPair instrumentPair = InstrumentCache.getPair(BTC_USDT_F);

    instrumentPair.setIndexFeedUsdMark(5000.00);

    // user has 10,000 usdt
    // contract pnl = 10*-3000 = -30,000
    // total pnl = -20,000
    // close at 2,970,

    Position position = user.setPosition(BTC_USDT_F, -10_00, null); // scale 2
    position.setUsdAvgCostBasisDouble(2000.00);
    MarginPreOrderCheckAndSettle marginPreOrderCheck = new MarginPreOrderCheckAndSettle();
    marginPreOrderCheck.updateRisk(user, null);

    final List<Order> liquidationOrders = new ArrayList<Order>();
    autoLiquidationThread.autoLiquidate(user, liquidationOrders); // buy at 2970_00
  }

  // createAutoLiquidationShort
  @Test
  public void createAutoLiquidationShortHugeLoss() throws Exception {
    RiskAutoLiquidationThread autoLiquidationThread = new RiskAutoLiquidationThread(new NoOpIdleStrategy());

    final InstrumentPair instrumentPair = InstrumentCache.getPair(BTC_USDT_F);

    instrumentPair.setIndexFeedUsdMark(5000.00);
    Position positionusdt = user.setPosition(USDT, -10000_00, null); // scale 2

    Position position = user.setPosition(BTC_USDT_F, -1_00, null); // scale 2
    position.setUsdAvgCostBasisDouble(2000.00);
    MarginPreOrderCheckAndSettle marginPreOrderCheck = new MarginPreOrderCheckAndSettle();
    marginPreOrderCheck.updateRisk(user, null);

    final List<Order> liquidationOrders = new ArrayList<Order>();
    autoLiquidationThread.autoLiquidate(user, liquidationOrders); // buy at 2970_00
  }
}
