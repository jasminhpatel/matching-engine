package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.LiquiditySubscriptionCache;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitTradeDataListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

import java.io.IOException;
import java.util.Properties;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.BybitFastClientTest.*;

public class MockTradeTest {

  final static String exchange = "BYBIT";
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitFastClientTest.class);

  private static void init() throws IOException {

    final LoggingThread loggingThread = Context.getLoggingThread();
    new Thread(loggingThread, "loggingThread").start();
    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
    properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
    properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
    PropertyReader.initialize(null, properties);

    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,
        false, 1, Sector.NOT_DEFINED);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);
    final User user = new User(100);
    UserCache.setTestUser(user);
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    init();
    LiquiditySubscriptionCache.onLoad(getFutureAccount());

    LiquiditySubscriptionCache.startAllSubscriptions();

    Thread.sleep(1000);
    ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, true);
    BybitTradeDataListener tradeDataListener = new BybitTradeDataListener(
        getFutureAccount().getApiKey(), getFutureAccount().getApiSecret(), subscription);
    BybitUserDataListener userDataListener = new BybitUserDataListener(
        getFutureAccount().getApiKey(), getFutureAccount().getApiSecret(), subscription);
    subscription.cacheNewOrder(getSampleOrder());

    tradeDataListener.onMessage(getOrderAcknowledgeJson());

    userDataListener.onMessage(getSampleExecutionJson());
    userDataListener.onMessage(getSampleOrderJson());
    userDataListener.onMessage(getWalletJson());
    userDataListener.onMessage(getPositionJson());

    printCache(subscription);
  }

  public static Order getSampleOrder() {
    Order order = new Order();
    order.setOrderId(12345);
    order.setSymbol("SOLUSDT");
    order.setSide(Side.BUY);
    order.setType(Constants.BUY_LIMIT);
    order.setOrdType(OrdType.LIMIT); //TODO set every where we use to set execution report
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill
    order.setPrice(180_00, (short) 2);
    order.setClOrdId("1759432560816495533");
    order.setQty(2L, (short) 1); //0.2
    order.setUser(UserCache.getTestUser());
    order.setTargetStrategy(ADL_MAKER_ONLY);  //TODO set every where we use to set execution report
    return order;
  }

  public static String getSampleExecutionJson() {
    return """
        {"topic":"execution","id":"505077818_SOLUSDT_227170130491","creationTime":1759432560943,"data":[{"category":"linear","symbol":"SOLUSDT","closedSize":"0","execFee":"0.0256322","execId":"3fb94d67-8482-58a5-91a4-154777511c1c","execPrice":"233.02","execQty":"0.2","execType":"Trade","execValue":"46.604","feeRate":"0.00055","tradeIv":"","markIv":"","blockTradeId":"","markPrice":"233.029","indexPrice":"","underlyingPrice":"","leavesQty":"0","orderId":"373b6927-71ff-4bde-a8a6-c3de21838f20","orderLinkId":"1759432560816495533","orderPrice":"240","orderQty":"0.2","orderType":"Limit","stopOrderType":"UNKNOWN","side":"Buy","execTime":"1759432560940","isLeverage":"0","isMaker":false,"seq":227170130491,"marketUnit":"","execPnl":"0","createType":"CreateByUser","extraFees":[{"feeCoin":"USDT","feeType":"GST","subFeeType":"IND_GST","feeRate":"0.000099","fee":"0.004613796"}],"feeCurrency":"USDT"}]}
        """;
  }

  public static String getSampleOrderJson() {
    return """
        {"topic":"order","id":"505077818_SOLUSDT_227170130491","creationTime":1759432560943,"data":[{"category":"linear","symbol":"SOLUSDT","orderId":"373b6927-71ff-4bde-a8a6-c3de21838f20","orderLinkId":"1759432560816495533","blockTradeId":"","side":"Buy","positionIdx":0,"orderStatus":"Filled","cancelType":"UNKNOWN","rejectReason":"EC_NoError","timeInForce":"FOK","isLeverage":"","price":"240","qty":"0.2","avgPrice":"233.02","leavesQty":"0","leavesValue":"0","cumExecQty":"0.2","cumExecValue":"46.604","cumExecFee":"0.0256322","orderType":"Limit","stopOrderType":"","orderIv":"","triggerPrice":"","takeProfit":"","stopLoss":"","triggerBy":"","tpTriggerBy":"","slTriggerBy":"","triggerDirection":0,"placeType":"","lastPriceOnCreated":"233.02","closeOnTrigger":false,"reduceOnly":false,"smpGroup":0,"smpType":"None","smpOrderId":"","slLimitPrice":"0","tpLimitPrice":"0","tpslMode":"UNKNOWN","createType":"CreateByUser","marketUnit":"","createdTime":"1759432560940","updatedTime":"1759432560942","feeCurrency":"","closedPnl":"0","slippageTolerance":"0","slippageToleranceType":"UNKNOWN","cumFeeDetail":{"USDT":"0.0256322"}}]}
        """;
  }


  public static String getOrderAcknowledgeJson() {
    return """
        {"reqId":"1759432560816495533","retCode":0,"retMsg":"OK","op":"order.create","data":{"orderId":"373b6927-71ff-4bde-a8a6-c3de21838f20","orderLinkId":"1759432560816495533"},"retExtInfo":{},"header":{"X-Bapi-Limit":"10","X-Bapi-Limit-Status":"9","X-Bapi-Limit-Reset-Timestamp":"1759432560940","Traceid":"505e2284d27bf2611898da734151119a","Timenow":"1759432560940"},"connId":"d2skksbfh6ke97cc05f0-6lyui"}
        """;
  }

  public static String getPositionJson() {
    return """
        {"id":"505077818_position_1759432560944","topic":"position","creationTime":1759432560944,"data":[{"positionIdx":0,"tradeMode":0,"riskId":281,"riskLimitValue":"50000","symbol":"SOLUSDT","side":"Buy","size":"0.2","entryPrice":"233.02","sessionAvgPrice":"","leverage":"10","positionValue":"46.604","positionBalance":"0","markPrice":"233.029","positionIM":"4.6876214","positionMM":"0.2602414","positionIMByMp":"4.68780139","positionMMByMp":"0.26025039","takeProfit":"0","stopLoss":"0","trailingStop":"0","unrealisedPnl":"0.0018","cumRealisedPnl":"1.24634526","curRealisedPnl":"-0.03024599","createdTime":"1758826877748","updatedTime":"1759432560942","tpslMode":"Full","liqPrice":"176.89650029","bustPrice":"","category":"linear","positionStatus":"Normal","adlRankIndicator":2,"autoAddMargin":0,"leverageSysUpdatedTime":"","mmrSysUpdatedTime":"","seq":227170130491,"isReduceOnly":false}]}
        """;
  }

  public static String getWalletJson() {
    return """
        {"id":"505077818_wallet_1759432560944","topic":"wallet","creationTime":1759432560944,"data":[{"accountIMRate":"0.4083","accountMMRate":"0.0226","accountIMRateByMp":"0.4083","accountMMRateByMp":"0.0226","totalEquity":"11.48691986","totalWalletBalance":"11.48511863","totalMarginBalance":"11.48691986","totalAvailableBalance":"6.79608275","totalPerpUPL":"0.00180123","totalInitialMargin":"4.6908371","totalMaintenanceMargin":"0.26041992","totalInitialMarginByMp":"4.69101722","totalMaintenanceMarginByMp":"0.26042892","coin":[{"coin":"USDT","equity":"11.47904524","usdValue":"11.48691986","walletBalance":"11.47724524","availableToWithdraw":"","availableToBorrow":"","borrowAmount":"0","accruedInterest":"0","totalOrderIM":"0","totalPositionIM":"4.6876214","totalPositionMM":"0.2602414","unrealisedPnl":"0.0018","cumRealisedPnl":"1.25916611","bonus":"0","collateralSwitch":true,"marginCollateral":true,"locked":"0","spotHedgingQty":"0","spotBorrow":"0"}],"accountLTV":"0","accountType":"UNIFIED"}]}
        """;
  }


}
