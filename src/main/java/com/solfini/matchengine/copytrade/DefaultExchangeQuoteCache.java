package com.solfini.matchengine.copytrade;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.LOG_FMT_4;

public class DefaultExchangeQuoteCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DefaultExchangeQuoteCache.class);
  private static final String SELECT = "SELECT exchange, quote_symbol FROM exchange_default_quote;";
  private static final ConcurrentHashMap<String, String> EXCHANGE_QUOTE_SYMBOL = new ConcurrentHashMap<>();

  public static String get(final String exchange) {
    return EXCHANGE_QUOTE_SYMBOL.get(exchange.toUpperCase());
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        String exchange = rs.getString(1);
        String symbol = rs.getString(2);
        EXCHANGE_QUOTE_SYMBOL.put(exchange, symbol);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "DefaultExchangeQuoteCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      loaderCounter.decrementAndGet();
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }
}
