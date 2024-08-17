package com.solfini.matchengine.copytrade;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalInstrumentCache implements Constants{
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalInstrumentCache.class);
  private static final String SELECT = "SELECT exchange,base,quoted,tradable,updated,closePricePercentage,isFutures,pricescale,qtyscale FROM external_instrument_state ORDER BY ID ASC;";
  private static final String INSERT = "INSERT INTO external_instrument_state (exchange,base,quoted,tradable,updated,closePricePercentage,isFutures,pricescale,qtyscale) VALUES (?,?,?,?,?,?,?,?,?);";
  private static final String UPDATE = "UPDATE external_instrument_state SET tradable=?,updated=?,pricescale=?,qtyscale=? WHERE exchange=? AND base=? AND quoted=? AND isFutures=?;";

  private static final ConcurrentHashMap<String, XExchange.SymbolStatus> SYMBOL_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, org.knowm.xchange.instrument.Instrument> INSTRUMENT_CACHE = new ConcurrentHashMap<>();
  private static final long DEFAULT_CLOSE_PRICE_PERCENTAGE = 2_000; //2,000 => 0.2% scaled by 4

  public static void onLoad(final String key, final XExchange.SymbolStatus instrument) {
    SYMBOL_CACHE.put(key, instrument);
  }

  public static void addInstrument(String key, org.knowm.xchange.instrument.Instrument instrument) {
    INSTRUMENT_CACHE.put(key, instrument);
  }

  public static org.knowm.xchange.instrument.Instrument getInstrument(String key) {
    return INSTRUMENT_CACHE.get(key);
  }

  public static XExchange.SymbolStatus getSymbolStatus(final String exchange, final String base, final String quoted, final boolean isFutures) {
    final String key = (exchange + "_" + base + "/" + quoted + "_" + (isFutures ? "1" :"0")).toLowerCase();
    return SYMBOL_CACHE.get(key);
  }

  public static boolean isTradeableOnExchange(final String exchange, final String base, final String quoted, final boolean isFutures) {
    final String key = (exchange + "_" + base + "/" + quoted + "_" + (isFutures ? "1" :"0")).toLowerCase();
    final XExchange.SymbolStatus status = SYMBOL_CACHE.get(key);

    return status != null && status.isTradable();
  }

  public static long getClosePricePercentage(final String exchange, final String base, final String quoted, final boolean isFutures) {
    final String key = (exchange + "_" + base + "/" + quoted + "_" + (isFutures ? "1" :"0")).toLowerCase();
    final XExchange.SymbolStatus status = SYMBOL_CACHE.get(key);
    if (status != null) {
      return status.getClosePricePercentage();
    }

    return DEFAULT_CLOSE_PRICE_PERCENTAGE;
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        String exchange = rs.getString(1);
        String base = rs.getString(2);
        String quoted = rs.getString(3);
        boolean tradable = rs.getBoolean(4);
        long updated = rs.getLong(5);
        long closePricePercentage = rs.getLong(6);
        boolean isFutures = rs.getBoolean(7);
        int priceScale = rs.getInt(8);
        int qtyScale = rs.getInt(9);
        final XExchange.SymbolStatus instrument = new XExchange.SymbolStatus();
        instrument.setExchange(exchange);
        instrument.setBase(base);
        instrument.setQuote(quoted);
        instrument.setTradable(tradable);
        instrument.setUpdated(updated);
        instrument.setClosePricePercentage(closePricePercentage);
        instrument.setFutures(isFutures);
        instrument.setPriceScale(priceScale);
        instrument.setQtyScale(qtyScale);

        onLoad(instrument.getKey(), instrument);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "ExternalInstrumentCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      loaderCounter.decrementAndGet();
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  public static void loadFromExchange() {
    for (String exchangeCode : ExternalExchangeUtil.EXCHANGES) {
      try {
        final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly(exchangeCode);
        if (exchange == null) {
          continue;
        }

        final List<XExchange.SymbolStatus> instruments = exchange.getExchangeInstrumentsFull();
        for (final XExchange.SymbolStatus instrument : instruments) {
          saveToDB(instrument);
        }
        LOGGER.info(Constants.LOG_FMT_4, "Instruments of ", exchangeCode, " loaded. count: " + instruments.size());
      } catch (Exception e) {
        LOGGER.error("Error, failed to load symbols for exchange " + exchangeCode + " ", e);
      }
    }
  }

  private static void saveToDB(final XExchange.SymbolStatus instrument) {
    final String key = instrument.getKey();
    if (SYMBOL_CACHE.containsKey(key)) {
      instrument.setClosePricePercentage(SYMBOL_CACHE.get(key).getClosePricePercentage());
      updateDB(instrument);
    } else {
      addToDB(instrument);
    }
    onLoad(key, instrument);
  }

  private static void addToDB(final XExchange.SymbolStatus instrument) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(INSERT);) {
      ps.setString(1, instrument.getExchange().toLowerCase());
      ps.setString(2, instrument.getBase().toLowerCase());
      ps.setString(3, instrument.getQuote().toLowerCase());
      ps.setBoolean(4, instrument.isTradable());
      ps.setLong(5, instrument.getUpdated());
      ps.setLong(6, instrument.getClosePricePercentage());
      ps.setBoolean(7, instrument.isFutures());
      ps.setInt(8, instrument.getPriceScale());
      ps.setInt(9, instrument.getQtyScale());

      ps.executeUpdate();

    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }

  private static void updateDB(final XExchange.SymbolStatus instrument) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(UPDATE);) {
      ps.setBoolean(1, instrument.isTradable());
      ps.setLong(2, instrument.getUpdated());
      ps.setInt(3, instrument.getPriceScale());
      ps.setInt(4, instrument.getQtyScale());

      ps.setString(5, instrument.getExchange().toLowerCase());
      ps.setString(6, instrument.getBase().toLowerCase());
      ps.setString(7, instrument.getQuote().toLowerCase());
      ps.setBoolean(8, instrument.isFutures());

      ps.executeUpdate();

    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }



  public static void main(String[] args) {
    //loadFromExchange();
    loadFromDB(new AtomicInteger(1));

    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "USDT", false));
    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "USDC", false));

    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "USD", false));
    System.out.println(isTradeableOnExchange("BINANCE", "BTC", "$", false));

    System.out.println(isTradeableOnExchange("BITSTAMP","BTC", "USDT", false));
    System.out.println(isTradeableOnExchange("BITSTAMP","BTC", "USD", false));

    System.out.println(isTradeableOnExchange("BITSTAMP","BTC", "$",false));
    System.out.println(isTradeableOnExchange("BITMEX", "BTC", "USDT", false));
    System.out.println(isTradeableOnExchange("BITMEX", "BTC", "USD", false));
    System.out.println(isTradeableOnExchange("BITMEX", "BTC", "$", false));

    System.out.println("Done");
  }
}
