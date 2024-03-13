package com.solfini.matchengine.copytrade;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.util.EncryptDecrypt2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;

public class InfluencerSubscriptionCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(InfluencerSubscriptionCache.class);
  private static final String SELECT = "SELECT id,userId,platform,accountId,exchange,apiUser,apiSecret,percentage,maxAmount,status,created,expires,apiKey,updated,preferredQuoteCurrency,preferredCurrencies,inverseTrade,amountWithLeverage,futuresEnabled,hasPendingClose,lastUsedProxy FROM subscription_state order by id asc";
  private static final String SELECT_UPDATED = "SELECT id,userId,platform,accountId,exchange,apiUser,apiSecret,percentage,maxAmount,status,created,expires,apiKey,updated,preferredQuoteCurrency,preferredCurrencies,inverseTrade,amountWithLeverage,futuresEnabled,hasPendingClose,lastUsedProxy FROM subscription_state WHERE updated > ? order by id asc";
  private static final ConcurrentHashMap<Long, InfluencerSubscription> SUBSCRIPTIONS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, InfluencerSubscription>> SUBSCRIBED_USERS = new ConcurrentHashMap<>();

  public static void onLoad(final InfluencerSubscription subscription) {
    final String key = subscription.getKey();
    final ConcurrentHashMap<Long, InfluencerSubscription> subscriptions = SUBSCRIBED_USERS.computeIfAbsent(key, v -> new ConcurrentHashMap<>());
    SUBSCRIPTIONS.put(subscription.getId(), subscription);
    if (subscription.getExpires() <= System.currentTimeMillis() || subscription.getStatus() != 1 || subscription.getMaxAmount() <= 0) {
      subscriptions.remove(subscription.getId());
    } else {
      subscriptions.put(subscription.getId(), subscription);
    }
  }

  public static InfluencerSubscription get(final long id) {
    return SUBSCRIPTIONS.get(id);
  }

  public static Collection<InfluencerSubscription> getSubscriptions(final String platform, final String accountId) {
    final String key = (platform + "_" + accountId).toLowerCase();
    final ConcurrentHashMap<Long, InfluencerSubscription> subscriptions = SUBSCRIBED_USERS.get(key);
    if (subscriptions != null)
      return subscriptions.values();

    return null;
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement userPS = conn.prepareStatement(SELECT);
        final ResultSet rs = userPS.executeQuery();) {
      while (rs.next()) {
        final InfluencerSubscription subscription = parse(rs);

        onLoad(subscription);
        count++;
      }
      LOGGER.info(LOG_FMT_1, "InfluencerSubscriptionCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
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
          final InfluencerSubscription subscription = parse(rs);

          onLoad(subscription);
          count++;
        }
      }
      LOGGER.info(LOG_FMT_1, "InfluencerSubscriptionCache.loadUpdated diff=", (long) count, ", time=", System.currentTimeMillis() - t0);
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  private static InfluencerSubscription parse(final ResultSet rs) throws SQLException {
    final InfluencerSubscription subscription = new InfluencerSubscription();
    subscription.setId(rs.getLong(1));
    subscription.setUserId(rs.getInt(2));
    subscription.setPlatform(rs.getString(3));
    subscription.setAccountId(rs.getString(4));
    subscription.setExchange(rs.getString(5));
    subscription.setApiUser(!(rs.getString(6) == null || rs.getString(6).isEmpty()) ? EncryptDecrypt2.decrypt(rs.getString(6)): null);
    subscription.setApiSecret(!(rs.getString(7) == null || rs.getString(7).isEmpty()) ? EncryptDecrypt2.decrypt(rs.getString(7)): null);
    subscription.setPercentage(rs.getInt(8));
    subscription.setMaxAmount(rs.getLong(9));
    subscription.setStatus(rs.getInt(10));
    subscription.setCreated(rs.getLong(11));
    subscription.setExpires(rs.getLong(12));
    subscription.setApiKey(!(rs.getString(13) == null || rs.getString(13).isEmpty()) ? EncryptDecrypt2.decrypt(rs.getString(13)): null);
    subscription.setUpdated(rs.getLong(14));
    subscription.setPreferredQuoteCurrency(rs.getString(15));
    subscription.setPreferredCurrencies(rs.getString(16));
    subscription.setInverseTrade(rs.getInt(17));
    subscription.setAmountWithLeverage(rs.getLong(18));
    subscription.setFuturesEnabled(rs.getBoolean(19));
    subscription.setHasPendingClose(rs.getBoolean(20));
    subscription.setLastUsedProxy(rs.getString(21));

    return subscription;
  }
}
