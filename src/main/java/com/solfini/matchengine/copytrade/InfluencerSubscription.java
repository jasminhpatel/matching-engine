package com.solfini.matchengine.copytrade;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.persist.Persister;

public class InfluencerSubscription extends Message {
  public static int TYPE_ACCOUNT = 1;
  public static int TYPE_TOP = 2;
  public static int TYPE_BOTTOM = 3;
  private long id;
  private int userId;
  private String platform;
  private String[] accountIds;
  private String exchange;
  private String apiUser;
  private String apiKey;
  private String apiSecret;
  private int percentage;
  private long maxAmount;
  private int status;
  private long created;
  private long expires;
  private long updated;
  private String preferredQuoteCurrency;
  private String preferredCurrencies;
  private int inverseTrade;
  private long amountWithLeverage;
  protected boolean hasPendingClose;
  protected boolean futuresEnabled;
  private String lastUsedProxy;
  private int subscriptionType = TYPE_ACCOUNT;

  private long availableMaxAmount;

  public boolean isTopBottom() {
    return (subscriptionType == TYPE_TOP || subscriptionType == TYPE_BOTTOM);
  }

  public long getId() {
    return id;
  }

  public void setId(final long id) {
    this.id = id;
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(final int userId) {
    this.userId = userId;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(final String platform) {
    this.platform = platform;
  }

  public String[] getAccountIds() {
    return accountIds;
  }

  public void setAccountIds(final String[] accountIds) {
    this.accountIds = accountIds;
  }

  public String getExchange() {
    return exchange;
  }

  public void setExchange(final String exchange) {
    this.exchange = exchange;
  }

  public String getApiUser() {
    return apiUser;
  }

  public void setApiUser(final String apiUser) {
    this.apiUser = apiUser;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiSecret() {
    return apiSecret;
  }

  public void setApiSecret(final String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public int getPercentage() {
    return percentage;
  }

  public void setPercentage(final int percentage) {
    this.percentage = percentage;
  }

  public long getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(final long maxAmount) {
    this.maxAmount = maxAmount;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(final int status) {
    this.status = status;
  }

  public long getCreated() {
    return created;
  }

  public void setCreated(final long created) {
    this.created = created;
  }

  public long getExpires() {
    return expires;
  }

  public void setExpires(final long expires) {
    this.expires = expires;
  }

  public long getUpdated() {
    return updated;
  }

  public void setUpdated(long updated) {
    this.updated = updated;
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

  public boolean hasLeverage() {
    return percentage > 10_000;
  }

  public boolean isFuturesEnabled() {
    return futuresEnabled;
  }

  public void setFuturesEnabled(boolean futuresEnabled) {
    this.futuresEnabled = futuresEnabled;
  }

  public String getLastUsedProxy() {
    return lastUsedProxy;
  }

  public void setLastUsedProxy(String lastUsedProxy) {
    this.lastUsedProxy = lastUsedProxy;
  }

  public int getSubscriptionType() {
    return subscriptionType;
  }

  public void setSubscriptionType(int subscriptionType) {
    this.subscriptionType = subscriptionType;
  }

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

  public boolean isHasPendingClose() {
    return hasPendingClose;
  }

  public void setHasPendingClose(boolean hasPendingClose) {
    this.hasPendingClose = hasPendingClose;
  }

  public long getAvailableMaxAmount() {
    return availableMaxAmount;
  }

  public void setAvailableMaxAmount(long availableMaxAmount) {
    this.availableMaxAmount = availableMaxAmount;
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
