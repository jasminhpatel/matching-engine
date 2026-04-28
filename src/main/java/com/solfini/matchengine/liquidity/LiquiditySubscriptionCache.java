package com.solfini.matchengine.liquidity;

import static com.solfini.common.Constants.ERROR_LOG;
import static com.solfini.common.Constants.FIVE_MINUTE;
import static com.solfini.common.Constants.LOG_FMT_2;
import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.LOG_FMT_6;
import static com.solfini.common.Constants.SIX_MINUTE;
import static com.solfini.common.Constants.USD;
import static com.solfini.common.Constants.USDC;
import static com.solfini.common.Constants.USDT;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.direct.aiGenerated.*;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.AsyncSender;
import com.solfini.util.EncryptDecrypt2;
import com.solfini.util.MbxMath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LiquiditySubscriptionCache {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(
      LiquiditySubscriptionCache.class);
  private static final String SELECT =
      "SELECT id,exchange,apiUser,apiKey,apiSecret,status,created,expires,futuresEnabled,hasLeverage,lastUsedProxy,apiKey2,apiSecret2 FROM liquidity_subscription_state ORDER BY id ASC";
  private static final String SELECT_UPDATED =
      "SELECT id,exchange,apiUser,apiKey,apiSecret,status,created,expires,futuresEnabled,hasLeverage,lastUsedProxy,apiKey2,apiSecret2 FROM liquidity_subscription_state WHERE updated > ? ORDER BY id ASC";
  private static final ConcurrentHashMap<String, ExchangeSubscription> SUBSCRIPTIONS = new ConcurrentHashMap<>();
  private static final HashSet<ExternalExchangeClient> registerClients = new HashSet<ExternalExchangeClient>();
  private static long LAST_PRINTED = System.currentTimeMillis();

  public static void onLoad(final ExchangeSubscription subscription) {
    final String key = (subscription.getExchange() + "_" + (subscription.isFuturesEnabled() ? "1"
        : "0")).toLowerCase();
    if (subscription.getLastUsedProxy() == null || subscription.getLastUsedProxy().isEmpty()) {
      subscription.setLastUsedProxy(ExternalExchangeUtil.getStickyProxy(subscription.getId()));
    }
    SUBSCRIPTIONS.put(key, subscription);
  }

  public static ExchangeSubscription get(final String exchange, final boolean futuresEnabled) {
    final String key = (exchange + "_" + (futuresEnabled ? "1" : "0")).toLowerCase();
    return SUBSCRIPTIONS.get(key);
  }

  public final static void startAllSubscriptions() {

    final Collection<ExchangeSubscription> subscriptions = SUBSCRIPTIONS.values();

    for (final ExchangeSubscription subscription : subscriptions) {
      if (subscription.getStatus() == 1) {
        if ("BINANCE".equalsIgnoreCase(subscription.getExchange())) {
          final BinanceFastClient client = new BinanceFastClient(subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("BYBIT".equalsIgnoreCase(subscription.getExchange())) {
          final BybitFastClient client = new BybitFastClient(subscription.getApiKey(),
              subscription.getApiSecret(), subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("MEXC".equalsIgnoreCase(subscription.getExchange())) {
          final MexcFastClient client = new MexcFastClient(subscription.getApiKey(),
              subscription.getApiSecret(), subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("BITGET".equalsIgnoreCase(subscription.getExchange())) {
          final BitgetFastClient client = new BitgetFastClient(subscription.getApiKey(),
              subscription.getApiSecret(), subscription.getPassphrase(), subscription, false);// use default v3
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("DERIBIT".equalsIgnoreCase(subscription.getExchange())) {
          final DeribitFastClient client = new DeribitFastClient(subscription.getApiKey(),
              subscription.getApiSecret(), subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("BITMART".equalsIgnoreCase(subscription.getExchange())) {
          final BitmartFastClient client = new BitmartFastClient(subscription.getApiKey(),
              subscription.getApiSecret(), subscription.getPassphrase(), subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("KRAKEN".equalsIgnoreCase(subscription.getExchange())) {
          final KrakenFastClient client = new KrakenFastClient(subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("COINBASE".equalsIgnoreCase(subscription.getExchange())) {
          final CoinbaseFastClient client = new CoinbaseFastClient(subscription.getApiKey(),
              subscription.getApiSecret(), subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        } else if ("KUCOIN".equalsIgnoreCase(subscription.getExchange())) {
          final KucoinFastClient client = new KucoinFastClient(subscription);
          registerClients.add(client);
          subscription.setClient(client);
          client.start();
        }

      }
    }
  }

  public static void processImbalance() {
    if (!Context.isLiquidityDexEnabled()) {
      return;
    }
    try {
      if (LAST_PRINTED + FIVE_MINUTE < System.currentTimeMillis()) {
        LAST_PRINTED = System.currentTimeMillis();
        final User marketMaker = UserCache.getMarketMakerUser();
        if (marketMaker == null) {
          LOGGER.warn("Market maker user not found to handle imbalance.");
          return;
        }

        //final StringBuilder exchangeBalances = new StringBuilder();
        //final Map<Integer, Double> exchangePositionsMap = new HashMap<>();// todo use agrona<int, long> collections after the initial testing
        final Map<String, PositionData> symbolPositions = new HashMap<>(); // todo use agrona<int, long> collections after the initial testing
        final SortedSet<String> exchanges = new TreeSet<>();
        // process external exchange position
        for (ExchangeSubscription s : SUBSCRIPTIONS.values()) {
          if (("BYBIT".equalsIgnoreCase(s.getExchange()) || "BINANCE".equalsIgnoreCase(
              s.getExchange())) && s.isFuturesEnabled()) {
            continue; // bybit unified spot returns both spot and future balances, binance also returns the same
          }

          for (LastBalance l : s.getBalanceCache().values()) {
            final String symbol = l.getSymbol().toUpperCase() + "/" + USD;

            exchanges.add(s.getExchange());
            if (l.getQuantity() != 0) {
              final PositionData positionData = symbolPositions.computeIfAbsent(symbol,
                  v -> new PositionData(symbol));
              positionData.addExchangeBalance(s.getExchange(), l.getQuantity());
              positionData.addToTotalBalance(l.getQuantity());
            }
          }

          for (LastBalance l : s.getPositionCache().values()) {
            if (l.getQuantity() != 0) {
              String symbol = l.getSymbol();
              if (!USDC.equalsIgnoreCase(symbol) && symbol.endsWith(USDC)) {
                symbol = symbol.substring(0, symbol.length() - 4) + "/" + USD;
              }
              if (!USDT.equalsIgnoreCase(symbol) && symbol.endsWith(USDT)) {
                symbol = symbol.substring(0, symbol.length() - 4) + "/" + USD;
              }
              if (l.getQuantity() != 0) {
                final String finalSymbol = symbol;
                final PositionData positionData = symbolPositions.computeIfAbsent(symbol,
                    v -> new PositionData(
                        finalSymbol));
                positionData.addExchangeBalance(s.getExchange(), l.getQuantity());
                positionData.addToTotalBalance(l.getQuantity());
              }
            }
          }

          final double usdcBalance = s.getUsdcBalance();
          final PositionData usdcPositionData = symbolPositions.computeIfAbsent(USDC,
              v -> new PositionData(USDC));
          usdcPositionData.addExchangeBalance(s.getExchange(), usdcBalance);
          usdcPositionData.addToTotalBalance(usdcBalance);

          final double usdtBalance = s.getUsdtBalance();
          final PositionData usdtPositionData = symbolPositions.computeIfAbsent(USDT,
              v -> new PositionData(USDT));
          usdtPositionData.addExchangeBalance(s.getExchange(), usdtBalance);
          usdtPositionData.addToTotalBalance(usdtBalance);
        }

        for (Position p : marketMaker.getPositionArr()) {
          if (p == null) {
            continue;
          }
          final Instrument instrument = InstrumentCache.get(p.getInstrumentId());
          final InstrumentPair pair = InstrumentCache.getPair(p.getInstrumentId());
          String symbol;
          short scale = 0;
          if (instrument != null) {
            symbol = instrument.getSymbol();
            scale = instrument.getQuantityScale();
          } else if (pair != null) {
            scale = pair.getQuantityScale();
            symbol = pair.getSymbol();
          } else {
            symbol = null;
          }
          if (symbol != null) {
            double marketMakerPositions = MbxMath.scaleDown(p.getQuantity(), scale);
            final PositionData positionData = symbolPositions.computeIfAbsent(symbol,
                v -> new PositionData(symbol));
            positionData.setMarketMakerBalance(marketMakerPositions);
          }
        }
        //print the diff
        final StringBuilder csv = new StringBuilder();
        csv.append("Symbol,MarketMakerBalance,TotalExternalBalance");
        for (String exchange : exchanges) {
          csv.append(",").append(exchange);
        }
        csv.append("\n");
        List<PositionData> sorted = symbolPositions.values().stream()
            .sorted(Comparator.comparing(PositionData::getSymbol)).toList();
        for (PositionData positionData : sorted) {
          csv.append(positionData.symbol).append(",").append(positionData.marketMakerBalance)
              .append(",")
              .append(positionData.totalExchangeBalance);
          for (String exchange : exchanges) {
            csv.append(",").append(positionData.getExchangeBalances().getOrDefault(exchange, 0D));
          }
          csv.append("\n");
        }
        if (sendAlert()) {
          AsyncSender.processInANewThread("External Exchange Reconciliation",
              "External exchange reconciliation notification",
              "text/csv", "external_exchange_reconciliation", csv.toString().getBytes());
        }
        LOGGER.info("Reconciliation output: \n" + csv.toString());
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      e.printStackTrace();
    }
  }

  public static double processImbalance(final InstrumentPair pair) {
    try {
      final User marketMaker = UserCache.getMarketMakerUser();
      if (marketMaker == null) {
        LOGGER.warn(LOG_FMT_2, "Market maker user not found to handle imbalance.", pair.getBase().getSymbol());
        return 0;
      }
      boolean isTestnet = "TEST".equalsIgnoreCase(Context.getEnvironment());
      final String symbol = isTestnet ? pair.getBase().getSymbol().substring(2) : pair.getBase().getSymbol();
      final String pairSymbol = isTestnet? pair.getSymbol().substring(2) : pair.getSymbol();
      final SortedSet<String> exchanges = new TreeSet<>();
      //final PositionData usdcPositionData = new PositionData(USDC);
      //final PositionData usdtPositionData = new PositionData(USDT);
      final PositionData symbolBalanceData = new PositionData(symbol);
      final PositionData pairPositionData = new PositionData(pairSymbol);

      // load external exchange position
      for (ExchangeSubscription s : SUBSCRIPTIONS.values()) {
        // ignore futures subscription if spot market returns both spot and futures
        if (("BYBIT".equalsIgnoreCase(s.getExchange()) || "BINANCE".equalsIgnoreCase(
            s.getExchange())) && s.isFuturesEnabled()) {
          continue; // bybit unified spot returns both spot and future balances, binance also returns the same
        }
        exchanges.add(s.getExchange());

/*        final double usdcBalance = s.getUsdcBalance();
        usdcPositionData.addExchangeBalance(s.getExchange(), usdcBalance);
        usdcPositionData.addToTotalBalance(usdcBalance);*/

/*        final double usdtBalance = s.getUsdtBalance();
        usdtPositionData.addExchangeBalance(s.getExchange(), usdtBalance);
        usdtPositionData.addToTotalBalance(usdtBalance);*/

        final LastBalance symbolBalance = s.getBalanceCache().get(pairSymbol);
        if (symbolBalance != null) {
          LOGGER.info(LOG_FMT_6, "Balance of: ", s.getExchange(), " futures: ", s.isFuturesEnabled(), " amount: ", symbolBalance.getQuantity());
          symbolBalanceData.addExchangeBalance(s.getExchange(), symbolBalance.getQuantity());
          symbolBalanceData.addToTotalBalance(symbolBalance.getQuantity());
        }

        final LastBalance symbolPosition = s.getPositionCache().get(pairSymbol);
        if (symbolPosition != null) {
          LOGGER.info(LOG_FMT_6, "Balance of: ", s.getExchange(), " futures: ", s.isFuturesEnabled(), " amount: ", symbolPosition.getQuantity());
          pairPositionData.addExchangeBalance(s.getExchange(), symbolPosition.getQuantity());
          pairPositionData.addToTotalBalance(symbolPosition.getQuantity());
        }
      }
      // load market maker balance
      final Position basePositions = marketMaker.getPosition(pair.getBaseId());
      final short baseScale = pair.getBase().getQuantityScale();
      double marketMakerBalance = MbxMath.scaleDown(basePositions.getQuantity(), baseScale);
      symbolBalanceData.setMarketMakerBalance(marketMakerBalance);

      // load market maker position
      final Position pairPositions = marketMaker.getPosition(pair.getId());
      final short scale = pair.getQuantityScale();
      double marketMakerPositions = MbxMath.scaleDown(pairPositions.getQuantity(), scale);
      pairPositionData.setMarketMakerBalance(marketMakerPositions);

      double imbalance = pairPositionData.getMarketMakerBalance() - pairPositionData.getTotalExchangeBalance();
      boolean hasImbalance = Math.abs(imbalance) > 0.0001;
      if (hasImbalance) {
        //print the diff
        final StringBuilder csv = new StringBuilder();
        csv.append("\n============================== Imbalance for ").append(symbol)
            .append(" ===================================");
        csv.append(
            "\nSymbol,MarketMakerBalance,MarketMakerPosition,TotalExternalBalance,HasImbalance,Imbalance");
        csv.append("\n").append(pairSymbol).append(",")
            .append(symbolBalanceData.getMarketMakerBalance()).append(",")
            .append(pairPositionData.getMarketMakerBalance()).append(",")
            .append(symbolBalanceData.getTotalExchangeBalance()).append(",")
            .append(pairPositionData.getTotalExchangeBalance()).append(",")
            //.append(usdcPositionData.getTotalExchangeBalance()).append(",")
            //.append(usdtPositionData.getTotalExchangeBalance()).append(",")
            .append(hasImbalance).append(",").append(imbalance)
        ;

  /*      csv.append("\n============================== Stable coin balances ").append(" ===================================");
        csv.append("\nExchange,Symbol,Balance");
        for (String exchange : exchanges) {
          csv.append("\n").append(exchange).append(",").append(USDC).append(",").append(usdcPositionData.getExchangeBalances().get(USDC));
          csv.append("\n").append(exchange).append(",").append(USDT).append(",").append(usdtPositionData.getExchangeBalances().get(USDT));
        }
        csv.append("\n");*/
        LOGGER.info(csv.toString());
        // Send Async email to notify the imbalance
/*        AsyncSender.sendEmailInANewThread("Position imbalance alert",
            "External exchange position imbalance notification.",
            "text/csv", "external_exchange_reconciliation", csv.toString().getBytes());*/
      } else {
        LOGGER.info(LOG_FMT_4, "No imbalance for the instrumentPair: ", pair.getId(), " symbol: ", pair.getSymbol());
      }

      return hasImbalance ? imbalance : 0D;

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      e.printStackTrace();
    }
    return 0;
  }

  public final static void stopAllSubscriptions() {
    for (final ExternalExchangeClient client : registerClients) {
      client.stop();
    }
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        final ExchangeSubscription subscription = parse(rs);

        onLoad(subscription);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "LiquiditySubscriptionCache.loadFromDB=", (long) count, ", time=",
          System.currentTimeMillis() - t0);
      loaderCounter.decrementAndGet();
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  public static void loadUpdated() {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_UPDATED);) {
      ps.setLong(1, (t0 - SIX_MINUTE));//overlap of 1 minute
      try (final ResultSet rs = ps.executeQuery();) {
        while (rs.next()) {
          final ExchangeSubscription subscription = parse(rs);

          onLoad(subscription);
          count++;
        }
      }
      LOGGER.info(LOG_FMT_4, "LiquiditySubscriptionCache.loadUpdated diff=", (long) count,
          ", time=", System.currentTimeMillis() - t0);
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  private static ExchangeSubscription parse(final ResultSet rs) throws Exception {
    final ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(rs.getInt(1));
    subscription.setExchange(rs.getString(2));
    subscription.setApiUser(
        !(rs.getString(3) == null || rs.getString(3).isEmpty()) ? EncryptDecrypt2.decrypt(
            rs.getString(3)) : null);
    subscription.setApiKey(
        !(rs.getString(4) == null || rs.getString(4).isEmpty()) ? EncryptDecrypt2.decrypt(
            rs.getString(4)) : null);
    subscription.setApiSecret(
        !(rs.getString(5) == null || rs.getString(5).isEmpty()) ? EncryptDecrypt2.decrypt(
            rs.getString(5)) : null);
    subscription.setStatus(rs.getInt(6));
    subscription.setCreated(rs.getLong(7));
    subscription.setExpires(rs.getLong(8));
    subscription.setFuturesEnabled(rs.getBoolean(9));
    subscription.setLeverage(rs.getBoolean(10));
    subscription.setLastUsedProxy(rs.getString(11));
    subscription.setApiKey2(
        !(rs.getString(12) == null || rs.getString(12).isEmpty()) ? EncryptDecrypt2.decrypt(
            rs.getString(12)) : null);
    subscription.setApiSecret2(
        !(rs.getString(13) == null || rs.getString(13).isEmpty()) ? EncryptDecrypt2.decrypt(
            rs.getString(13)) : null);

    return subscription;
  }

  private static boolean sendAlert() {
    LocalTime now = LocalTime.now();
    final int sendSuccessEmailAtHour = Context.getPositionManagerEodEmailAtHour();

    return now.getHour() == sendSuccessEmailAtHour && (now.getMinute() < 3);
  }

  private static class PositionData {

    private final String symbol;
    private double marketMakerBalance;
    private double totalExchangeBalance;
    private final Map<String, Double> exchangeBalances = new HashMap<>();

    private PositionData(String symbol) {
      this.symbol = symbol;
    }

    public void addExchangeBalance(final String exchange, final double balance) {
      double currentBalance = exchangeBalances.getOrDefault(exchange, 0D);
      exchangeBalances.put(exchange, (currentBalance + balance));
    }

    public void addToTotalBalance(final double balance) {
      totalExchangeBalance += balance;
    }

    public String getSymbol() {
      return symbol;
    }

    public double getMarketMakerBalance() {
      return marketMakerBalance;
    }

    public void setMarketMakerBalance(final double marketMakerBalance) {
      this.marketMakerBalance = marketMakerBalance;
    }

    public double getTotalExchangeBalance() {
      return totalExchangeBalance;
    }

    public Map<String, Double> getExchangeBalances() {
      return exchangeBalances;
    }
  }

}

