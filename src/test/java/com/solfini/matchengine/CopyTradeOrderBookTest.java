package com.solfini.matchengine;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.ExternalInstrumentCache;
import com.solfini.matchengine.copytrade.InfluencerSubscription;
import com.solfini.matchengine.copytrade.InfluencerSubscriptionCache;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.CopyTradeOrderBook;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.benchmark.RateBenchmark;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class CopyTradeOrderBookTest {
  public static void main(String[] args) throws Exception {
    int modValue = 50;
    Properties properties = new Properties();
    PropertyReader.initialize(new FileInputStream(new File("/Users/rohanw/Documents/XinoTech/SolfiniOrg/solfini-matching-engine/server/config.properties")), properties);

    PoolSize.minimize(properties);
    properties.setProperty("INSTANCE_ID", "injector");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("ROUTER_THREAD_POOL_CORE_SIZE", "100");
    properties.setProperty("ROUTER_THREAD_POOL_MAX_SIZE", "100");
    properties.setProperty("QUEUE_CAPACITY", "20000");

    XExchange.SymbolStatus symbolStatus = new XExchange.SymbolStatus();
    symbolStatus.setExchange("BINANCE");
    symbolStatus.setBase("BTC");
    symbolStatus.setQuote("USDC");
    symbolStatus.setTradable(true);
    symbolStatus.setFutures(false);
    symbolStatus.setUpdated(System.currentTimeMillis());
    symbolStatus.setClosePricePercentage(2000);
    symbolStatus.setPriceScale(2);
    symbolStatus.setQtyScale(4);

    ExternalInstrumentCache.onLoad(symbolStatus.getKey(), symbolStatus);

    for (int i = 0; i < 50; i++) {
      int mod = (i % modValue) + 1;
      UserAdminMessage user = new UserAdminMessage();
      user.setUserId(mod);
      user.setUpdateType(UpdateType.PUT);
      UserCache.add(user);

      InfluencerSubscription influencerSubscription = new InfluencerSubscription();
      influencerSubscription.setId(i);
      influencerSubscription.setUserId(mod);
      influencerSubscription.setPlatform("YOUTUBE");
      influencerSubscription.setAccountIds(new String[] {"wrohanc"});
      influencerSubscription.setExchange("BINANCE");
      influencerSubscription.setPercentage(100);
      influencerSubscription.setMaxAmount(1000);
      influencerSubscription.setStatus(0);
      influencerSubscription.setExpires(System.currentTimeMillis() + 10000000);
      influencerSubscription.setPreferredQuoteCurrency("USDC");
      InfluencerSubscriptionCache.onLoad(influencerSubscription);
    }

    new Thread(() -> {
      final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
      RateBenchmark rateBenchmark = new RateBenchmark("Summary: " , 10);
      while (true) {
        try {
          final Message message = matcherToPublisherQueue.poll();
          if (message instanceof CopyTrade) {
            rateBenchmark.sample();
          }
        } catch (Exception e) {
        }
      }
    }).start();

    InstrumentPair pair = new InstrumentPair(1, "COPY_TRADE/USD", "COPY_TRADE", null, null, (short) 0, (short) 0,0, AssetType.PAIR, 0,0,0,0, Sector.NOT_DEFINED);
    InstrumentCache.addPair(pair);
    CopyTradeOrderBook orderBook = new CopyTradeOrderBook(pair, null, 1, 1);
    for (int i = 0; i < 10000; i++) {
      int mod = (i % modValue) + 1;
      Order order = new Order();
      order.setSecurityId(1);
      order.setSubmitterId(mod);
      order.setAccount(mod);
      order.setSide(Side.BUY);
      order.setOrdType(OrdType.MARKET);
      order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
      order.setUser(UserCache.get(mod));
      order.setClOrdId(String.valueOf(System.nanoTime()));
      order.setSymbol("BTC");
      order.setPlatform("YOUTUBE");
      order.setAccountId("wrohanc");
      order.setQty(1, (short) 0);
      order.setKafkaRecordOffset(i);

      orderBook.addOrder(order);
    }
    Thread.sleep(20000);
  }

}
