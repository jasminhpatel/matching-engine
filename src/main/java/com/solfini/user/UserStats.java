package com.solfini.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.StringUtil;

public class UserStats implements Constants {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserStats.class);
  private static final Map<Integer, UserStatRecord> records = new HashMap<>();
  private static final long startInputOffset = loadStartInputOffset();
  private static long lastTimestamp = 0;
  private static long lastExecReportOffset = 0;
  private static long lastPositionReportOffset = 0;

  private UserStats() {
    // Do not allow to be instantiated
  }

  public static final long loadStartInputOffset() {
    long lastInputOffset = 0;
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement("select max(inputoffset) from user_stat_log");
        final ResultSet rs = ps.executeQuery();) {
      if (rs.next()) {
        lastInputOffset = rs.getLong(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return 0;
    }
    return lastInputOffset;
  }

  public static void onMessage(final ExecutionReportMessage message) {
    if (message.getKafkaRecordOffset() <= startInputOffset) {
      return;
    }

    final UserStatRecord record = get(message.getAccount());
    if (message.getOpenOrderCount() != -1) {
      record.setOpenOrderCount(message.getOpenOrderCount());
    }

    if ((message.getOrdType() == OrdType.LIMIT) || (message.getOrdType() == OrdType.MARKET)) {
      switch (message.getExecType()) {
        case NEW: {
          if (message.getOrdStatus() == OrdStatus.NEW) {
            if (message.getSide() == Side.BUY) {
              record.addOrder(StringUtil.toDouble(message.getPrice(), message.getPriceScale()),
                  StringUtil.toDouble(message.getBestBidPx(), message.getBestPxScale()));
            } else {
              record.addOrder(StringUtil.toDouble(message.getPrice(), message.getPriceScale()),
                  StringUtil.toDouble(message.getBestAskPx(), message.getBestPxScale()));
            }
          }

          break;
        }
        case CANCELED: {
          record.addCancelOrder();
          break;
        }
        case TRADE: {
          final InstrumentPair instrumentPair = InstrumentCache.getPair(message.getSecurityId());
          if (instrumentPair != null) {
            final double notional = StringUtil.toDouble(message.getLastQty(), message.getLastQtyScale())
                * StringUtil.toDouble(message.getPrice(), message.getPriceScale())
                * InstrumentCache.get(instrumentPair.getQuotedId()).getIndexFeedUsdMark();

            if (message.getSide() != message.getAggressorSide()) {
              record.addMakerTrade(notional, notional * instrumentPair.getEffectiveVolumeScale());
            } else {
              record.addTakerTrade(notional, notional * instrumentPair.getEffectiveVolumeScale());
            }
          }

          final Instrument feeInstrument = InstrumentCache.get(message.getFeePositionId());
          if (feeInstrument != null) {
            record.addFees(feeInstrument.getId(), message.getFeePositionQuantityChange());
          }

          break;
        }
        default: {
          // Do nothing
        }
      }

      lastExecReportOffset = message.getKafkaRecordOffset();
      persist(message.getTimestamp(), lastExecReportOffset);
    }
  }

  public static void onMessage(final PositionReportMessage message) {
    if (message.getKafkaRecordOffset() <= startInputOffset) {
      return;
    }

    final UserStatRecord record = get(message.getUser().getId());
    record.update(message.getPositions());

    lastPositionReportOffset = message.getKafkaRecordOffset();
    persist(message.getTimestamp(), lastPositionReportOffset);
  }

  private static long trim(final long timestamp) {
    return timestamp / (1000 * Context.getUserStatsInterval());
  }

  public static void persist(final long timestamp, final long offset) {
    if (trim(lastTimestamp) == trim(timestamp)) {
      return;
    }

    persistSync(timestamp, offset);
  }

  public static void persist() {
    persistSync(System.currentTimeMillis(), Math.max(lastExecReportOffset, lastPositionReportOffset));
  }

  private static synchronized void persistSync(final long timestamp, final long offset) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "Persisting user statistics: timestamp=", lastTimestamp, ", offset=", offset);
    }

    final String sql1 = "INSERT INTO user_stat_log "
        + "(timestamp, userid, feetier, makervolume, takervolume, effectivevolume, makerfillcount, takerfillcount, "
        + "ordercount, cancelcount, openordercount, bestordercount0bps, bestordercount20bps, bestordercount50bps, "
        + "realizedpnl, unrealizedpnl, inputoffset) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    final String sql2 = "INSERT INTO user_stat_fee_log "
        + "(timestamp, userid, feeinstrumentid, feescale, feeamount) "
        + "VALUES (?, ?, ?, ?, ?)";
    try (
      final Connection connection = DBManager.getConnection();
      final PreparedStatement statement1 = connection.prepareStatement(sql1);
      final PreparedStatement statement2 = connection.prepareStatement(sql2);
    ) {
      connection.setAutoCommit(false);
      final Timestamp batchTimestamp = new Timestamp(lastTimestamp);
      for (final UserStatRecord record : records.values()) {
        final User user = UserCache.get(record.getUserId());
        statement1.setTimestamp(1, batchTimestamp);
        statement1.setInt(2, record.getUserId());
        statement1.setInt(3, user.getFeeTier());
        statement1.setDouble(4, record.getMakerVolume());
        statement1.setDouble(5, record.getTakerVolume());
        statement1.setDouble(6, record.getEffectiveVolume());
        statement1.setLong(7, record.getMakerFillCount());
        statement1.setLong(8, record.getTakerFillCount());
        statement1.setLong(9, record.getOrderCount());
        statement1.setLong(10, record.getCancelCount());
        statement1.setLong(11, record.getOpenOrderCount());
        statement1.setLong(12, record.getBestOrderCount0());
        statement1.setLong(13, record.getBestOrderCount20());
        statement1.setLong(14, record.getBestOrderCount50());
        statement1.setDouble(15, record.getRealizedPnl());
        statement1.setDouble(16, record.getUnrealizedPnl());
        statement1.setLong(17, offset);
        statement1.addBatch();

        final long[] fees = record.getFees();
        for (int i = 0; i < fees.length; i++) {
          if (fees[i] != 0) {
            statement2.setTimestamp(1, batchTimestamp);
            statement2.setInt(2, record.getUserId());
            statement2.setInt(3, i);
            statement2.setInt(4, InstrumentCache.get(i).getQuantityScale());
            statement2.setLong(5, fees[i]);
            statement2.addBatch();
          }
        }
      }

      statement1.executeBatch();
      statement2.executeBatch();
      connection.commit();

      Iterator<Integer> iterator = records.keySet().iterator();
      while (iterator.hasNext()) {
        final Integer userId = iterator.next();
        final UserStatRecord record = records.get(userId);
        if (record.getOpenOrderCount() == 0) {
          iterator.remove();
        } else {
          record.reset();
        }
      }

      lastTimestamp = timestamp;

    } catch (SQLException e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  private static UserStatRecord get(final int userId) {
    UserStatRecord record = records.get(userId);
    if (record == null) {
      record = new UserStatRecord(userId);
      records.put(record.getUserId(), record);
    }

    return record;
  }
}
