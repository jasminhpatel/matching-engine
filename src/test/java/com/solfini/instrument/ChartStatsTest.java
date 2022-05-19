package com.solfini.instrument;

import org.junit.Assert;
import org.junit.Test;

import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.stats.TradeHistory;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class ChartStatsTest extends OrderBookTest {

  @Test
  public void testChartData() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USD[F]", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDC", BTC, USDT, 2, 8, CASH_PREORDER_CHECK));

    InstrumentCache
        .updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_DF, UpdateType.PUT, "BTC/USD[DF]Jun26", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_CALL_6000, UpdateType.PUT, "BTC/USDT[C]Apr24_6000", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_PUT_6000, UpdateType.PUT, "BTC/USDT[P]Apr24_6000", BTC, USDT, 2, 8));

    InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDT);
    InstrumentPair futurePair = InstrumentCache.getPair(BTC_USDT_DF);
    InstrumentPair callPair = InstrumentCache.getPair(BTC_USDT_CALL_6000);
    InstrumentPair putPair = InstrumentCache.getPair(BTC_USDT_PUT_6000);

    long now = System.currentTimeMillis();
    TradeHistory tradeHistory = spotPair.getTradeHistory();
    tradeHistory.setStartTime(now - ONE_DAY);

    tradeHistory.addToChart(now - ONE_HOUR, 1170000000, 9000);
    tradeHistory.addToChart(now - 10 * ONE_MINUTE, 1150000000, 9000);
    tradeHistory.addToChart(now - 10 * ONE_MINUTE, 1135000000, 1000);
    tradeHistory.addToChart(now - ONE_MINUTE, 1140000000, 1000);
    // tradeHistory.addToChart(now-ONE_MINUTE, 1150000, 1000);

    int twap = tradeHistory.getRolling8HrTWAP();
    System.out.println("twap=" + twap);
  }

}
