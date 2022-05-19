package com.solfini.matchengine.pricing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.persist.Persister;

public class DeribitCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitCache.class);

  public static final String SELECT_LATEST_PRICES =
      "select * from deribit_last_trade where id in (select max(id) as id from deribit_last_trade group by symbol) order by id desc";

  private static final DeribitLastTrade[] byInstrumentId = new DeribitLastTrade[1024];

  public static final void load() {
    int type = 0;
    try (final Connection connection = DBManager.getConnection();
        final PreparedStatement statement1 = connection.prepareStatement(SELECT_LATEST_PRICES);
        final ResultSet rs = statement1.executeQuery();) {

      while (rs.next()) {
        final long id = rs.getLong(1);
        final int pairId = rs.getInt(2);
        final int instrumentId = rs.getInt(3);
        final long timestamp = rs.getLong(4);
        final String symbol = rs.getString(5);
        final String direction = rs.getString(6);
        final String trade_id = rs.getString(7);
        final long trade_seq = rs.getInt(8);
        final int tick_direction = rs.getInt(9);
        final double price = rs.getInt(10);
        final double index_price = rs.getInt(11);
        final double amount = rs.getInt(12);
        final double iv = rs.getInt(13);
        final String created = rs.getString(14);

        if (instrumentId < byInstrumentId.length - 1) {
          if (byInstrumentId[instrumentId] == null)
            byInstrumentId[instrumentId] = new DeribitLastTrade(pairId, instrumentId, type, timestamp, symbol, direction, trade_id,
                trade_seq, tick_direction, price, index_price, amount, iv);
          else
            byInstrumentId[instrumentId].set(pairId, instrumentId, type, timestamp, symbol, direction, trade_id, trade_seq, tick_direction,
                price, index_price, amount, iv);
        }
      }
    } catch (Exception e) {
      LOGGER.error("error", e);
    }
  }

  public static final DeribitLastTrade lookupLast(final int instrumentId) {
    if (instrumentId < byInstrumentId.length - 1)
      return byInstrumentId[instrumentId];
    else
      return null;
  }
}
