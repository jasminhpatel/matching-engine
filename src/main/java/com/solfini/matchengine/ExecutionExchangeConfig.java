package com.solfini.matchengine;

public interface ExecutionExchangeConfig {
  long getId();
  String getExchange();
  String getApiUser();
  String getApiKey();
  String getApiSecret();
  boolean isFuturesEnabled();
  boolean hasLeverage();
  String getLastUsedProxy();
  void setLastUsedProxy(String lastUsedProxy);
  boolean isForceToUseProxy();
}
