package com.solfini.matchengine.liquidity;

import static com.solfini.common.Constants.LOG_FMT_12;
import static com.solfini.common.Constants.LOG_FMT_2;
import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.LOG_FMT_6;
import static com.solfini.common.Constants.TARDIS_PERPS;
import static com.solfini.common.Constants.TARDIS_SPOT;
import static org.knowm.xchange.bybit.BybitAdapters.QUOTE_CURRENCIES;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.LiquidityResponse;
import com.solfini.matchengine.message.internal.LiquidityResponse.Depth;
import com.solfini.matchengine.message.internal.LiquidityResponse.Liquidity;
import com.solfini.matchengine.orderbook.LiquidityOrderBook;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.CMCTop30Checker;
import com.solfini.util.StringUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LiquidityCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityCache.class);
  private static final String SELECT_EXCHANGES =
      "SELECT id,name,externalReference,instrumentType,status FROM liquidity_exchange_state WHERE status=1;";
  private static final String SELECT_EXCHANGE_SYMBOLS =
      "SELECT id,exchange,instrumentType,base,quote FROM liquidity_exchange_pair_state WHERE status=1;";

  // has a key formatted as <name>_<instrumentType>
  private static final ConcurrentHashMap<String, Exchange> EXCHANGE_NANE_TYPE_MAP = new ConcurrentHashMap<>();
  // has a key formatted as <exchangeId>_<instrumentType>_<base>_<quote>
  private static final ConcurrentHashMap<String, ExchangeSymbol> SYMBOL_NANE_TYPE_MAP = new ConcurrentHashMap<>();

  public static int getTardisExchangeId(final String exchange, final int instrumentType) {
    final String key = (exchange + "_" + instrumentType).toUpperCase();
    final Exchange e = EXCHANGE_NANE_TYPE_MAP.get(key);
    if (e != null) {
      return e.id;
    }
    return 0;
  }

  public static int getTardisSymbolId(final int tardisExchangeId, final int instrumentType, final String base, final String quote) {
    final String key = (tardisExchangeId + "_" + instrumentType + "_" + base + "_" + quote).toUpperCase();
    final ExchangeSymbol e = SYMBOL_NANE_TYPE_MAP.get(key);
    if (e != null) {
      return e.id;
    }
    return 0;
  }

  public static double getSymbolPrice(final InstrumentPair pair, final Side side, final String alias, final double quantity) {
    String symbol = alias;
    if (Context.getEnvironment().startsWith("TEST")) {// testnet has T_<symbol name> format
      symbol = alias.substring(2);
    }
    final ExchangeSymbol exchangeSymbol = getBestExchangePair(pair, symbol);
    if (exchangeSymbol == null) {
      LOGGER.info(LOG_FMT_2, "Symbol not found in liquidity cache. symbol: ", symbol);
      return -1;
    }
    final int symbolId = exchangeSymbol.getId();
    final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Liquidity>> exchangeSymbolLiquidity
        = ((LiquidityOrderBook) pair.getOrderBook()).getExchangeSymbolLiquidity();
    final ConcurrentHashMap<Integer, Liquidity> liquidityMap = exchangeSymbolLiquidity.get(symbolId);
    double bps = CMCTop30Checker.getBPS(exchangeSymbol.getBase());

    if (liquidityMap != null) {
      final Optional<Liquidity> liquidityOptional = liquidityMap.values().stream().findFirst();// todo sort by best priced exchange
      if (liquidityOptional.isPresent()) {
        Liquidity liquidity = liquidityOptional.get();
        final Instrument quote = InstrumentCache.getBySymbol(exchangeSymbol.quote);
        final double fxRate = quote.getIndexFeedUsdMark();
        if (side == Side.SELL) {
          double price = Math.max(liquidity.getBestAsk(), liquidity.getBestBid());
          int priceMultiplier = getMaximumPriceMultiplier(price);
          for (final LiquidityResponse.Depth depth : liquidity.getBids()) {
            LOGGER.info(LOG_FMT_4, "Ask price: ", depth.getPrice(), " qty: ", depth.getCumQty());
            if (quantity <= depth.getCumQty()) {
              double p = StringUtil.roundDown(depth.getPrice() * fxRate * (1 - bps), priceMultiplier);
              return p;
            }
          }
        } else {
          double price = Math.max(liquidity.getBestAsk(), liquidity.getBestBid());
          int priceMultiplier = getMaximumPriceMultiplier(price);
          for (final Depth depth : liquidity.getAsks()) {
            LOGGER.info(LOG_FMT_4, "Bid price: ", depth.getPrice(), " qty: ", depth.getCumQty());
            if (quantity <= depth.getCumQty()) {
              double p = StringUtil.roundDown(depth.getPrice() * fxRate * (1 + bps), priceMultiplier);
              LOGGER.info(LOG_FMT_12, "Bid price: ", depth.getPrice(), " qty: ", depth.getCumQty(), " fxRate: ", fxRate, " bps: ", bps,
                  " priceMultiplier: ", priceMultiplier, " p: ", p);
              return p;
            }
          }
        }
      }
    }
    return -1;
  }

  public static ExchangeSymbol getBestExchangePair(final InstrumentPair pair, final String alias) {
    ExchangeSymbol spotExchangeSymbol = null;
    ExchangeSymbol perpExchangeSymbol = null;
    // select according to the exchange preference
    for (final String exchange : Context.getLiquidityExchangePreference()) {
      if (spotExchangeSymbol == null) {
        spotExchangeSymbol = findExchangeSymbol(exchange, alias, TARDIS_SPOT);
      }
      if (perpExchangeSymbol == null) {
        perpExchangeSymbol = findExchangeSymbol(exchange, alias, TARDIS_PERPS);
      }
      if (spotExchangeSymbol != null && perpExchangeSymbol != null) break;
    }

    // If nothing is available, return null
    if (spotExchangeSymbol == null && perpExchangeSymbol == null) {
      LOGGER.info(LOG_FMT_2, "Best exchange not found:  ", alias);
      for (final String key : EXCHANGE_NANE_TYPE_MAP.keySet()) {
        LOGGER.info(LOG_FMT_2, "Available keys:  ", key);
      }
      LOGGER.info("Printing SYMBOL_NANE_TYPE_MAP ");
      for (String key : SYMBOL_NANE_TYPE_MAP.keySet()) {
        LOGGER.info("Key: " + key);
      }
      return null;
    }

    if (spotExchangeSymbol == null) {
      LOGGER.info(LOG_FMT_4, "No SPOT pair found for: ", alias, ", using PERP: ", perpExchangeSymbol.getKey());
      return perpExchangeSymbol;
    }

    if (perpExchangeSymbol == null) {
      LOGGER.info(LOG_FMT_4, "No PERP pair found for: ", alias, ", using SPOT: ", spotExchangeSymbol.getKey());
      return spotExchangeSymbol;
    }

    // Both SPOT and PERP available - compare L0 (top-of-book bid) prices and apply spread threshold (configured to 5% )
    final double spotL0 = getL0Price(pair, spotExchangeSymbol);
    final double perpL0 = getL0Price(pair, perpExchangeSymbol);
    LOGGER.info(LOG_FMT_4, "Spot L0: ", spotL0, " Perp L0: ", perpL0);

    // If either price is zero (no data yet), fall back to whichever has data
    if (spotL0 <= 0) return perpExchangeSymbol;
    if (perpL0 <= 0) return spotExchangeSymbol;

    final double spread = Math.abs(perpL0 - spotL0) / spotL0;

    final double spotPerpSpreadThresholdPct = Context.getSpotPerpSpreadThresholdPct();
    if (spread <= spotPerpSpreadThresholdPct) {
      // Within Configured Threshold: use the worst (higher) price
      final ExchangeSymbol selected = (spotL0 >= perpL0) ? spotExchangeSymbol : perpExchangeSymbol;
      LOGGER.info(LOG_FMT_6, "Spread within " , spotPerpSpreadThresholdPct, " (", spread, "), selecting higher price pair: ", selected);
      return selected;
    } else {
      // More than Configured Threshold: use the better (lower) price
      final ExchangeSymbol selected = (spotL0 <= perpL0) ? spotExchangeSymbol : perpExchangeSymbol;
      LOGGER.info(LOG_FMT_6, "Spread exceeds " , spotPerpSpreadThresholdPct, " (", spread, "), selecting lower price pair: ", selected);
      return selected;
    }
  }

  public static int getMaximumPriceMultiplier(final double symbolPrice) {
    if (symbolPrice > 1000) return 100;
    if (symbolPrice > 100)    return 100;
    if (symbolPrice > 10)   return 10_000;
    if (symbolPrice > 1)  return 10_000;

    if (symbolPrice > 0.1)  return 10_000;
    if (symbolPrice > 0.01)  return 1_00_000;
    if (symbolPrice > 0.001)  return 1_000_000;
    if (symbolPrice > 0.0001)  return 10_000_000;

    return 100_000_000;
  }

  private static ExchangeSymbol findExchangeSymbol(final String exchangeName, final String alias, final int instrumentType) {
    final Exchange exchange = EXCHANGE_NANE_TYPE_MAP.get((exchangeName + "_" + instrumentType).toUpperCase());
    if (exchange == null) return null;
    for (final String quote : QUOTE_CURRENCIES) {
      final String key = (exchange.getId() + "_" + instrumentType + "_" + alias + "_" + quote).toUpperCase();
      LOGGER.info("GKey: " + key);
      final ExchangeSymbol exchangeSymbol = SYMBOL_NANE_TYPE_MAP.get(key);
      //LOGGER.info(LOG_FMT_4, "Subscribe " + typeLabel + " ", key, " exchangeSymbol: ", exchangeSymbol);
      if (exchangeSymbol != null) {
        LOGGER.info("Symbol found: " + exchangeSymbol.getKey());
        return exchangeSymbol;
      }
    }
/*    LOGGER.info("Printing SYMBOL_NANE_TYPE_MAP ");
    for (String key : SYMBOL_NANE_TYPE_MAP.keySet()) {
      LOGGER.info("Key: " + key);
    }*/
    return null;
  }

  private static double getL0Price(final InstrumentPair pair, final ExchangeSymbol exchangeSymbol) {
    if (exchangeSymbol == null) return 0;
    final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Liquidity>> exchangeSymbolLiquidity
        = ((LiquidityOrderBook) pair.getOrderBook()).getExchangeSymbolLiquidity();
    final ConcurrentHashMap<Integer, Liquidity> liquidityMap = exchangeSymbolLiquidity.get(exchangeSymbol.getId());
    if (liquidityMap != null) {
      final Optional<Liquidity> liquidityOptional = liquidityMap.values().stream().findFirst();
      if (liquidityOptional.isPresent()) {
        // Normalize to USD so SPOT (e.g. BTCUSDT) and PERP (e.g. BTCUSD) can be compared fairly
        final double bestBid = liquidityOptional.get().getBestBid();
        final Instrument quoteInstrument = InstrumentCache.getBySymbol(exchangeSymbol.getQuote());
        if (quoteInstrument == null) return 0;
        final double fxRate = quoteInstrument.getIndexFeedUsdMark();
        return bestBid * fxRate;
      }
    }
    return 0;
  }

  public static void loadFromDb(final AtomicInteger loaderCounter) {
    loadExchanges();
    loadExchangeSymbols();
    loaderCounter.decrementAndGet();
  }

  private static void loadExchanges() {
    int count = 0;
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_EXCHANGES);
        final ResultSet rs = ps.executeQuery()) {
      count = parseExchange(rs);

      LOGGER.info("Exchanges loaded " + count + " contracts.");
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  private static void loadExchangeSymbols() {
    int count = 0;
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_EXCHANGE_SYMBOLS);
        final ResultSet rs = ps.executeQuery()) {
      count = parseExchangeSymbol(rs);

      LOGGER.info("Exchange symbols loaded " + count + " symbols.");
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  private static int parseExchange(final ResultSet rs) throws SQLException {
    int count = 0;
    while (rs.next()) {
      final Exchange exchange = new Exchange();
      exchange.setId(rs.getInt(1));
      exchange.setName(rs.getString(2));
      exchange.setExternalReference(rs.getString(3));
      exchange.setInstrumentType(rs.getInt(4));
      exchange.setStatus(rs.getInt(5));

      loadExchange(exchange);
      count++;
    }
    return count;
  }

  private static int parseExchangeSymbol(final ResultSet rs) throws SQLException {
    int count = 0;
    while (rs.next()) {
      final ExchangeSymbol exchangeSymbol = new ExchangeSymbol();
      exchangeSymbol.setId(rs.getInt(1));
      exchangeSymbol.setExchangeId(rs.getInt(2));
      exchangeSymbol.setInstrumentType(rs.getInt(3));
      exchangeSymbol.setBase(rs.getString(4));
      exchangeSymbol.setQuote(rs.getString(5));

      loadExchangeSymbol(exchangeSymbol);
      count++;
    }
    return count;
  }

  private static void loadExchange(final Exchange exchange) {
    EXCHANGE_NANE_TYPE_MAP.put(exchange.getKey(), exchange);
  }

  private static void loadExchangeSymbol(final ExchangeSymbol exchangeSymbol) {
    SYMBOL_NANE_TYPE_MAP.put(exchangeSymbol.getKey(), exchangeSymbol);
  }

  private static class Exchange {
    private int id;
    private String name;
    private String externalReference;
    private int instrumentType;
    private int status;

    public String getKey() {
      return (name + "_" + instrumentType).toUpperCase();
    }

    public final int getId() {
      return id;
    }

    public final void setId(final int id) {
      this.id = id;
    }

    public final String getName() {
      return name;
    }

    public final void setName(final String name) {
      this.name = name;
    }

    public final String getExternalReference() {
      return externalReference;
    }

    public final void setExternalReference(final String externalReference) {
      this.externalReference = externalReference;
    }

    public final int getInstrumentType() {
      return instrumentType;
    }

    public final void setInstrumentType(final int instrumentType) {
      this.instrumentType = instrumentType;
    }

    public final int getStatus() {
      return status;
    }

    public final void setStatus(final int status) {
      this.status = status;
    }
  }

  private static class ExchangeSymbol {
    private int id;
    private int exchangeId;
    private int instrumentType;
    private String base;
    private String quote;

    public String getKey() {
      return (exchangeId + "_" + instrumentType + "_" + base + "_" + quote).toUpperCase();
    }

    public final int getId() {
      return id;
    }

    public final void setId(int id) {
      this.id = id;
    }

    public int getExchangeId() {
      return exchangeId;
    }

    public void setExchangeId(int exchangeId) {
      this.exchangeId = exchangeId;
    }

    public final int getInstrumentType() {
      return instrumentType;
    }

    public final void setInstrumentType(final int instrumentType) {
      this.instrumentType = instrumentType;
    }

    public final String getBase() {
      return base;
    }

    public final void setBase(final String base) {
      this.base = base;
    }

    public final String getQuote() {
      return quote;
    }

    public final void setQuote(final String quote) {
      this.quote = quote;
    }
  }

}

