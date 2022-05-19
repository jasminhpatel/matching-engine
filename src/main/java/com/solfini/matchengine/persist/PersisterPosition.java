package com.solfini.matchengine.persist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class PersisterPosition implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PersisterPosition.class);
  private static final String SELECT_REPORT_ID = "select max(reportId) from POSITION_REPORT";
  private static long maxPositionReportId = loadMaxPositionReportId();
  private static boolean failed = false;
  private static boolean active = false;

  public static final String INSERT_POS_REPORT =
      "INSERT INTO position_report (reportid,userid,usdvalue,usdMarginableValue,usdnotionalpositionvalue,usdmaxexposurepositionandopenordersvalue,usdopenordersrequiredvalue,usdmarginvalue,usdmarginrequiredvalue,usdmarginmaintvalue,leverageratio,usdunrealized,sourceseqnum,sourcesendtime,snapid,kafkarecordoffset,transactionid,islastmessageintransaction,decodedtime,matchtime,publishtime,txnType,txnId,execId,orderId,instrumentId1,qty1,change1,instrumentId2,qty2,change2,instrumentId3,qty3,change3,instrumentId4,qty4,change4) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  public static final String INSERT_POS_REPORT_BALANCE =
      "INSERT INTO position_report_balance(reportid,instrumentid,assettype,quantity,quantityscale,availablequantity,availablequantityscale,usdcostbasis,usdcostbasisscale,usdavgcostbasis,usdavgcostbasisscale,usdvalue,usdunrealized,usdrealized,quotedusdmark,settlecoinusdmark,settlecoinunrealized,settlecoinrealized,bankruptpriceint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static PreparedStatement psPosReport = null;
  private static PreparedStatement psPosReportBalance = null;

  private static ArrayList<Connection> connections = new ArrayList<>();

  private PersisterPosition() {
    // Make sure this class is not instantiated
  }

  private static void closePreparedStatement(final PreparedStatement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        LOGGER.warn(ERROR_LOG, e);
      }
    }
  }

  public static final boolean startBatch() {

    if (failed || (psPosReport == null) || (psPosReportBalance == null)) {

      // Close prepared statements
      closePreparedStatement(psPosReport);
      closePreparedStatement(psPosReportBalance);

      // Close any active connections as we are about to create new ones.
      for (final Connection conn : connections) {
        try {
          conn.close();
        } catch (SQLException e) {
          LOGGER.warn(ERROR_LOG, e);
        }
      }
      connections.clear();

      psPosReport = buildPreparedStatement(INSERT_POS_REPORT);
      psPosReportBalance = buildPreparedStatement(INSERT_POS_REPORT_BALANCE);
    }

    if ((psPosReport != null) && (psPosReportBalance != null)) {
      failed = false;
    }

    return !failed;
  }

  public static final PreparedStatement buildPreparedStatement(final String sql) {
    try {
      final Connection conn = DBManager.getConnection(); // we don't close this conn
      connections.add(conn); // Save the connection, so we can close later
      conn.setAutoCommit(false);
      return conn.prepareStatement(sql);
    } catch (SQLException e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;
    }
    return null;
  }

  public static final long loadMaxPositionReportId() {
    long maxExecReportId = 0;
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_REPORT_ID);
        final ResultSet rs = ps.executeQuery();) {
      if (rs.next()) {
        maxExecReportId = rs.getLong(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return 0;
    }
    return maxExecReportId;
  }

  public static final void onMessage(final PositionReportMessage message) {
    if (message.getKafkaRecordOffset() > Persister.getMaxKafkaRecordOffset()) {
      active = true;
    }
    if (!active) {
      return;
    }

    maxPositionReportId++;
    try {
      final User user = message.getUser();
      final User cachedUser = UserCache.get(user.getId());
      final PositionReportMessage prevPositionReport = (cachedUser == null) ? null : cachedUser.getPrevPositionReport();

      psPosReport.setLong(1, maxPositionReportId);
      psPosReport.setInt(2, message.getUser().getId());
      psPosReport.setDouble(3, user.getUsdValue());
      psPosReport.setDouble(4, user.getUsdMarginableValue());
      psPosReport.setDouble(5, user.getUsdNotionalPositionValue());
      psPosReport.setDouble(6, user.getUsdMaxExposurePositionAndOpenOrdersValue());
      psPosReport.setDouble(7, user.getUsdOpenOrdersRequiredValue());
      psPosReport.setDouble(8, user.getUsdMarginValue());
      psPosReport.setDouble(9, user.getUsdMarginRequiredValue());
      psPosReport.setDouble(10, user.getUsdMarginMaintValue());
      psPosReport.setDouble(11, user.getLeverageRatio());
      psPosReport.setDouble(12, user.getUsdUnrealized());
      psPosReport.setLong(13, message.getSourceSeqNum());
      psPosReport.setLong(14, message.getSourceSendTime());
      psPosReport.setLong(15, message.getSnapId());
      psPosReport.setLong(16, message.getKafkaRecordOffset());
      psPosReport.setLong(17, message.getTransactionId());
      psPosReport.setBoolean(18, message.isLastMessageInTransaction());
      psPosReport.setLong(19, message.getDecodedTime());
      psPosReport.setLong(20, message.getMatchTime());
      psPosReport.setLong(21, System.currentTimeMillis());
      psPosReport.setInt(22, message.getTxnType());
      psPosReport.setLong(23, message.getTxnId());
      psPosReport.setLong(24, message.getExecId());
      psPosReport.setLong(25, message.getOrderId());
      int index = 25;

      // add up to 4 positions to position report with their diff from previous report
      if (prevPositionReport != null) {
        final Position[] positions = message.getPositions();
        final Position[] prevPositions = prevPositionReport.getPositions();
        if (positions != null && prevPositions != null) {
          for (int i = 0; i < positions.length; i++) {
            if (index >= 35)
              break;
            final Position position = positions[i];
            final Position prevPosition = (prevPositions.length > i) ? prevPositions[i] : null;

            if (position != null && prevPosition == null) {
              // System.out.println("skip position=" + position.getInstrumentId());
              psPosReport.setInt(++index, position.getInstrumentId());
              psPosReport.setLong(++index, position.getQuantity());
              psPosReport.setLong(++index, position.getQuantity());
            } else if (position == null && prevPosition != null) {
              // System.out.println("skip prevPosition=" + prevPosition.getInstrumentId());
              psPosReport.setInt(++index, prevPosition.getInstrumentId());
              psPosReport.setLong(++index, 0);
              psPosReport.setLong(++index, -prevPosition.getQuantity());
            } else if (position != null && prevPosition != null) {
              // System.out.println("match position=" + position.getInstrumentId() + ", prevPosition=" + prevPosition.getInstrumentId());
              if (position.getQuantity() != prevPosition.getQuantity()) {
                psPosReport.setInt(++index, position.getInstrumentId());
                psPosReport.setLong(++index, position.getQuantity());
                psPosReport.setLong(++index, position.getQuantity() - prevPosition.getQuantity());
              }
            }
          }
        }
      }

      while (index < 35) {
        psPosReport.setInt(++index, 0);
        psPosReport.setLong(++index, 0);
        psPosReport.setLong(++index, 0);
      }

      psPosReport.addBatch();

      if (cachedUser != null)
        cachedUser.setPrevPositionReport(message);

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;
    }

    final Position[] positions = message.getPositions();
    if (positions == null)
      return;

    for (int i = 0; i < positions.length; i++) {
      if (positions[i] != null)
        onMessage(positions[i], maxPositionReportId);
      // TODO: put this on a queue
    }
  }

  public static final long MULTIPLIER = 100_000_000;

  private static final void onMessage(final Position position, final long maxPositionReportId) {
    try {
      psPosReportBalance.setLong(1, maxPositionReportId);
      psPosReportBalance.setLong(2, position.getInstrumentId());
      psPosReportBalance.setString(3, position.getAssetType() == null ? null : position.getAssetType().toString());
      psPosReportBalance.setLong(4, position.getQuantity());
      psPosReportBalance.setInt(5, 0); // QuantityScale
      psPosReportBalance.setLong(6, position.getAvailableQuantity());
      psPosReportBalance.setInt(7, 0); // AvailableQuantityScale
      psPosReportBalance.setLong(8, (long) (position.getUsdCostBasis() * MULTIPLIER));
      psPosReportBalance.setInt(9, 8); // UsdCostBasis
      psPosReportBalance.setLong(10, position.getUsdAvgCostBasis());
      psPosReportBalance.setInt(11, position.getUsdAvgCostBasisScale()); // UsdAvgCostBasisSCale
      psPosReportBalance.setDouble(12, position.getUsdValue());
      psPosReportBalance.setDouble(13, position.getUsdUnrealized());
      psPosReportBalance.setDouble(14, position.getUsdRealizedDouble());
      psPosReportBalance.setDouble(15, position.getQuotedUsdMark());
      psPosReportBalance.setDouble(16, position.getSettleCoinUsdMark());
      psPosReportBalance.setDouble(17, position.getSettleCoinUnrealized());
      psPosReportBalance.setDouble(18, position.getSettleCoinRealized());
      psPosReportBalance.setInt(19, position.getBankruptPriceInt());

      psPosReportBalance.addBatch();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;
    }
  }

  public static final boolean commitBatch() {
    try {
      psPosReport.executeBatch();
      psPosReport.getConnection().commit();

      psPosReportBalance.executeBatch();
      psPosReportBalance.getConnection().commit();

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;

      try {
        psPosReport.getConnection().rollback();
      } catch (Exception e1) {
        LOGGER.error(ERROR_LOG, e1);
      }

      try {
        psPosReportBalance.getConnection().rollback();
      } catch (Exception e1) {
        LOGGER.error(ERROR_LOG, e1);
      }
    }

    return !failed;
  }


}
