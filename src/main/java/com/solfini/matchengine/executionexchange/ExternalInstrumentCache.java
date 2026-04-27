package com.solfini.matchengine.executionexchange;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalInstrumentCache implements Constants{
  public static final double PRICE_PERCENTAGE_SCALE = 1_000_000D;
  private static final long DEFAULT_OPEN_PRICE_PERCENTAGE = 100_000; //100,000 => 10% scaled by 4
  private static final long DEFAULT_CLOSE_PRICE_PERCENTAGE = 50_000; //50,000 => 5% scaled by 4
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalInstrumentCache.class);
  private static final String SELECT = "SELECT id, exchange, base, quoted, tradable, updated, closepricepercentage, isfutures, pricescale, qtyscale, openpricepercentage, symbol, instrumenttype FROM external_instrument_state ORDER BY ID ASC;";
  private static final String INSERT = "INSERT INTO external_instrument_state (exchange,base,quoted,tradable,updated,closePricePercentage,isFutures,pricescale,qtyscale,openPricePercentage,symbol,instrumenttype) VALUES (?,?,?,?,?,?,?,?,?,?,?,?);";
  private static final String UPDATE = "UPDATE external_instrument_state SET tradable=?,updated=?,pricescale=?,qtyscale=?,symbol=? WHERE exchange=? AND base=? AND quoted=? AND isFutures=? AND tradable=true;";
  private static final String SELECT_MAX_ID = "SELECT MAX(id) FROM external_instrument_state WHERE exchange=? AND base=? AND quoted=? AND isFutures=?";

  // key <lower(exchange_instrupentType_base_quote), SymbolData>
  private static final ConcurrentHashMap<String, ExternalSymbol> EXTERNAL_SYMBOL_CACHE = new ConcurrentHashMap<>();
  // key <lower(base), List<SymbolData>> search exchanges and its symbols by base symbol.
  private static final ConcurrentHashMap<String, CopyOnWriteArrayList<ExternalSymbol>> BASE_TO_EXTERNAL_SYMBOLS = new ConcurrentHashMap<>();
  // ket <lower(exchange), Set<base>>
  private static final ConcurrentHashMap<String, HashSet<String>> EXCHANGE_SYMBOLS = new ConcurrentHashMap<>();
  // xchange library specific
  private static final ConcurrentHashMap<String, org.knowm.xchange.instrument.Instrument> INSTRUMENT_CACHE = new ConcurrentHashMap<>();

  private static final Map<String, MultiplierContractDetails> SYMBOL_TO_MULTIPLIER_CONTRACT_MAP = new HashMap<>();
  private static final Map<String, String> SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP = new HashMap<>();

  static {
    init();
  }

  public static void onLoad(final String key, final ExternalSymbol instrument) {
    String base = instrument.getBase();
    // todo parse the symbol format and derive multiplier and the base.
    MultiplierContractDetails details = SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.get((instrument.getExchange() + "_" + instrument.getBase() + instrument.getQuote()).toUpperCase());
    if (details != null) { // handle symbols like BYBIT_1000000BABYDOGEUSDT, BYBIT_SHIB1000PERP, BINANCE_1000SHIBUSDC
      instrument.setMultiplierContract(details.getMultiplier());
      base = details.getBase();
    }
    final CopyOnWriteArrayList<ExternalSymbol> availableExchanges = BASE_TO_EXTERNAL_SYMBOLS.computeIfAbsent(base.toLowerCase(), v -> new CopyOnWriteArrayList<>());
    availableExchanges.add(instrument);

    EXTERNAL_SYMBOL_CACHE.put(key, instrument);
    final HashSet<String> symbols = EXCHANGE_SYMBOLS.computeIfAbsent(instrument.getExchange().toLowerCase(), v -> new HashSet<>());
    symbols.add(instrument.getBase().toLowerCase());
  }

  public static String getKey(final String exchange, final int instrumentType, final String base,
      final String quote) {
    return (exchange + "_" + instrumentType + "_" + base + "_" + quote).toLowerCase();
  }

  public static ExternalSymbol getSymbol(final String key) {
    return EXTERNAL_SYMBOL_CACHE.get(key.toLowerCase());
  }

  public static ExternalSymbol getSymbol(final String exchange, final int instrumentType, final String base,
      final String quote) {
    return EXTERNAL_SYMBOL_CACHE.get(getKey(exchange, instrumentType, base, quote));
  }

  public static void addInstrument(String key, org.knowm.xchange.instrument.Instrument instrument) {
    INSTRUMENT_CACHE.put(key, instrument);
  }

  public static org.knowm.xchange.instrument.Instrument getInstrument(String key) {
    return INSTRUMENT_CACHE.get(key);
  }

  public static ExternalSymbol getSymbolStatus(final String exchange, final String base, final String quoted, final boolean isFutures) {
    final String key = (exchange + "_" + base + "/" + quoted + "_" + (isFutures ? "1" :"2")).toLowerCase();
    return EXTERNAL_SYMBOL_CACHE.get(key);
  }

  public static List<ExternalSymbol> getAvailableExchanges(final String symbol) {
    return BASE_TO_EXTERNAL_SYMBOLS.get(symbol.toLowerCase());
  }

  public static List<String> getAvailableSymbols(final String exchange) {
    return new ArrayList<>(EXCHANGE_SYMBOLS.get(exchange.toLowerCase()));
  }

  public static boolean isTradeableOnExchange(final String exchange, final String base, final String quoted, final boolean isFutures) {
    final String key = (exchange + "_" + base + "/" + quoted + "_" + (isFutures ? "1" :"2")).toLowerCase();
    final ExternalSymbol status = EXTERNAL_SYMBOL_CACHE.get(key);

    return status != null && status.isTradable();
  }

  public static void loadFromDB(final AtomicInteger loaderCounter)  {
    LOGGER.info("ExternalInstrumentCache loading. ");
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        final ExternalSymbol externalSymbol = parseSymbol(rs);

        onLoad(externalSymbol.getKey(), externalSymbol);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "ExternalInstrumentCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      int id = loaderCounter.decrementAndGet();
      LOGGER.info("ExternalInstrumentCache loaded. " + id);
    } catch (final Exception e) {
      LOGGER.error("error", e);
      e.printStackTrace();
    }
  }

  public static void loadFromExchange() {
    for (String exchangeCode : ExternalExchangeUtil.EXCHANGES) {
      try {
        final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly(exchangeCode);
        if (exchange == null) {
          continue;
        }

        final List<ExternalSymbol> instruments = exchange.getExchangeInstrumentsFull();
        for (final ExternalSymbol externalSymbol : instruments) {
          saveToDB(externalSymbol);
        }
        LOGGER.info(Constants.LOG_FMT_4, "Instruments of ", exchangeCode, " loaded. count: " + instruments.size());
      } catch (Exception e) {
        LOGGER.error("Error, failed to load symbols for exchange " + exchangeCode + " ", e);
      }
    }
  }

  private static void saveToDB(final ExternalSymbol externalSymbol) {
    final String key = externalSymbol.getKey();
    if (EXTERNAL_SYMBOL_CACHE.containsKey(key)) {
      externalSymbol.setOpenPricePercentage(EXTERNAL_SYMBOL_CACHE.get(key).getOpenPricePercentage());
      externalSymbol.setClosePricePercentage(EXTERNAL_SYMBOL_CACHE.get(key).getClosePricePercentage());
      updateDB(externalSymbol);
    } else {
      externalSymbol.setOpenPricePercentage(DEFAULT_OPEN_PRICE_PERCENTAGE);
      externalSymbol.setClosePricePercentage(DEFAULT_CLOSE_PRICE_PERCENTAGE);
      addToDB(externalSymbol);
    }
    onLoad(key, externalSymbol);
  }

  private static void addToDB(final ExternalSymbol instrument) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(INSERT);
        final PreparedStatement selectIdPS = conn.prepareStatement(SELECT_MAX_ID)) {
      ps.setString(1, instrument.getExchange().toLowerCase());
      ps.setString(2, instrument.getBase().toLowerCase());
      ps.setString(3, instrument.getQuote().toLowerCase());
      ps.setBoolean(4, instrument.isTradable());
      ps.setLong(5, instrument.getUpdated());
      ps.setLong(6, instrument.getClosePricePercentage());
      ps.setBoolean(7, instrument.isFutures());
      ps.setInt(8, instrument.getPriceScale());
      ps.setInt(9, instrument.getQtyScale());
      ps.setLong(10, instrument.getOpenPricePercentage());
      ps.setString(11, instrument.getSymbol());
      ps.setInt(12, instrument.isFutures() ? TARDIS_PERPS : TARDIS_SPOT);

      ps.executeUpdate();

      selectIdPS.setString(1, instrument.getExchange().toLowerCase());
      selectIdPS.setString(2, instrument.getBase().toLowerCase());
      selectIdPS.setString(3, instrument.getQuote().toLowerCase());
      selectIdPS.setBoolean(4, instrument.isFutures());
      final ResultSet rs = selectIdPS.executeQuery();
      if (rs.next()) {
        final long id = rs.getLong(1);
        instrument.setId(id);
      }
      rs.close();
    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }

  private static void updateDB(final ExternalSymbol instrument) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(UPDATE);) {
      ps.setBoolean(1, instrument.isTradable());
      ps.setLong(2, instrument.getUpdated());
      ps.setInt(3, instrument.getPriceScale());
      ps.setInt(4, instrument.getQtyScale());
      ps.setString(5, instrument.getSymbol());

      ps.setString(6, instrument.getExchange().toLowerCase());
      ps.setString(7, instrument.getBase().toLowerCase());
      ps.setString(8, instrument.getQuote().toLowerCase());
      ps.setBoolean(9, instrument.isFutures());

      ps.executeUpdate();

    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }

  private static ExternalSymbol parseSymbol(final ResultSet rs) throws SQLException {
    final ExternalSymbol externalSymbol = new ExternalSymbol();
    externalSymbol.setId(rs.getInt(1));
    externalSymbol.setExchange(rs.getString(2));
    externalSymbol.setBase(rs.getString(3));
    externalSymbol.setQuote(rs.getString(4));
    externalSymbol.setTradable(rs.getBoolean(5));
    externalSymbol.setUpdated(rs.getLong(6));
    externalSymbol.setClosePricePercentage(rs.getLong(7));
    externalSymbol.setFutures(rs.getBoolean(8));
    externalSymbol.setPriceScale(rs.getInt(9));
    externalSymbol.setQtyScale(rs.getInt(10));
    externalSymbol.setOpenPricePercentage(rs.getLong(11));
    externalSymbol.setSymbol(rs.getString(12));
    externalSymbol.setInstrumentType(rs.getInt(13));

    return externalSymbol;
  }

  private static void init() {
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000000BABYDOGEUSDT", new MultiplierContractDetails("BABYDOGE", "USDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000000CHEEMSUSDT", new MultiplierContractDetails("CHEEMS", "USDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000000MOGUSDT", new MultiplierContractDetails("MOG", "USDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000000PEIPEIUSDT", new MultiplierContractDetails("PEIPEI", "USDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_10000COQUSDT", new MultiplierContractDetails("COQ", "USDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_10000ELONUSDT", new MultiplierContractDetails("ELON", "USDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_10000LADYSUSDT", new MultiplierContractDetails("LADYS", "USDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_10000QUBICUSDT", new MultiplierContractDetails("QUBIC", "USDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_10000SATSUSDT", new MultiplierContractDetails("SATS", "USDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_10000WENUSDT", new MultiplierContractDetails("WEN", "USDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000BONKPERP", new MultiplierContractDetails("BONK", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000BONKUSDT", new MultiplierContractDetails("BONK", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000BTTUSDT", new MultiplierContractDetails("BTT", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000CATUSDT", new MultiplierContractDetails("CAT", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000FLOKIUSDT", new MultiplierContractDetails("FLOKI", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000LUNCUSDT", new MultiplierContractDetails("LUNC", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000NEIROCTOPERP", new MultiplierContractDetails("NEIROCTO", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000NEIROCTOUSDT", new MultiplierContractDetails("NEIROCTO", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000PEPEPERP", new MultiplierContractDetails("PEPE", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000PEPEUSDT", new MultiplierContractDetails("PEPE", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000RATSUSDT", new MultiplierContractDetails("RATS", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000TAGUSDT", new MultiplierContractDetails("TAG", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000TOSHIUSDT", new MultiplierContractDetails("TOSHI", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000TURBOUSDT", new MultiplierContractDetails("TURBO", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000XECUSDT", new MultiplierContractDetails("XEC", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_1000XUSDT", new MultiplierContractDetails("X", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_SHIB1000PERP", new MultiplierContractDetails("SHIB", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BYBIT_SHIB1000USDT", new MultiplierContractDetails("SHIB", "USDT", 1000));

    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000SHIBUSDC", new MultiplierContractDetails("SHIB", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000SHIBUSDT", new MultiplierContractDetails("SHIB", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000XECUSDT", new MultiplierContractDetails("XEC", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000LUNCUSDT", new MultiplierContractDetails("LUNC", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000FLOKIUSDT", new MultiplierContractDetails("FLOKI", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000BONKUSDT", new MultiplierContractDetails("BONK", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000BONKUSDC", new MultiplierContractDetails("BONK", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000SATSUSDT", new MultiplierContractDetails("SATS", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000RATSUSDT", new MultiplierContractDetails("RATS", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000PEPEUSDC", new MultiplierContractDetails("PEPE", "USDC", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000CATUSDT", new MultiplierContractDetails("CAT", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000000MOGUSDT", new MultiplierContractDetails("MOG", "USDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000XUSDT", new MultiplierContractDetails("X", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000CHEEMSUSDT", new MultiplierContractDetails("CHEEMS", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000WHYUSDT", new MultiplierContractDetails("WHY", "USDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BINANCE_1000000BOBUSDT", new MultiplierContractDetails("BOB", "USDT", 1000000));


    // reverse map
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_BABYDOGEUSDT", "1000000BABYDOGEUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_CHEEMSUSDT", "1000000CHEEMSUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_MOGUSDT", "1000000MOGUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_PEIPEIUSDT", "1000000PEIPEIUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_COQUSDT", "10000COQUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_ELONUSDT", "10000ELONUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_LADYSUSDT", "10000LADYSUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_QUBICUSDT", "10000QUBICUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_SATSUSDT", "10000SATSUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_WENUSDT", "10000WENUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_BONKUSDC", "1000BONKPERP");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_BONKUSDT", "1000BONKUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_BTTUSDT", "1000BTTUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_CATUSDT", "1000CATUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_FLOKIUSDT", "1000FLOKIUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_LUNCUSDT", "1000LUNCUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_NEIROCTOUSDC", "1000NEIROCTOPERP");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_NEIROCTOUSDT", "1000NEIROCTOUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_PEPEUSDC", "1000PEPEPERP");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_PEPEUSDT", "1000PEPEUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_RATSUSDT", "1000RATSUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_TAGUSDT", "1000TAGUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_TOSHIUSDT", "1000TOSHIUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_TURBOUSDT", "1000TURBOUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_XECUSDT", "1000XECUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_XUSDT", "1000XUSDT");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_SHIBUSDC", "SHIB1000PERP");
    SYMBOL_TO_MULTIPLIER_CONTRACT_REVERSE_MAP.put("BYBIT_SHIBUSDT", "SHIB1000USDT");
  }

  public static class MultiplierContractDetails {
    private String base;
    private String quote;
    private int multiplier;

    public MultiplierContractDetails(String base, String quote, int multiplier) {
      this.base = base;
      this.quote = quote;
      this.multiplier = multiplier;
    }

    public String getBase() {
      return base;
    }

    public void setBase(String base) {
      this.base = base;
    }

    public String getQuote() {
      return quote;
    }

    public void setQuote(String quote) {
      this.quote = quote;
    }

    public int getMultiplier() {
      return multiplier;
    }

    public void setMultiplier(int multiplier) {
      this.multiplier = multiplier;
    }
  }

  public static class ExchangeData {
    private String exchange;
    private boolean isFutures;

    public String getExchange() {
      return exchange;
    }

    public void setExchange(String exchange) {
      this.exchange = exchange;
    }

    public boolean isFutures() {
      return isFutures;
    }

    public void setFutures(boolean futures) {
      isFutures = futures;
    }
  }

  public static void main(String[] args) {
    //loadFromExchange();
    loadFromDB(new AtomicInteger(1));

    System.out.println("Done");
  }
}
