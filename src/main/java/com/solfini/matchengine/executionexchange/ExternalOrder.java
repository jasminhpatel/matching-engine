package com.solfini.matchengine.executionexchange;

import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;

public interface ExternalOrder {
  String getClOrdId();
  String getExchange();
  boolean isFuturesEnabled();
  XExchange getxExchange();
  Instrument getInstrument();
  Side getSide();
  OrdType getOrdType();
  BigDecimal getxQuantity();
  BigDecimal getxPrice();
  ExecutionExchangeConfig getSubscription();
  String getExternalId();
  void setExternalId(String externalId);
  long getPrice();
  void setPrice(long price);
  short getPriceScale();
  void setPriceScale(short priceScale);
  double getOriginalAmount();
  void setOriginalAmount(double originalAmount);
  double getCumulativeAmount();
  void setCumulativeAmount(double cumulativeAmount);
  double getAveragePrice();
  void setAveragePrice(double averagePrice);
  String getStatus();
  void setStatus(String status);
  double getTradeValue();
  void setTradeValue(double tradeValue);
  String getResult();
  void setResult(String result);
  double getFee();
  void setFee(double fee);
}
