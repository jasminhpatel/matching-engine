package com.solfini.matchengine.liquidity.direct;

import static com.solfini.matchengine.executionexchange.ExternalExchangeUtil.getStickyProxy;
import static com.solfini.matchengine.liquidity.ExchangeSubscription.CONNECTION_VIA_DIRECT;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.BinanceFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.BitgetFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.BitmartFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.BybitFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.CoinbaseFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.DeribitFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.KrakenFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.KucoinFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.MexcFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.AccountMode;

public class FastClientFactory {
  private static final boolean REST_ONLY = true;
  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public static ExternalExchangeClient createRestOnlyClient(final ExchangeSubscription externalSubscription) {
    if (externalSubscription.getExchange() == null) return null;

    if (externalSubscription.getLastUsedProxy() == null) {
      if (externalSubscription.getConnectionType() == CONNECTION_VIA_DIRECT) {
        //assign a sticky proxy per user because an exchange account can be shared among multiple subscriptions by the same user
        externalSubscription.setLastUsedProxy(getStickyProxy(externalSubscription.getUserId()));
        //to persist lastUsedProxy
        matcherToPublisherQueue.addGuaranteed(externalSubscription);
      } else {
        externalSubscription.setLastUsedProxy(getStickyProxy(1));
      }
    }


    switch (externalSubscription.getExchange().toUpperCase()) {
      case "BINANCE": {
        final BinanceFastClient client = new BinanceFastClient(externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "BYBIT": {
        final BybitFastClient client = new BybitFastClient(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "MEXC": {
        final MexcFastClient client = new MexcFastClient(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "BITGET": {
        final AccountMode accountMode = BitgetFastClient.getAccountMode(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription.getPassphrase(),
            externalSubscription.getLastUsedProxy(), externalSubscription.isForceToUseProxy());
        final String apiVersion = accountMode == AccountMode.CLASSIC ? "v2" : "v3";
        final BitgetFastClient client = new BitgetFastClient(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription.getPassphrase(),
            externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "DERIBIT": {
        final DeribitFastClient client = new DeribitFastClient(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "BITMART": {
        final BitmartFastClient client = new BitmartFastClient(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription.getPassphrase(),
            externalSubscription, REST_ONLY);

        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "KRAKEN": {
        final KrakenFastClient client = new KrakenFastClient(externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "COINBASE": {
        final CoinbaseFastClient client = new CoinbaseFastClient(externalSubscription.getApiKey(),
            externalSubscription.getApiSecret(), externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      case "KUCOIN": {
        final KucoinFastClient client = new KucoinFastClient(externalSubscription, REST_ONLY);
        externalSubscription.setClient(client);
        client.start();

        return client;
      }
      default:
        return null;
    }
  }

  public static ExchangeSubscription convert(final ExchangeSubscription influencerSubscription) {
    final ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(influencerSubscription.getId());
    subscription.setExchange(influencerSubscription.getExchange().toUpperCase());
    subscription.setApiUser(influencerSubscription.getApiUser());
    subscription.setApiKey(influencerSubscription.getApiKey());
    subscription.setApiSecret(influencerSubscription.getApiSecret());
    //subscription.setApiKey2(i.getApiKey());
    //subscription.setApiSecret2(i.getApiSecret());
    subscription.setStatus(influencerSubscription.getStatus());
    subscription.setCreated(influencerSubscription.getCreated());
    subscription.setExpires(influencerSubscription.getExpires());
    subscription.setFuturesEnabled(influencerSubscription.isFuturesEnabled());
    subscription.setLeverage(influencerSubscription.hasLeverage());
    subscription.setLastUsedProxy(influencerSubscription.getLastUsedProxy());

    return subscription;
  }

}
