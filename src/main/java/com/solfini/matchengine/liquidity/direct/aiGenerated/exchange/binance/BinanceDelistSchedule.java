package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One entry of GET /sapi/v1/spot/delist-schedule: the spot symbols Binance will delist at delistTime.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BinanceDelistSchedule {
  private long delistTime;
  private List<String> symbols;

  public long getDelistTime() {
    return delistTime;
  }

  public void setDelistTime(final long delistTime) {
    this.delistTime = delistTime;
  }

  public List<String> getSymbols() {
    return symbols;
  }

  public void setSymbols(final List<String> symbols) {
    this.symbols = symbols;
  }
}
