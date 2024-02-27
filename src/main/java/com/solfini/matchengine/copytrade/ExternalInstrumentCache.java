package com.solfini.matchengine.copytrade;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.instrument.Instrument;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalInstrumentCache implements Constants{
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalInstrumentCache.class);
  private static final String SELECT = "SELECT exchange,base,quoted,tradable,updated,closePricePercentage FROM external_instrument_state ORDER BY ID ASC;";
  private static final String INSERT = "INSERT INTO external_instrument_state (exchange,base,quoted,tradable,updated,closePricePercentage) VALUES (?,?,?,?,?,?);";
  private static final String UPDATE = "UPDATE external_instrument_state SET tradable=?,updated=? WHERE exchange=? AND base=? AND quoted=?;";

  private static final ConcurrentHashMap<String, SymbolStatus> SYMBOL_CACHE = new ConcurrentHashMap<>();
  private static final long DEFAULT_CLOSE_PRICE_PERCENTAGE = 2000; //2000 => 20% scaled by 2

  public static void onLoad(final String exchange, final String base, final String quoted, final boolean tradable, final long updated, final long closePricePercentage) {
    final String key = (exchange + "_" + base + "/" + quoted).toLowerCase();

    final SymbolStatus status = new SymbolStatus(tradable, updated, closePricePercentage);

    SYMBOL_CACHE.put(key, status);
  }

  public static boolean isTradeableOnExchange(final String exchange, final String base, final String quoted) {
    final String key = (exchange + "_" + base + "/" + quoted).toLowerCase();
    final SymbolStatus status = SYMBOL_CACHE.get(key);

    return status != null && status.tradable && status.updated > (System.currentTimeMillis() - TWO_DAY);
  }

  public static long getClosePricePercentage(final String exchange, final String base, final String quoted) {
    final String key = (exchange + "_" + base + "/" + quoted).toLowerCase();
    final SymbolStatus status = SYMBOL_CACHE.get(key);
    if (status != null) {
      return status.getClosePricePercentage();
    }

    return DEFAULT_CLOSE_PRICE_PERCENTAGE;
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement userPS = conn.prepareStatement(SELECT);
        final ResultSet rs = userPS.executeQuery();) {
      while (rs.next()) {
        String exchange = rs.getString(1);
        String base = rs.getString(2);
        String quoted = rs.getString(3);
        boolean tradable = rs.getBoolean(4);
        long updated = rs.getLong(5);
        long closePricePercentage = rs.getLong(6);

        onLoad(exchange, base, quoted, tradable, updated, closePricePercentage);
        count++;
      }
      LOGGER.info(LOG_FMT_1, "ExternalInstrumentCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      loaderCounter.decrementAndGet();
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  public static void loadFromExchange() {
    for (String exchangeCode : ExternalExchangeUtil.EXCHANGES) {
      try {
        final XExchange exchange = ExternalExchangeUtil.createXExchange(exchangeCode);
        if (exchange == null) {
          continue;
        }

        final List<Instrument> instruments = exchange.getExchangeInstruments();
        for (final Instrument instrument : instruments) {
          saveToDB(exchangeCode.toLowerCase(), instrument.getBase().getCurrencyCode().toLowerCase(), instrument.getCounter().getCurrencyCode().toLowerCase(), true);
        }
      } catch (Exception e) {
        LOGGER.error("Error, failed to load symbols for exchange " + exchangeCode, e);
      }
    }
  }

  private static void saveToDB(final String exchange, final String base, final String quoted, final boolean tradable) {
    long closePricePercentage = DEFAULT_CLOSE_PRICE_PERCENTAGE;
    final String key = (exchange + "_" + base + "/" + quoted).toLowerCase();
    if (SYMBOL_CACHE.containsKey(key)) {
      closePricePercentage = SYMBOL_CACHE.get(key).getClosePricePercentage();
      updateDB(exchange, base, quoted, tradable);
    } else {
      addToDB(exchange, base, quoted, tradable, DEFAULT_CLOSE_PRICE_PERCENTAGE);
    }
    onLoad(exchange, base, quoted, tradable, System.currentTimeMillis(), closePricePercentage);
  }

  private static void addToDB(final String exchange, final String base, final String quoted, final boolean tradable, final long closePricePercentage) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(INSERT);) {
      ps.setString(1, exchange);
      ps.setString(2, base);
      ps.setString(3, quoted);
      ps.setBoolean(4, tradable);
      ps.setLong(5, System.currentTimeMillis());
      ps.setLong(6, closePricePercentage);

      ps.executeUpdate();

    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }

  private static void updateDB(final String exchange, final String base, final String quoted, final boolean tradable) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(UPDATE);) {
      ps.setBoolean(1, tradable);
      ps.setLong(2, System.currentTimeMillis());

      ps.setString(3, exchange);
      ps.setString(4, base);
      ps.setString(5, quoted);

      ps.executeUpdate();

    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }

  private static class SymbolStatus {
    private boolean tradable;
    private long updated;
    private long closePricePercentage;


    public SymbolStatus(boolean tradable, long updated, long closePricePercentage) {
      this.tradable = tradable;
      this.updated = updated;
      this.closePricePercentage = closePricePercentage;
    }

    public boolean isTradable() {
      return tradable;
    }

    public void setTradable(boolean tradable) {
      this.tradable = tradable;
    }

    public long getUpdated() {
      return updated;
    }

    public void setUpdated(long updated) {
      this.updated = updated;
    }

    public long getClosePricePercentage() {
      return closePricePercentage;
    }

    public void setClosePricePercentage(long closePricePercentage) {
      this.closePricePercentage = closePricePercentage;
    }
  }

  public static void main(String[] args) {
    //loadFromExchange();
    loadFromDB(new AtomicInteger(1));

    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "USDT"));
    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "USDC"));

    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "USD"));
    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "$"));

    System.out.println(isTradeableOnExchange("BITSTAMP","BTC", "USDT"));
    System.out.println(isTradeableOnExchange("BITSTAMP","BTC", "USD"));

    System.out.println(isTradeableOnExchange("BITSTAMP","BTC", "$"));
    System.out.println(isTradeableOnExchange("BITMEX", "BTC", "USDT"));
    System.out.println(isTradeableOnExchange("BITMEX", "BTC", "USD"));
    System.out.println(isTradeableOnExchange("BITMEX", "BTC", "$"));

    System.out.println("Done");
  }
}
