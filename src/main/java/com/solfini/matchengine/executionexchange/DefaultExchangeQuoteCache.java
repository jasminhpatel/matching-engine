package com.solfini.matchengine.executionexchange;

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
  private static final String SELECT = "SELECT exchange,base_symbol,quote_symbol FROM exchange_default_quote;";
  private static final ConcurrentHashMap<String, String> EXCHANGE_QUOTE_SYMBOL = new ConcurrentHashMap<>();

  public static String get(final String exchange, String baseSymbol) {
    return EXCHANGE_QUOTE_SYMBOL.get((exchange + "_" + baseSymbol).toUpperCase());
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    LOGGER.info("DefaultExchangeQuoteCache loading. ");
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        String exchange = rs.getString(1);
        String baseSymbol = rs.getString(2);
        String quoteSymbol = rs.getString(3);
        EXCHANGE_QUOTE_SYMBOL.put((exchange + "_" + baseSymbol).toUpperCase(), quoteSymbol);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "DefaultExchangeQuoteCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      if (loaderCounter != null) {
        int id = loaderCounter.decrementAndGet();
        LOGGER.info("DefaultExchangeQuoteCache loaded. " + id);
      }
    } catch (final Exception e) {
      LOGGER.error("error", e);
      e.printStackTrace();
    }
  }
}
