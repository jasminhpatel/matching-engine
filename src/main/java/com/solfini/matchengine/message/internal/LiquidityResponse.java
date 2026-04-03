package com.solfini.matchengine.message.internal;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.LiquidityOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.pool.LiquidityObjectPool;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.LiquidityResponseDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LiquidityResponse extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityResponse.class);
  private static final int LIQUIDITY_DEPTH_LEVELS = Context.getLiquidityDepthLevels();
  private long timestamp;
  private List<Liquidity> liquidityList;

  public void set(final LiquidityResponseDecoder decoder) {
    this.timestamp = decoder.timestamp();
    LiquidityResponseDecoder.LiquidityGroupDecoder liquidityGroupDecoder = decoder.liquidityGroup();
    liquidityList = new ArrayList<>(liquidityGroupDecoder.count());
    while (liquidityGroupDecoder.hasNext()) {
      liquidityGroupDecoder = liquidityGroupDecoder.next();

      int exchangeId = liquidityGroupDecoder.exchangeId();
      int type = liquidityGroupDecoder.type();
      int symbolId = liquidityGroupDecoder.symbolId();
      int pairId = liquidityGroupDecoder.instrumentPairId();
      double slip = liquidityGroupDecoder.slip();
      long lastUpdated = liquidityGroupDecoder.lastUpdated();
      double lastPrice = liquidityGroupDecoder.lastPrice();
      double bestBid = liquidityGroupDecoder.bestBid();
      double bestAsk = liquidityGroupDecoder.bestAsk();
      boolean maxBidDepthReached = liquidityGroupDecoder.maxBidDepthReached() == BooleanType.TRUE;
      boolean maxAskDepthReached = liquidityGroupDecoder.maxAskDepthReached() == BooleanType.TRUE;

      final Liquidity liquidity = LiquidityObjectPool.get();
      liquidity.setExchangeId(exchangeId);
      liquidity.setType(type);
      liquidity.setSymbolId(symbolId);
      liquidity.setInstrumentPairId(pairId);
      liquidity.setSlip(slip);
      liquidity.setLastUpdated(lastUpdated);
      liquidity.setLastPrice(lastPrice);
      liquidity.setBestBid(bestBid);
      liquidity.setBestAsk(bestAsk);
      liquidity.setMaxBidDepthReached(maxBidDepthReached);
      liquidity.setMaxAskDepthReached(maxAskDepthReached);

      LiquidityResponseDecoder.LiquidityGroupDecoder.BidGroupDecoder bidGroupDecoder = liquidityGroupDecoder.bidGroup();
      int count = 0;
      while (bidGroupDecoder.hasNext()) {
        bidGroupDecoder = bidGroupDecoder.next();
        double price = bidGroupDecoder.price();
        double cumQty = bidGroupDecoder.cumQty();
        if (count < liquidity.bids.length) {
          liquidity.bids[count].setPrice(price);
          liquidity.bids[count].setCumQty(cumQty);
        }
        count++;
      }
      count = 0;
      LiquidityResponseDecoder.LiquidityGroupDecoder.AskGroupDecoder askGroupDecoder = liquidityGroupDecoder.askGroup();
      while (askGroupDecoder.hasNext()) {
        askGroupDecoder = askGroupDecoder.next();
        double price = askGroupDecoder.price();
        double cumQty = askGroupDecoder.cumQty();
        if (count < liquidity.asks.length) {
          liquidity.asks[count].setPrice(price);
          liquidity.asks[count].setCumQty(cumQty);
        }
        count++;
      }
      liquidityList.add(liquidity);
    }
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.marketData;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.LIQUIDITY;
  }

  @Override
  public void onMatcher() {
    if (this.liquidityList != null) {
      for (final Liquidity liquidity : this.liquidityList) {
        final int pairId = liquidity.getInstrumentPairId();
        final InstrumentPair pair = InstrumentCache.getPair(pairId);
        if (pair != null) {
          final OrderBook orderBook = pair.getOrderBook();
          if (orderBook instanceof LiquidityOrderBook) {
            final LiquidityOrderBook liquidity2OrderBook = (LiquidityOrderBook) orderBook;
            liquidity2OrderBook.updateLiquidity(liquidity);
          }
        }
      }
    }
  }

  @Override
  public void clear() {
    if (this.liquidityList != null) {
      this.timestamp = 0;
      for (final Liquidity liquidity : this.liquidityList) {
        liquidity.clear();
      }
    }
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{").append("\"timestamp\":").append(timestamp);
    sb.append(",\"liquidityList\":[");
    if (this.liquidityList != null) {
      boolean comma = false;
      for (Liquidity liquidity : liquidityList) {
        if (comma) {
          sb.append(",");
        }
        liquidity.toJson(sb);
        comma = true;
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder sb) {
    sb.append("Liquidity{");
    sb.append("timestamp=").append(timestamp);
    sb.append(",liquidityList: [");
    if (this.liquidityList != null) {
      for (final Liquidity liquidity : this.liquidityList) {
        liquidity.appendTo(sb);
      }
    }
    sb.append("]}");
    return sb;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public List<Liquidity> getLiquidityList() {
    return liquidityList;
  }

  public void setLiquidityList(List<Liquidity> liquidityList) {
    this.liquidityList = liquidityList;
  }

  public static class Liquidity {
    private int exchangeId;//tardis exchange id
    private int type;// tardis exchange type 1=futures, 2=spot...
    private int symbolId; // tardis exchange specific symbol id
    private int instrumentPairId;// engine instrument id
    private double slip;
    private long lastUpdated;
    private double lastPrice;
    private double bestBid;
    private double bestAsk;
    private boolean maxBidDepthReached;
    private boolean maxAskDepthReached;
    private final Depth[] bids;
    private final Depth[] asks;

    protected boolean markAsReturned = false;

    public Liquidity() {
      this.bids = new Depth[LIQUIDITY_DEPTH_LEVELS];
      this.asks = new Depth[LIQUIDITY_DEPTH_LEVELS];
      for (int i = 0; i < LIQUIDITY_DEPTH_LEVELS; i++) {
        this.bids[i] = new Depth();
        this.asks[i] = new Depth();
      }
    }

    public void clear() {
      this.exchangeId = 0;
      this.type = 0;
      this.symbolId = 0;
      this.instrumentPairId = 0;
      this.slip = 0;
      this.lastUpdated = 0;
      this.lastPrice = 0;
      this.bestBid = 0;
      this.bestAsk = 0;
      this.maxBidDepthReached = false;
      this.maxAskDepthReached = false;
      for (Depth depth : bids) {
        depth.price = 0;
        depth.cumQty = 0;
      }
      for (Depth depth : asks) {
        depth.price = 0;
        depth.cumQty = 0;
      }
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass())
        return false;
      Liquidity liquidity = (Liquidity) o;
      return exchangeId == liquidity.exchangeId && symbolId == liquidity.symbolId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(exchangeId, symbolId);
    }

    public void resetMarkAsReturned() {
      markAsReturned = false;
    }

    public void markAsReturned() {
      markAsReturned = true;
    }

    public boolean isMarkAsReturned() {
      return markAsReturned;
    }


    public int getExchangeId() {
      return exchangeId;
    }

    public void setExchangeId(int exchangeId) {
      this.exchangeId = exchangeId;
    }

    public int getType() {
      return type;
    }

    public void setType(int type) {
      this.type = type;
    }

    public int getSymbolId() {
      return symbolId;
    }

    public void setSymbolId(int symbolId) {
      this.symbolId = symbolId;
    }

    public int getInstrumentPairId() {
      return instrumentPairId;
    }

    public void setInstrumentPairId(int instrumentPairId) {
      this.instrumentPairId = instrumentPairId;
    }

    public double getSlip() {
      return slip;
    }

    public void setSlip(double slip) {
      this.slip = slip;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }

    public double getLastPrice() {
      return lastPrice;
    }

    public void setLastPrice(double lastPrice) {
      this.lastPrice = lastPrice;
    }

    public double getBestBid() {
      return bestBid;
    }

    public void setBestBid(double bestBid) {
      this.bestBid = bestBid;
    }

    public double getBestAsk() {
      return bestAsk;
    }

    public void setBestAsk(double bestAsk) {
      this.bestAsk = bestAsk;
    }

    public boolean isMaxBidDepthReached() {
      return maxBidDepthReached;
    }

    public void setMaxBidDepthReached(boolean maxBidDepthReached) {
      this.maxBidDepthReached = maxBidDepthReached;
    }

    public boolean isMaxAskDepthReached() {
      return maxAskDepthReached;
    }

    public void setMaxAskDepthReached(boolean maxAskDepthReached) {
      this.maxAskDepthReached = maxAskDepthReached;
    }

    public Depth[] getBids() {
      return bids;
    }

    public Depth[] getAsks() {
      return asks;
    }

    public StringBuilder appendTo(StringBuilder sb) {
      sb.append("Liquidity{");
      sb.append("exchangeId=").append(exchangeId);
      sb.append(", type=").append(type);
      sb.append(", symbolId=").append(symbolId);
      sb.append(", instrumentPairId=").append(instrumentPairId);
      sb.append(", slip=").append(slip);
      sb.append(", lastUpdated=").append(lastUpdated);
      sb.append(", lastPrice=").append(lastPrice);
      sb.append(", bestBid=").append(bestBid);
      sb.append(", bestAsk=").append(bestAsk);
      sb.append(", maxBidDepthReached=").append(maxBidDepthReached);
      sb.append(", maxAskDepthReached=").append(maxAskDepthReached);
      sb.append(", bids=").append(Arrays.toString(bids));
      sb.append(", asks=").append(Arrays.toString(asks));
      sb.append('}');

      return sb;
    }

    public StringBuilder toJson(StringBuilder sb) {
      if (sb == null) {
        sb = new StringBuilder();
      }
      sb.append("{");
      sb.append("\"exchangeId\":").append(exchangeId);
      sb.append(",\"type\":").append(type);
      sb.append(",\"symbolId\":").append(symbolId);
      sb.append(",\"instrumentPairId\":").append(instrumentPairId);
      sb.append(",\"slip\":").append(slip);
      sb.append(",\"lastUpdated\":").append(lastUpdated);
      sb.append(",\"lastPrice\":").append(lastPrice);
      sb.append(",\"bestBid\":").append(bestBid);
      sb.append(",\"bestAsk\":").append(bestAsk);
      sb.append(",\"maxBidDepthReached\":").append(maxBidDepthReached);
      sb.append(",\"maxAskDepthReached\":").append(maxAskDepthReached);
      boolean comma = false;
      sb.append(",\"bids\":[");
      for (Depth depth : bids) {
        if (comma) sb.append(",");
        sb.append("{\"price\":").append(depth.price).append(",\"cumQty\":").append(depth.cumQty).append("}");
        comma = true;
      }
      comma = false;
      sb.append("],\"asks\":[");
      for (Depth depth : asks) {
        if (comma) sb.append(",");
        sb.append("{\"price\":").append(depth.price).append(",\"cumQty\":").append(depth.cumQty).append("}");
        comma = true;
      }
      sb.append("]}");

      return sb;
    }
  }

  public static class Depth {
    private double price;
    private double cumQty;

    public double getPrice() {
      return price;
    }

    public void setPrice(double price) {
      this.price = price;
    }

    public double getCumQty() {
      return cumQty;
    }

    public void setCumQty(double cumQty) {
      this.cumQty = cumQty;
    }
  }
}
