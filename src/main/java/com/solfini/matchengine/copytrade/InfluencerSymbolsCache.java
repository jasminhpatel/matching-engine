package com.solfini.matchengine.copytrade;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.db.ExternalDBManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;

/*
  InfluencerSymbolsCache loads data from marketprophit db.
 */
public class InfluencerSymbolsCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(InfluencerSymbolsCache.class);
  private static final String SELECT = "SELECT username,symbols,lastupdated FROM infulencer_symbol_state order by username asc;";
  private static final String UPSERT = "INSERT INTO infulencer_symbol_state (username, symbols, lastupdated) VALUES (?, ?, ?) ON CONFLICT(username) DO UPDATE set symbols = EXCLUDED.symbols, lastupdated = EXCLUDED.lastupdated;";

  private static final String SELECT_USERNAMES = "SELECT DISTINCT accountId FROM subscription_state order by accountid asc;";
  private static final ConcurrentHashMap<String, UserSymbols> USER_SYMBOLS = new ConcurrentHashMap<>();

  public static void onLoad(final UserSymbols userSymbols) {
    USER_SYMBOLS.put(userSymbols.getUsername(), userSymbols);
  }

  public static Set<String> getUserSymbols(final String username) {
    if (username == null)
      return null;

    final UserSymbols userSymbols = USER_SYMBOLS.computeIfAbsent(username.toUpperCase(), v -> new UserSymbols());

    return userSymbols.getSymbols();
  }

  public static void loadFromDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        final UserSymbols userSymbols = parse(rs);

        onLoad(userSymbols);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "InfluencerSymbolsCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      loaderCounter.decrementAndGet();
    } catch (final Exception e) {
      System.out.println(e.getMessage());
      LOGGER.error("error", e);
    }
  }

  public static void loadFromMarketProphit() {
    final Set<String> usernames = new HashSet<>();
    final Set<String> updateRequiredUsers = new HashSet<>();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_USERNAMES);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        usernames.add(rs.getString(1));
      }
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
    for (String username : usernames) {
      if (updateRequired(username)) {
        updateRequiredUsers.add(username);
      }
    }
    if (!updateRequiredUsers.isEmpty()) {
      final StringBuilder sql = new StringBuilder("SELECT USERNAME, TICKER FROM UserSpecificData WHERE USERNAME in (");
      for (String user : updateRequiredUsers) {
        sql.append("'").append(user).append("',");
      }
      sql.deleteCharAt(sql.length() - 1);
      sql.append(") AND TIME_FRAME='THREE_WEEKS' AND TS = (SELECT TS FROM UserSpecificData ORDER BY TS DESC LIMIT 1) ORDER BY TICKER LIMIT 20 OFFSET 0;");
      LOGGER.info("SQL: " + sql);
      try (final Connection conn = ExternalDBManager.getConnection();
          final PreparedStatement ps = conn.prepareStatement(sql.toString());) {
        try (final ResultSet rs = ps.executeQuery();) {
          while (rs.next()) {
            final String username = rs.getString(1);
            final String symbol = rs.getString(2);
            final UserSymbols userSymbols = USER_SYMBOLS.computeIfAbsent(username.toUpperCase(), v -> new UserSymbols());
            if (userSymbols.symbols == null || userSymbols.getLastUpdated() + TWO_DAY < System.currentTimeMillis()) {
              userSymbols.setSymbols(new HashSet<>());
            }
            userSymbols.getSymbols().add(symbol);
            userSymbols.setLastUpdated(System.currentTimeMillis());
          }
        }
      } catch (final Exception e) {
        LOGGER.error("error", e);
      }
      try (final Connection conn = DBManager.getConnection();
          final PreparedStatement ps = conn.prepareStatement(UPSERT);
          ) {
        for (String user : updateRequiredUsers) {
          final UserSymbols userSymbols = USER_SYMBOLS.get(user.toUpperCase());
          if (userSymbols != null && userSymbols.symbols != null) {
            ps.setString(1, user.toUpperCase());
            ps.setString(2, String.join(",", userSymbols.symbols));
            ps.setLong(3, userSymbols.lastUpdated);

            ps.addBatch();
          }
        }
        int[] affectedRecords = ps.executeBatch();
      } catch (final Exception e) {
        LOGGER.error("error", e);
      }
    }

  }

  private static UserSymbols parse(final ResultSet rs) throws SQLException {
    final UserSymbols userSymbols = new UserSymbols();
    userSymbols.setUsername(rs.getString(1));
    String symbols = rs.getString(2);
    if (symbols == null)
      symbols = "";
    userSymbols.setSymbols(Set.of(symbols.split(",")));
    userSymbols.setLastUpdated(rs.getLong(3));

    return userSymbols;
  }

  private static boolean updateRequired(final String username) {
    UserSymbols userSymbols = USER_SYMBOLS.get(username.toUpperCase());
    if (userSymbols == null) {
      return true;
    }
    return (userSymbols.getLastUpdated() + ONE_WEEK < System.currentTimeMillis());
  }

  public static class UserSymbols {
    String username;
    Set<String> symbols;
    long lastUpdated;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public Set<String> getSymbols() {
      return symbols;
    }

    public void setSymbols(Set<String> symbols) {
      this.symbols = symbols;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }
  }

  public static void main(String[] args) {
    Set userSymbols = getUserSymbols("Betixgg");
    System.out.println(userSymbols);
  }
}
