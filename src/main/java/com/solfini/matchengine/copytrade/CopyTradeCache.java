package com.solfini.matchengine.copytrade;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.LOG_FMT_1;
import static com.solfini.common.Constants.ORDER_STATUS_FILLED;

public class CopyTradeCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CopyTradeCache.class);

  private static final String SELECT = "SELECT id,userid,securityid,subscriptionId,basesymbol,quotedsymbol,origClOrdId,clordid,platform,accountid,exchange,side,ordtype,timeinforce,signalpercentage,signalpercentagescale,signalprice,signalpricescale,orderqty,orderqtyscale,price,pricescale,\"result\",created,externalId,originalAmount,cumulativeAmount,status,istoclose,xquantity,xprice,kafkarecordoffset,borrowedAmount,repaid,futuresEnabled,tradeValue FROM copy_trade_state WHERE istoclose=false AND closed=false ORDER BY id asc;";
  private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, CopyTradeData>> COPY_TRADES_BY_ACCOUNT = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Long, ConcurrentHashMap<String, CopyTrade>> COPY_TRADES_BY_SUBSCRIPTION = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, CopyTrade> OPEN_COPY_TRADE_ORDERS = new ConcurrentHashMap<>();

  public static void onLoad(final CopyTrade copyTrade) {
    final String key = (copyTrade.getPlatform() + "-" + copyTrade.getAccountId()).toLowerCase();
    final ConcurrentHashMap<Long, CopyTradeData> copyTradeDataMap = COPY_TRADES_BY_ACCOUNT.computeIfAbsent(key, v -> new ConcurrentHashMap<>());
    final CopyTradeData copyTradeData = copyTradeDataMap.computeIfAbsent(copyTrade.getSubscriptionId(), v -> new CopyTradeData());

    copyTradeData.add(copyTrade);

    final ConcurrentHashMap<String, CopyTrade> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.computeIfAbsent(copyTrade.getSubscriptionId(), v -> new ConcurrentHashMap<>());
    copyTrades.put(copyTrade.getClOrdId(), copyTrade);

    if (copyTrade.isToClose() && ORDER_STATUS_FILLED.equalsIgnoreCase(copyTrade.getStatus())) {
      OPEN_COPY_TRADE_ORDERS.put(copyTrade.getClOrdId(), copyTrade);
    }
  }

  public static void onRemove(final CopyTrade copyTrade) {
    final String key = (copyTrade.getPlatform() + "-" + copyTrade.getAccountId()).toLowerCase();
    final ConcurrentHashMap<Long, CopyTradeData> copyTradeDataMap = COPY_TRADES_BY_ACCOUNT.computeIfAbsent(key, v -> new ConcurrentHashMap<>());
    final CopyTradeData copyTradeData = copyTradeDataMap.computeIfAbsent(copyTrade.getSubscriptionId(), v -> new CopyTradeData());

    copyTradeData.remove(copyTrade);

    final ConcurrentHashMap<String, CopyTrade> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.get(copyTrade.getSubscriptionId());
    if (copyTrades != null) {
      copyTrades.remove(copyTrade.getClOrdId());
    }
  }

  public static List<CopyTradeData> getAllCopyTrades() {
    List<CopyTradeData> copyTradeDataList = new ArrayList<>();
    for (ConcurrentHashMap<Long, CopyTradeData> map : COPY_TRADES_BY_ACCOUNT.values()) {
      copyTradeDataList.addAll(map.values());
    }

    return copyTradeDataList;
  }

  public static Collection<CopyTradeData> getCopyTrades(final String platform, final String accountId) {
    final String key = (platform + "-" + accountId).toLowerCase();
    final ConcurrentHashMap<Long, CopyTradeData> copyTradeDataMap = COPY_TRADES_BY_ACCOUNT.get(key);
    if (copyTradeDataMap != null) {
      return copyTradeDataMap.values();
    }

    return null;
  }

  public static Side getOpenSide(final String platform, final String accountId) {
    final String key = (platform + "-" + accountId).toLowerCase();
    int[] counts = new int[2];
    final ConcurrentHashMap<Long, CopyTradeData> copyTradeDataMap = COPY_TRADES_BY_ACCOUNT.get(key);
    if (copyTradeDataMap != null) {
      for (CopyTradeData cd : copyTradeDataMap.values()) {
        for (CopyTrade cp : cd.getCopyTrades()) {
          if (!cp.isToClose() && !cp.isClosed()) {
            if (cp.getSide() == Side.BUY) {
              counts[0] = counts[0] + 1;
            } else {
              counts[1] = counts[1] + 1;
            }
          }
        }
      }
    }
    if (counts[0] > counts[1]) {
      return Side.BUY;
    } else if (counts[0] < counts[1]) {
      return Side.SELL;
    } else {
      return null;
    }
  }

  public static double getOpenOrderValue(final long subscriptionId, final String quoteSymbol, final String baseSymbol) {
    final ConcurrentHashMap<String, CopyTrade> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.get(subscriptionId);
    if (copyTrades != null) {
      double openOrderValue = 0;
      for (CopyTrade c : copyTrades.values()) {
        if (!c.isToClose() && !c.isClosed() && c.getResult() == null && quoteSymbol.equalsIgnoreCase(c.getQuotedSymbol()) &&
            baseSymbol.equalsIgnoreCase(c.getBaseSymbol())) {
          openOrderValue += c.getTradeValue();
        }
      }
      return openOrderValue;
    }

    return 0D;
  }

  public static Collection<CopyTrade> getCopyTrades(final long subscriptionId) {
    final ConcurrentHashMap<String, CopyTrade> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.get(subscriptionId);
    if (copyTrades != null) {
      return copyTrades.values();
    }

    return null;
  }

  public static void addOpenOrder(final CopyTrade copyTrade) {
    OPEN_COPY_TRADE_ORDERS.put(copyTrade.getClOrdId(), copyTrade);
  }

  public static void removeOpenOrder(final CopyTrade copyTrade) {
    OPEN_COPY_TRADE_ORDERS.remove(copyTrade.getClOrdId());
  }

  public static Collection<CopyTrade> getAllOpenCopyTrades() {
    return OPEN_COPY_TRADE_ORDERS.values();
  }

  public static void add(final CopyTrade copyTrade) {
    onLoad(copyTrade);
  }

  public static void remove(final CopyTrade copyTrade) {
    onRemove(copyTrade);
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement userPS = conn.prepareStatement(SELECT);
        final ResultSet rs = userPS.executeQuery();) {
      while (rs.next()) {
        final CopyTrade copyTrade = parse(rs);
        onLoad(copyTrade);
        count++;
      }
      LOGGER.info(LOG_FMT_1, "CopyTradeCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      loaderCounter.decrementAndGet();
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  private static CopyTrade parse(final ResultSet rs) throws SQLException {
    final CopyTrade copyTrade = new CopyTrade();
    //copyTrade.setId(rs.getInt(1));
    copyTrade.setUserId(rs.getInt(2));
    copyTrade.setSecurityId(rs.getInt(3));
    copyTrade.setSubscriptionId(rs.getLong(4));
    copyTrade.setBaseSymbol(rs.getString(5));
    copyTrade.setQuotedSymbol(rs.getString(6));
    copyTrade.setOrigClOrdId(rs.getString(7));
    copyTrade.setClOrdId(rs.getString(8));
    copyTrade.setPlatform(rs.getString(9));
    copyTrade.setAccountId(rs.getString(10));
    copyTrade.setExchange(rs.getString(11));
    copyTrade.setSide(Side.valueOf(rs.getString(12)));
    copyTrade.setOrdType(OrdType.valueOf(rs.getString(13)));
    copyTrade.setTimeInForce(TimeInForce.valueOf(rs.getString(14)));
    copyTrade.setSignalPercentage(rs.getLong(15));
    copyTrade.setSignalPercentageScale(rs.getShort(16));
    copyTrade.setSignalPrice(rs.getLong(17));
    copyTrade.setSignalPriceScale(rs.getShort(18));
    copyTrade.setOrderQty(rs.getLong(19));
    copyTrade.setOrderQtyScale(rs.getShort(20));
    copyTrade.setPrice(rs.getLong(21));
    copyTrade.setPriceScale(rs.getShort(22));
    copyTrade.setResult(rs.getString(23));
    copyTrade.setCreated(rs.getLong(24));
    copyTrade.setExternalId(rs.getString(25));
    copyTrade.setOriginalAmount(rs.getDouble(26));
    copyTrade.setCumulativeAmount(rs.getDouble(27));
    copyTrade.setStatus(rs.getString(28));
    copyTrade.setToClose(rs.getBoolean(29));
    copyTrade.setxQuantity(rs.getString(30) != null ? new BigDecimal(rs.getString(30)) : null);
    copyTrade.setxPrice(rs.getString(31) != null ? new BigDecimal(rs.getString(31)) : null);
    copyTrade.setKafkaRecordOffset(rs.getLong(32));
    copyTrade.setClosed(false);
    copyTrade.setBorrowedAmount(rs.getDouble(33));
    copyTrade.setRepaid(rs.getBoolean(34));
    copyTrade.setFuturesEnabled(rs.getBoolean(35));
    copyTrade.setTradeValue(rs.getDouble(36));

    return copyTrade;
  }

  public static class CopyTradeData {
    private final ConcurrentSkipListSet<CopyTrade> copyTrades = new ConcurrentSkipListSet<>(Comparator.comparing(CopyTrade::getClOrdId));

    public ConcurrentSkipListSet<CopyTrade> getCopyTrades() {
      return copyTrades;
    }

    public void add(CopyTrade copyTrade) {
      this.copyTrades.add(copyTrade);
    }

    public void remove(CopyTrade copyTrade) {
      this.copyTrades.remove(copyTrade);
    }
  }

  public static void main(String[] args) {
    loadFromDB(new AtomicInteger());
  }
}
