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

import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.ORDER_STATUS_FILLED;

public class CopyTradeCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CopyTradeCache.class);

  private static final String SELECT = "SELECT id,userid,securityid,subscriptionId,basesymbol,quotedsymbol,origClOrdId,clordid,platform,accountid,exchange,side,ordtype,timeinforce,signalpercentage,signalpercentagescale,signalprice,signalpricescale,orderqty,orderqtyscale,price,pricescale,\"result\",created,externalId,originalAmount,cumulativeAmount,status,istoclose,xquantity,xprice,kafkarecordoffset,borrowedAmount,repaid,futuresEnabled,tradeValue FROM copy_trade_state WHERE istoclose=false AND closed=false AND status is not null ORDER BY id asc;";
  private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, CopyTradeData>> COPY_TRADES_BY_ACCOUNT = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Long, ConcurrentHashMap<String, CopyTradeOrder>> COPY_TRADES_BY_SUBSCRIPTION = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, CopyTradeOrder> OPEN_COPY_TRADE_ORDERS = new ConcurrentHashMap<>();

  public static void onLoad(final CopyTradeOrder copyTradeOrder) {
    final String key = (copyTradeOrder.getPlatform() + "-" + copyTradeOrder.getAccountId()).toLowerCase();
    final ConcurrentHashMap<Long, CopyTradeData> copyTradeDataMap = COPY_TRADES_BY_ACCOUNT.computeIfAbsent(key, v -> new ConcurrentHashMap<>());
    final CopyTradeData copyTradeData = copyTradeDataMap.computeIfAbsent(copyTradeOrder.getSubscriptionId(), v -> new CopyTradeData());

    copyTradeData.add(copyTradeOrder);

    final ConcurrentHashMap<String, CopyTradeOrder> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.computeIfAbsent(
        copyTradeOrder.getSubscriptionId(), v -> new ConcurrentHashMap<>());
    copyTrades.put(copyTradeOrder.getClOrdId(), copyTradeOrder);

    if (copyTradeOrder.isToClose() && ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
      OPEN_COPY_TRADE_ORDERS.put(copyTradeOrder.getClOrdId(), copyTradeOrder);
    }
  }

  public static void onRemove(final CopyTradeOrder copyTradeOrder) {
    final String key = (copyTradeOrder.getPlatform() + "-" + copyTradeOrder.getAccountId()).toLowerCase();
    final ConcurrentHashMap<Long, CopyTradeData> copyTradeDataMap = COPY_TRADES_BY_ACCOUNT.computeIfAbsent(key, v -> new ConcurrentHashMap<>());
    final CopyTradeData copyTradeData = copyTradeDataMap.computeIfAbsent(copyTradeOrder.getSubscriptionId(), v -> new CopyTradeData());

    copyTradeData.remove(copyTradeOrder);

    final ConcurrentHashMap<String, CopyTradeOrder> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.get(
        copyTradeOrder.getSubscriptionId());
    if (copyTrades != null) {
      copyTrades.remove(copyTradeOrder.getClOrdId());
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
        for (CopyTradeOrder cp : cd.getCopyTrades()) {
          if (!cp.isToClose() && !cp.isClosed() && cp.getStatus() != null) {
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
    final ConcurrentHashMap<String, CopyTradeOrder> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.get(subscriptionId);
    if (copyTrades != null) {
      double openOrderValue = 0;
      for (CopyTradeOrder c : copyTrades.values()) {
        if (!c.isToClose() && !c.isClosed() && c.getResult() == null && quoteSymbol.equalsIgnoreCase(c.getQuotedSymbol()) &&
            baseSymbol.equalsIgnoreCase(c.getBaseSymbol())) {
          openOrderValue += c.getTradeValue();
        }
      }
      return openOrderValue;
    }

    return 0D;
  }

  public static Collection<CopyTradeOrder> getCopyTrades(final long subscriptionId) {
    final ConcurrentHashMap<String, CopyTradeOrder> copyTrades = COPY_TRADES_BY_SUBSCRIPTION.get(subscriptionId);
    if (copyTrades != null) {
      return copyTrades.values();
    }

    return null;
  }

  public static void addOpenOrder(final CopyTradeOrder copyTradeOrder) {
    OPEN_COPY_TRADE_ORDERS.put(copyTradeOrder.getClOrdId(), copyTradeOrder);
  }

  public static void removeOpenOrder(final CopyTradeOrder copyTradeOrder) {
    OPEN_COPY_TRADE_ORDERS.remove(copyTradeOrder.getClOrdId());
  }

  public static Collection<CopyTradeOrder> getAllOpenCopyTrades() {
    return OPEN_COPY_TRADE_ORDERS.values();
  }

  public static void add(final CopyTradeOrder copyTradeOrder) {
    onLoad(copyTradeOrder);
  }

  public static void remove(final CopyTradeOrder copyTradeOrder) {
    onRemove(copyTradeOrder);
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    LOGGER.info("CopyTradeCache loading. ");
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        final CopyTradeOrder copyTradeOrder = parse(rs);
        onLoad(copyTradeOrder);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "CopyTradeCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      int id = loaderCounter.decrementAndGet();
      LOGGER.info("CopyTradeCache loaded. " + id);
    } catch (final Exception e) {
      LOGGER.error("error", e);
      e.printStackTrace();
    }
  }

  private static CopyTradeOrder parse(final ResultSet rs) throws SQLException {
    final CopyTradeOrder copyTradeOrder = new CopyTradeOrder();
    //copyTrade.setId(rs.getInt(1));
    copyTradeOrder.setUserId(rs.getInt(2));
    copyTradeOrder.setSecurityId(rs.getInt(3));
    copyTradeOrder.setSubscriptionId(rs.getLong(4));
    copyTradeOrder.setBaseSymbol(rs.getString(5));
    copyTradeOrder.setQuotedSymbol(rs.getString(6));
    copyTradeOrder.setOrigClOrdId(rs.getString(7));
    copyTradeOrder.setClOrdId(rs.getString(8));
    copyTradeOrder.setPlatform(rs.getString(9));
    copyTradeOrder.setAccountId(rs.getString(10));
    copyTradeOrder.setExchange(rs.getString(11));
    copyTradeOrder.setSide(Side.valueOf(rs.getString(12)));
    copyTradeOrder.setOrdType(OrdType.valueOf(rs.getString(13)));
    copyTradeOrder.setTimeInForce(TimeInForce.valueOf(rs.getString(14)));
    copyTradeOrder.setSignalPercentage(rs.getLong(15));
    copyTradeOrder.setSignalPercentageScale(rs.getShort(16));
    copyTradeOrder.setSignalPrice(rs.getLong(17));
    copyTradeOrder.setSignalPriceScale(rs.getShort(18));
    copyTradeOrder.setOrderQty(rs.getLong(19));
    copyTradeOrder.setOrderQtyScale(rs.getShort(20));
    copyTradeOrder.setPrice(rs.getLong(21));
    copyTradeOrder.setPriceScale(rs.getShort(22));
    copyTradeOrder.setResult(rs.getString(23));
    copyTradeOrder.setCreated(rs.getLong(24));
    copyTradeOrder.setExternalId(rs.getString(25));
    copyTradeOrder.setOriginalAmount(rs.getDouble(26));
    copyTradeOrder.setCumulativeAmount(rs.getDouble(27));
    copyTradeOrder.setStatus(rs.getString(28));
    copyTradeOrder.setToClose(rs.getBoolean(29));
    copyTradeOrder.setxQuantity(rs.getString(30) != null ? new BigDecimal(rs.getString(30)) : null);
    copyTradeOrder.setxPrice(rs.getString(31) != null ? new BigDecimal(rs.getString(31)) : null);
    copyTradeOrder.setKafkaRecordOffset(rs.getLong(32));
    copyTradeOrder.setClosed(false);
    copyTradeOrder.setBorrowedAmount(rs.getDouble(33));
    copyTradeOrder.setRepaid(rs.getBoolean(34));
    copyTradeOrder.setFuturesEnabled(rs.getBoolean(35));
    copyTradeOrder.setTradeValue(rs.getDouble(36));

    return copyTradeOrder;
  }

  public static class CopyTradeData {
    private final ConcurrentSkipListSet<CopyTradeOrder> copyTradeOrders = new ConcurrentSkipListSet<>(Comparator.comparing(
        CopyTradeOrder::getClOrdId));

    public ConcurrentSkipListSet<CopyTradeOrder> getCopyTrades() {
      return copyTradeOrders;
    }

    public void add(CopyTradeOrder copyTradeOrder) {
      this.copyTradeOrders.add(copyTradeOrder);
    }

    public void remove(CopyTradeOrder copyTradeOrder) {
      this.copyTradeOrders.remove(copyTradeOrder);
    }
  }

  public static void main(String[] args) {
    loadFromDB(new AtomicInteger());
  }
}
