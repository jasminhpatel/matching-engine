package com.solfini.matchengine.liquidity;

import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.persist.Persister;
import com.solfini.util.MbxMath;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ExchangeSubscription extends Message implements ExecutionExchangeConfig {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExchangeSubscription.class);
  public static final int CONNECTION_VIA_XACHANGE = 0;
  public static final int CONNECTION_VIA_DIRECT = 1;
  public static final int TYPE_ACCOUNT = 1;
  public static final int TYPE_TOP = 2;
  public static final int TYPE_BOTTOM = 3;

  private final ConcurrentHashMap<String, LastBalance> BALANCE_CACHE = new ConcurrentHashMap<>(); // spot assets
  private final ConcurrentHashMap<String, LastBalance> POSITION_CACHE = new ConcurrentHashMap<>(); // derivatives
  private final ConcurrentHashMap<String, Order> ORDER_CACHE = new ConcurrentHashMap<>(); // order assets
  private final ConcurrentHashMap<String, ExecutionReportMessage> EXECUTION_REPORT_CACHE = new ConcurrentHashMap<>(); // order assets
  private final AtomicLong reservedUsdcBalance = new AtomicLong();
  private final AtomicLong reservedUsdtBalance = new AtomicLong();
  private final BalanceLruCache usdcBalanceLru = new BalanceLruCache(16);
  private final BalanceLruCache usdtBalanceLru = new BalanceLruCache(16);
  private long id;
  private String exchange;
  private String apiUser;
  private String apiKey;
  private String apiKey2;
  private String apiSecret;
  private String apiSecret2;
  private String passphrase;
  private String brokerId;
  private String brokerKey;
  private String brokerName;
  private int status;
  private long created;
  private long expires;
  private long updated;
  private boolean futuresEnabled;
  private boolean hasLeverage;
  private String lastUsedProxy;
  private volatile double usdcBalance;
  private volatile double usdtBalance;
  private volatile double usdcBuyingPower;
  private volatile double usdtBuyingPower;
  private volatile double usdcOpenNotional;
  private volatile double usdtOpenNotional;
  private volatile double leverage;
  private volatile long updatedRiskTimestamp;
  private volatile long lastUpdatedTime;

  // MP copy trade
  private int userId;
  private String platform;
  private String[] accountIds;
  private int percentage;
  private long maxAmount;
  private String preferredQuoteCurrency;
  private String preferredCurrencies;
  private int inverseTrade;
  private long amountWithLeverage;
  protected boolean hasPendingClose;
  private int subscriptionType = TYPE_ACCOUNT;
  private int connectionType = CONNECTION_VIA_XACHANGE;
  private boolean restOnly;
  private long availableMaxAmount;
  private boolean forceToUseProxy;

  private ExchangeSubscription boundSubscription;
  private ExternalExchangeClient client;

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.NULL_VAL;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.INFLUENCER_SUBSCRIPTION;
  }

  @Override
  public final void onPersist() {
    Persister.onMessage(this);
  }

  @Override
  public final void onPublish() {
    //Subscriptions are not published
  }

  public final long getId() {
    return id;
  }

  public final void setId(final long id) {
    this.id = id;
  }

  public final String getExchange() {
    return exchange;
  }

  public final void setExchange(final String exchange) {
    this.exchange = exchange;
  }

  public final String getApiUser() {
    return apiUser;
  }

  public final void setApiUser(final String apiUser) {
    this.apiUser = apiUser;
  }

  public final String getApiKey() {
    return apiKey;
  }

  public final void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public final String getApiSecret() {
    return apiSecret;
  }

  public final void setApiSecret(final String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public final String getApiKey2() {
    return apiKey2;
  }

  public final void setApiKey2(final String apiKey2) {
    this.apiKey2 = apiKey2;
  }

  public final String getApiSecret2() {
    return apiSecret2;
  }

  public final void setApiSecret2(final String apiSecret2) {
    this.apiSecret2 = apiSecret2;
  }

  public final String getPassphrase() {
    return passphrase;
  }

  public final void setPassphrase(final String passphrase) {
    this.passphrase = passphrase;
  }

  public final String getBrokerId() {
    return brokerId;
  }

  public final void setBrokerId(final String brokerId) {
    this.brokerId = brokerId;
  }

    public final String getBrokerKey() {
        return brokerKey;
    }

    public final void setBrokerKey(final String brokerKey) {
        this.brokerKey = brokerKey;
    }

    public final String getBrokerName() {
        return brokerName;
    }

    public final void setBrokerName(final String brokerName) {
        this.brokerName = brokerName;
    }

  public final int getStatus() {
    return status;
  }

  public final void setStatus(final int status) {
    this.status = status;
  }

  public final long getCreated() {
    return created;
  }

  public final void setCreated(final long created) {
    this.created = created;
  }

  public final long getExpires() {
    return expires;
  }

  public final void setExpires(final long expires) {
    this.expires = expires;
  }

  public final long getUpdated() {
    return updated;
  }

  public final void setUpdated(final long updated) {
    this.updated = updated;
  }

  public final boolean isFuturesEnabled() {
    return futuresEnabled;
  }

  public final void setFuturesEnabled(final boolean futuresEnabled) {
    this.futuresEnabled = futuresEnabled;
  }

  public final boolean hasLeverage() {
    return hasLeverage;
  }

  public final void setLeverage(final boolean hasLeverage) {
    this.hasLeverage = hasLeverage;
  }

  public final String getLastUsedProxy() {
    return lastUsedProxy;
  }

  public final void setLastUsedProxy(final String lastUsedProxy) {
    this.lastUsedProxy = lastUsedProxy;
  }

  public boolean isTopBottom() {
    return (subscriptionType == TYPE_TOP || subscriptionType == TYPE_BOTTOM);
  }

  public final ConcurrentHashMap<String, LastBalance> getBalanceCache() {
    return BALANCE_CACHE;
  }

  public final ConcurrentHashMap<String, LastBalance> getPositionCache() {
    return POSITION_CACHE;
  }

  public Order getOrder(String clOrId) {
    LOGGER.info(LOG_FMT_6, "SubscriptionId: ", id, " clOrdId: ", clOrId, " cacheSize: ",
        ORDER_CACHE.size());
    return ORDER_CACHE.get(clOrId);
  }

  public ConcurrentHashMap<String, Order> getOrders() {
    LOGGER.info(LOG_FMT_6, "SubscriptionId: ", id, " cacheSize: ", ORDER_CACHE.size());
    return ORDER_CACHE;
  }

  public ConcurrentHashMap<String, ExecutionReportMessage> getEXECUTION_REPORT_CACHE() {
    return EXECUTION_REPORT_CACHE;
  }

  public ExecutionReportMessage getExecutionReport(final String clordId) {
    return EXECUTION_REPORT_CACHE.get(clordId);
  }

  public void updateExecutionReport(final ExecutionReportMessage message) {
    EXECUTION_REPORT_CACHE.put(message.getClOrdId(), message);
  }

  public final double getUsdcBalance() {
    return usdcBalance;
  }

  public final void setUsdcBalance(final double usdcBalance) {
    this.usdcBalance = usdcBalance;
  }

  public final double getUsdtBalance() {
    return usdtBalance;
  }

  public final void setUsdtBalance(final double usdtBalance) {
    this.usdtBalance = usdtBalance;
  }

  public final double getUsdcBuyingPower() {
    return usdcBuyingPower;
  }

  public final void setUsdcBuyingPower(final double usdcBuyingPower) {
    this.usdcBuyingPower = usdcBuyingPower;
  }

  public final double getUsdtBuyingPower() {
    return usdtBuyingPower;
  }

  public final void setUsdtBuyingPower(final double usdtBuyingPower) {
    this.usdtBuyingPower = usdtBuyingPower;
  }

  public final double getUsdcOpenNotional() {
    return usdcOpenNotional;
  }

  public final void setUsdcOpenNotional(final double usdcOpenNotional) {
    this.usdcOpenNotional = usdcOpenNotional;
  }

  public final double getUsdtOpenNotional() {
    return usdtOpenNotional;
  }

  public final void setUsdtOpenNotional(final double usdtOpenNotional) {
    this.usdtOpenNotional = usdtOpenNotional;
  }

  public final double getLeverage() {
    return leverage;
  }

  public final void setLeverage(final double leverage) {
    this.leverage = leverage;
  }

  public final long getUpdatedRiskTimestamp() {
    return updatedRiskTimestamp;
  }

  public final void setUpdatedRiskTimestamp(final long updatedRiskTimestamp) {
    this.updatedRiskTimestamp = updatedRiskTimestamp;
  }

  public ExchangeSubscription getBoundSubscription() {
    return boundSubscription;
  }

  public void setBoundSubscription(ExchangeSubscription boundSubscription) {
    this.boundSubscription = boundSubscription;
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String[] getAccountIds() {
    return accountIds;
  }

  public void setAccountIds(String[] accountIds) {
    this.accountIds = accountIds;
  }

  public int getPercentage() {
    return percentage;
  }

  public void setPercentage(int percentage) {
    this.percentage = percentage;
  }

  public long getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(long maxAmount) {
    this.maxAmount = maxAmount;
  }

  public String getPreferredQuoteCurrency() {
    return preferredQuoteCurrency;
  }

  public void setPreferredQuoteCurrency(String preferredQuoteCurrency) {
    this.preferredQuoteCurrency = preferredQuoteCurrency;
  }

  public String getPreferredCurrencies() {
    return preferredCurrencies;
  }

  public void setPreferredCurrencies(String preferredCurrencies) {
    this.preferredCurrencies = preferredCurrencies;
  }

  public int getInverseTrade() {
    return inverseTrade;
  }

  public void setInverseTrade(int inverseTrade) {
    this.inverseTrade = inverseTrade;
  }

  public long getAmountWithLeverage() {
    return amountWithLeverage;
  }

  public void setAmountWithLeverage(long amountWithLeverage) {
    this.amountWithLeverage = amountWithLeverage;
  }

  public boolean isHasPendingClose() {
    return hasPendingClose;
  }

  public void setHasPendingClose(boolean hasPendingClose) {
    this.hasPendingClose = hasPendingClose;
  }

  public int getSubscriptionType() {
    return subscriptionType;
  }

  public void setSubscriptionType(int subscriptionType) {
    this.subscriptionType = subscriptionType;
  }

  public int getConnectionType() {
    return connectionType;
  }

  public void setConnectionType(int connectionType) {
    this.connectionType = connectionType;
  }

  public boolean isRestOnly() {
    return restOnly;
  }

  public void setRestOnly(boolean restOnly) {
    this.restOnly = restOnly;
  }

  public ExternalExchangeClient getClient() {
    return client;
  }

  public void setClient(ExternalExchangeClient client) {
    this.client = client;
  }

  public long getAvailableMaxAmount() {
    return availableMaxAmount;
  }

  public void setAvailableMaxAmount(long availableMaxAmount) {
    this.availableMaxAmount = availableMaxAmount;
  }

  public boolean isForceToUseProxy() {
    return forceToUseProxy;
  }

  public void setForceToUseProxy(boolean forceToUseProxy) {
    this.forceToUseProxy = forceToUseProxy;
  }

  public final void updateBalance(final String symbol, final double balance) {
    //LOGGER.info(LOG_FMT_6, "updateBalance - symbol: ", exchange, "-", symbol, " balance: ", balance);
    switch (symbol) {
      case "USDT":
        usdtBalance = balance;
        break;
      case "USDC":
        usdcBalance = balance;
        break;
      default:
        final LastBalance lastBalance = BALANCE_CACHE.get(symbol);
        if (lastBalance != null) {
          lastBalance.setQuantity(balance);
        } else {
          BALANCE_CACHE.put(symbol, new LastBalance(symbol, balance));
        }
    }
  }

  // TODO need to implement. imp: we intenrnally use long for holding the values
  public final void updateOrder(final String clOrId, final String orderStatus) {
    LOGGER.info(LOG_FMT_6, "SubscriptionId: ", id, " clOrdId: ", clOrId, " orderStatus: ",
        orderStatus);
    final Order exsitingOrder = ORDER_CACHE.get(clOrId);
    if ("FILLED".equalsIgnoreCase(orderStatus)) {
      exsitingOrder.setExecuted(true);
    } else if ("CANCELED".equalsIgnoreCase(orderStatus) || "REJECTED".equalsIgnoreCase(orderStatus)
        || "EXPIRED".equalsIgnoreCase(orderStatus) || "CANCELLED".equalsIgnoreCase(orderStatus)) {
      exsitingOrder.setRejected(true);
    }

  }

  public final void updateOrder(final String clOrId, final Order order) {
    LOGGER.info(LOG_FMT_6, "SubscriptionId: ", id, " clOrdId: ", clOrId, " cacheSize: ",
        ORDER_CACHE.size());
    final Order exsitingOrder = ORDER_CACHE.get(clOrId);
    exsitingOrder.setExecuted(order.isExecuted());
    exsitingOrder.setRejected(order.isRejected());

  }

  // TODO need to implement. imp: we intenrnally use long for holding the values
  public final void cacheNewOrder(final Order order) {
    LOGGER.info(LOG_FMT_6, "SubscriptionId: ", id, " clOrdId: ", order.getClOrdId(), " cacheSize: ",
        ORDER_CACHE.size());
    LOGGER.info("Add Order to the cache: " + order.toJSON());
    ORDER_CACHE.put(order.getClOrdId(), order);
  }


  public final void updateBalance(final String symbol, final double balance,
      final double markPrice) {
    LOGGER.info(LOG_FMT_6, "updateBalance - symbol: ", exchange, "-", symbol, " balance: ",
        balance);
    switch (symbol) {
      case "USDT":
        usdtBalance = balance;
        break;
      case "USDC":
        usdcBalance = balance;
        break;
      default:
        final LastBalance lastBalance = BALANCE_CACHE.get(symbol);
        if (lastBalance != null) {
          lastBalance.setQuantity(markPrice);
          lastBalance.setMarkPrice(markPrice);
        } else {
          BALANCE_CACHE.put(symbol, new LastBalance(symbol, balance, markPrice));
        }
    }
  }

  public final void updatePosition(final String symbol, final double balance) {
    final LastBalance lastBalance = POSITION_CACHE.get(symbol);
    if (lastBalance != null) {
      lastBalance.setQuantity(balance);
    } else {
      POSITION_CACHE.put(symbol, new LastBalance(symbol, balance));
    }
  }

  public final void updatePosition(final String symbol, final double balance,
      final double markPrice) {
    final LastBalance lastBalance = POSITION_CACHE.get(symbol);
    if (lastBalance != null) {
      lastBalance.update(balance, markPrice);
    } else {
      POSITION_CACHE.put(symbol, new LastBalance(symbol, balance, markPrice));
    }
  }

  public final void updatePosition(final String symbol, final double balance,
      final double markPrice, final double costBasis) {
    final LastBalance lastBalance = POSITION_CACHE.get(symbol);
    if (lastBalance != null) {
      lastBalance.update(balance, markPrice, costBasis);
    } else {
      POSITION_CACHE.put(symbol, new LastBalance(symbol, balance, markPrice, costBasis));
    }
  }

  public final void recalcRisk() {
    double usdcOpenNotional = 0;
    double usdtOpenNotional = 0;

    for (final LastBalance lastBalance : POSITION_CACHE.values()) {
      if (lastBalance.isUsdcQuoted()) {
        usdcOpenNotional += lastBalance.calcNotoional();
      } else if (lastBalance.isUsdtQuoted()) {
        usdtOpenNotional += lastBalance.calcNotoional();
      }
    }

    this.usdcOpenNotional = usdcOpenNotional;
    this.usdtOpenNotional = usdtOpenNotional;

    final double totalNotional = (usdcOpenNotional + usdtOpenNotional);
    final double totalCollateral = this.usdcBalance + this.usdtBalance;
    if (totalCollateral > 0) {
      this.leverage = totalNotional / totalCollateral;
    } else {
      this.leverage = 0;
    }

    // use max 3x leverage for risk calc
    this.usdcBuyingPower = Math.max((this.usdcBalance * 3) - usdcOpenNotional, 0);
    this.usdtBuyingPower = Math.max((this.usdtBalance * 3) - usdtOpenNotional, 0);

    this.updatedRiskTimestamp = System.currentTimeMillis();
  }

  public boolean reserve(final double orderValue, final Instrument instrument) {
    while (true) {
      final long scaledOrderValue = MbxMath.changeScale(orderValue, instrument.getQuantityScale());
      long scaledBalance;
      final AtomicLong reservedBalance;
      switch (instrument.getSymbol()) {
        case USDT:
          scaledBalance = MbxMath.changeScale(usdtBalance, instrument.getQuantityScale());
          reservedBalance = reservedUsdtBalance;
          break;
        case USDC:
          scaledBalance = MbxMath.changeScale(usdcBalance, instrument.getQuantityScale());
          reservedBalance = reservedUsdcBalance;
          break;
        default:
          scaledBalance = 0;
          reservedBalance = reservedUsdcBalance;
      }

      final long currentReserved = reservedBalance.get();
      final long available = scaledBalance - currentReserved;

      LOGGER.info(LOG_FMT_8, "scaledOrderValue: ", scaledOrderValue, " currentReserved: ",
          currentReserved, " scaledBalance: ",
          scaledBalance, " symbol: ", instrument.getSymbol());
      if (scaledOrderValue > available) {
        return false; // insufficient funds
      }

      final long newReserved = currentReserved + scaledOrderValue;

      if (reservedBalance.compareAndSet(currentReserved, newReserved)) {
        return true; // success
      }

      // else: retry (some other thread modified reservedUsdcBalance)
    }
  }

  public void release(final double orderValue, final Instrument instrument) {
    final long scaledOrderValue = MbxMath.changeScale(orderValue, instrument.getQuantityScale());
    AtomicLong reservedBalance = null;
    switch (instrument.getSymbol()) {
      case USDT:
        reservedBalance = reservedUsdtBalance;
        break;
      case USDC:
        reservedBalance = reservedUsdcBalance;
        break;
      default:
    }
    if (reservedBalance == null) {
      return;
    }
    while (true) {
      long currentReserved = reservedBalance.get();
      long newReserved = currentReserved - scaledOrderValue;
      LOGGER.info(LOG_FMT_6, "scaledOrderValue: ", scaledOrderValue, " currentReserved: ",
          currentReserved, " newReserved: ",
          newReserved);

      if (newReserved < 0) {
        reservedBalance.set(0L);
        //throw new IllegalStateException("Releasing more than reserved!");
      }

      if (reservedBalance.compareAndSet(currentReserved, newReserved)) {
        return; // success
      }
      // retry if CAS failed
    }
  }

  public double getBalance(final String symbol) {
    if (USDC.equalsIgnoreCase(symbol)) {
      return usdcBalance;
    } else if (USDT.equalsIgnoreCase(symbol)) {
      return usdtBalance;
    } else {
      return BALANCE_CACHE.getOrDefault(symbol.toUpperCase(),
          new LastBalance(symbol.toUpperCase(), 0D)).getQuantity();
    }
  }

  public double getPosition(final String symbol) {
    return POSITION_CACHE.getOrDefault(symbol.toUpperCase(),
        new LastBalance(symbol.toUpperCase(), 0D)).getQuantity();
  }

  public double getTotalStableCoinBalance() {
    final Instrument usdc = InstrumentCache.getBySymbol(USDC);
    final Instrument usdt = InstrumentCache.getBySymbol(USDT);
    final double usdBalance = getBalance(USD);
    final double usdcBalance = getBalance(USDC);
    final double usdtBalance = getBalance(USDT);

    return usdBalance + usdcBalance * usdc.getIndexFeedUsdMark()
        + usdtBalance * usdt.getIndexFeedUsdMark();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("id=").append(id);
    sb.append(", exchange='").append(exchange).append('\'');
    sb.append(", apiUser='").append(apiUser).append('\'');
    //sb.append(", apiKey='").append(apiKey).append('\'');
    //sb.append(", apiSecret='").append(apiSecret).append('\'');
    //sb.append(", apiSecret2='").append(apiSecret2).append('\'');
    sb.append(", status=").append(status);
    sb.append(", created=").append(created);
    sb.append(", expires=").append(expires);
    sb.append(", updated=").append(updated);
    sb.append(", futuresEnabled=").append(futuresEnabled);
    sb.append(", hasLeverage=").append(hasLeverage);
    sb.append(", lastUsedProxy='").append(lastUsedProxy).append('\'');
    sb.append('}');
    return sb.toString();
  }

  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"id\":").append(id);
    sb.append(",\"userId\":").append(userId);
    sb.append(",\"platform\":\"").append(platform).append("\"");
    if (accountIds != null && accountIds.length > 0) {
      sb.append(",\"accountId\":[");
      boolean addComma = false;
      for (String account : accountIds) {
        if (addComma) {
          sb.append(",");
        }
        addComma = true;
        sb.append("\"").append(account).append("\"");
      }
      sb.append("]");
    }
    sb.append(",\"exchange\":\"").append(exchange).append("\"");
    sb.append(",\"apiUser\":\"").append(apiUser).append("\"");
    sb.append(",\"apiKey\":\"").append(apiKey).append("\"");
    sb.append(",\"apiSecret\":\"").append(apiSecret).append("\"");
    sb.append(",\"percentage\":").append(percentage);
    sb.append(",\"maxAmount\":").append(maxAmount);
    sb.append(",\"status\":").append(status);
    sb.append(",\"created\":").append(created);
    sb.append(",\"expires\":").append(expires);
    sb.append(",\"updated\":").append(updated);
    sb.append(",\"preferredQuoteCurrency\":\"").append(preferredQuoteCurrency).append("\"");
    sb.append(",\"preferredCurrencies\":\"").append(preferredCurrencies).append("\"");
    sb.append(",\"inverseTrade\":").append(inverseTrade);
    //sb.append(",\"marginEnabled\":").append(marginEnabled);
    //sb.append(",\"futuresEnabled\":").append(futuresEnabled);
    sb.append(",\"amountWithLeverage\":").append(amountWithLeverage);
    sb.append(",\"futuresEnabled\":").append(futuresEnabled);
    sb.append(",\"availableMaxAmount\":").append(availableMaxAmount);
    sb.append('}');

    return sb.toString();
  }

  @Override
  public StringBuilder appendTo(StringBuilder s) {
    return null;
  }
}
