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
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kraken.KrakenFutureUserDataListener;
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
import static com.solfini.matchengine.liquidity.direct.aiGenerated.KrakenFastClientTest.*;

public class KrakenMockTradeTest {

  final static String exchange = "KRAKEN";
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenFastClientTest.class);
  private static final String WS_FUTURES_USERDATA = "wss://futures.kraken.com/ws/v1";

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
    LiquiditySubscriptionCache.onLoad(getKrakenFutureAccount());

    LiquiditySubscriptionCache.startAllSubscriptions();

    Thread.sleep(1000);
    ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, true);
    KrakenFutureUserDataListener userDataListener = new KrakenFutureUserDataListener(WS_FUTURES_USERDATA,
            getKrakenFutureAccount().getApiKey(), getKrakenFutureAccount().getApiSecret(), subscription);

    subscription.cacheNewOrder(getSampleOrder());

    userDataListener.onMessage(getFillSnapshot());
    userDataListener.onMessage(getFill());

    printCache(subscription,null);
  }

  public static Order getSampleOrder() {
    Order order = new Order();
    order.setClOrdId("1767801895873822636");
    order.setOrderId(12345);
    order.setSymbol("SOLUSDT");
    order.setSide(Side.BUY);
    order.setType(Constants.BUY_LIMIT);
    order.setOrdType(OrdType.LIMIT);
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
    order.setPrice(180_00, (short) 2);
    order.setQty(2L, (short) 1); //0.2
    order.setUser(UserCache.getTestUser());
    order.setTargetStrategy(ADL_MAKER_ONLY);
    return order;
  }

  public static String getFillSnapshot() {
    return """
        {"feed":"fills_snapshot","account":"fd17a6ed-d69c-4081-bc7b-1acf6290f2a6","fills":[{"instrument":"PF_XRPUSD","time":1766412134646,"price":1.93507,"seq":0,"buy":true,"qty":1.0,"remaining_order_qty":0.0,"order_id":"a0a77be2-3afb-4b6f-8b02-b82bbb0f1a53","fill_id":"7622e7cc-1e47-4fa6-a9d3-4d8ba8e6c704","fill_type":"takerAfterEdit","fee_paid":9.67535E-4,"fee_currency":"USD","taker_order_type":"lmt","order_type":"lmt"},{"instrument":"PF_XRPUSD","time":1767779828618,"price":2.24061,"seq":1,"buy":true,"qty":1.0,"remaining_order_qty":0.0,"order_id":"a0c754df-6248-4d0b-810e-19a9f3a8d67d","cli_ord_id":"1767779828367678891","fill_id":"025e4485-4c08-44af-8799-3de264383622","fill_type":"taker","fee_paid":0.001120305,"fee_currency":"USD","taker_order_type":"lmt","order_type":"lmt"},{"instrument":"PF_XRPUSD","time":1767779982438,"price":2.24,"seq":2,"buy":true,"qty":1.0,"remaining_order_qty":0.0,"order_id":"a0c75566-e950-4e78-a949-5991427b4d22","cli_ord_id":"1767779917272742543","fill_id":"3698ec4e-3deb-42b1-8f44-803aa55bf941","fill_type":"maker","fee_paid":4.48E-4,"fee_currency":"USD","taker_order_type":"ioc","order_type":"lmt"}]}
        """;
  }

  public static String getFill() {
    return """
            {"feed":"fills","username":"fd17a6ed-d69c-4081-bc7b-1acf6290f2a6","fills":[{"instrument":"PF_XRPUSD","time":1767802161979,"price":2.21,"seq":3,"buy":false,"qty":1.0,"remaining_order_qty":0.0,"order_id":"a0c7d86f-1ff8-4ce9-bdc5-4e9e8fb9551a","cli_ord_id":"1767801895873822636","fill_id":"fe811159-29d5-405a-9d64-f38c5921cc63","fill_type":"maker","fee_paid":4.42E-4,"fee_currency":"USD","taker_order_type":"market","order_type":"lmt"}]}
            """;
  }


}
