package com.solfini.matchengine.copytrade;

import com.solfini.db.DBManager;
import com.solfini.db.ExternalDBManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;

public class TickerTopBottomAccountCache {
  private static final Logger LOGGER = LogManager.getLogger(TickerTopBottomAccountCache.class);
  private static final String SELECT_TICKERS = "SELECT subscriptiontype,preferredcurrencies as ticker FROM subscription_state WHERE subscriptiontype IN (2,3) AND status=1 AND expires>? AND updated>? GROUP BY subscriptiontype, preferredcurrencies;";
  private static final String SELECT = """
                (
                    SELECT
                        2 AS REC_TYPE,
                        u.USERNAME,
                        u.RANK
                    FROM TopUsers_TickerScore u
                    LEFT JOIN UserCumulativeReturns ur ON ur.USERNAME = u.USERNAME AND ur.TICKER = u.TICKER AND ur.WINDOW_PERIOD='3M'
                    WHERE
                        u.TS = (SELECT TS FROM TopUsers_TickerScore ORDER BY TS DESC LIMIT 1)
                        AND u.TICKER = ?
                        AND u.TIME_FRAME = 'THREE_WEEKS'
                        AND u.PNL > 0
                    ORDER BY u.RANK ASC
                    LIMIT 10
                )
                UNION
                (
                    SELECT
                        3 AS REC_TYPE,
                        u.USERNAME,
                        u.RANK
                    FROM TopUsers_TickerScore u
                    LEFT JOIN UserCumulativeReturns ur ON ur.USERNAME = u.USERNAME AND ur.TICKER = u.TICKER AND ur.WINDOW_PERIOD='3M'
                    WHERE
                        u.TS = (SELECT TS FROM TopUsers_TickerScore ORDER BY TS DESC LIMIT 1)
                        AND u.TICKER = ?
                        AND u.TIME_FRAME = 'THREE_WEEKS'
                        AND u.PNL < 0
                    ORDER BY u.RANK DESC
                    LIMIT 10
                )
      """;
  private static final ConcurrentHashMap<String, String> TICKER_MAP = new ConcurrentHashMap<>();
  //Set<String> should be immutable
  private static final ConcurrentHashMap<String, Set<String>> TOP_BOTTOM_USER_MAP = new ConcurrentHashMap<>();
  private static long LAST_LOADED_TIME;

  public static void onLoad(final String ticker, final Set<String> accountIds, final int subscriptionType) {
    final String key = (subscriptionType + "_" + ticker).toUpperCase();

    TOP_BOTTOM_USER_MAP.put(key, accountIds);
  }

  public static boolean isTopBottomUser(final String ticker, final String accountId, final int subscriptionType) {
    if (accountId == null) return false;
    String key = (subscriptionType + "_" + ticker).toUpperCase();
    final Set<String> accountIds = TOP_BOTTOM_USER_MAP.get(key);
    if (accountIds != null) {
      return accountIds.contains(accountId.toUpperCase());
    }
    return false;
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final Set<String> newTickers = new HashSet<>();
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_TICKERS)){
      ps.setLong(1, System.currentTimeMillis());
      ps.setLong(2, (LAST_LOADED_TIME - TEN_MINUTES));
      try (final ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          //int subscriptionType = rs.getInt(1);
          String ticker = rs.getString(2);
          newTickers.add(ticker);
          TICKER_MAP.put(ticker, ticker);
          count++;
        }
      }
      LOGGER.info(LOG_FMT_4, "TickerTopBottomAccountCache.loadTickersFromDB=", count, ", time=", (System.currentTimeMillis() - t0));
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
    if (!newTickers.isEmpty()) {
      loadTopUsersFromDB(newTickers);
    }
    if (loaderCounter != null) {
      loaderCounter.decrementAndGet();
      print("INITIAL");
    }
  }

  public static void reloadTopUsersFromDB() {
    print("BEFORE");
    loadTopUsersFromDB(TICKER_MAP.values());
    print("AFTER");
  }

  private static void loadTopUsersFromDB(final Collection<String> tickers) {
    LAST_LOADED_TIME = System.currentTimeMillis();
    if (!tickers.isEmpty()) {
      for (String ticker : tickers) {
        final Set<String> topAccounts = new HashSet<>();
        final Set<String> bottomAccounts = new HashSet<>();
        int count = 0;
        final long t0 = System.currentTimeMillis();
        try (final Connection conn = ExternalDBManager.getConnection();
            final PreparedStatement ps = conn.prepareStatement(SELECT)) {
          ps.setString(1, ticker);
          ps.setString(2, ticker);
          try (final ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              int recType = rs.getInt(1);
              String accountId = rs.getString(2);
              if (recType == InfluencerSubscription.TYPE_TOP) {
                topAccounts.add(accountId.toUpperCase());
              } else {
                bottomAccounts.add(accountId.toUpperCase());
              }
              count++;
            }
          }
          if (!topAccounts.isEmpty()) {
            onLoad(ticker, topAccounts, InfluencerSubscription.TYPE_TOP);
          }
          if (!bottomAccounts.isEmpty()){
            onLoad(ticker, bottomAccounts, InfluencerSubscription.TYPE_BOTTOM);
          }
          LOGGER.info(LOG_FMT_6, "TickerTopBottomAccountCache.loadFromDB=", count, ", ticker=", ticker, ", time=", (System.currentTimeMillis() - t0));
        } catch (final Exception e) {
          LOGGER.error("error", e);
        }
      }
    }
  }

  private static void print(final String txt) {
    final StringBuilder sb = new StringBuilder();
    sb.append("\nTop bottom ticker cache ").append(txt);
    for (Map.Entry<String, Set<String>> tickerUsers : TOP_BOTTOM_USER_MAP.entrySet()) {
      sb.append("\nTicker: ").append(tickerUsers.getKey()).append("\n");
      for (String accountId : tickerUsers.getValue()) {
        sb.append(accountId).append(", ");
      }
    }
    sb.append("\nEnd. ").append(txt);
    LOGGER.info(sb.toString());
  }
}
