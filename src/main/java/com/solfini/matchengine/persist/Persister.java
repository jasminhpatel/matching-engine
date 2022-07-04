package com.solfini.matchengine.persist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;

/**
 *
 * @author Chris Mack
 *
 */
public class Persister implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(Persister.class);
  private static final String SELECT_MAX_KAFKA_RECORD_OFFSET = "select max(kafkarecordoffset) from execution_report";
  private static long maxKafkaRecordOffset = loadMaxKafkaRecordOffset();
  private static boolean failed = false;
  private static boolean active = false;

  public static final String INSERT_EXEC_REPORT =
      "INSERT INTO execution_report (securityid,userid,clordid,symbol,side,ordtype,exectype,ordstatus,orderid,secondaryorderid,origorderid,execid,secondaryexecid,counterpartyid,ispositionsidecrossed,targetstrategy,orderqty,orderqtyscale,leavesqty,leavesqtyscale,cumqty,cumqtyscale,cumquoteqty,price,pricescale,avgpx,avgpxscale,lastpx,lastpxscale,lastqty,lastqtyscale,stoppx,stoppxscale,timeinforce,expiretime,timestampmillis,expiretimemillis,aggressorside,price2,price2scale,execrestatementreason,sourceseqnum,sourcesendtime,snapid,kafkarecordoffset,transactionid,islastmessageintransaction,decodedtime,matchtime,publishtime,notional,feePositionId,feePositionQuantityChange,feePositionQuantity,settlePositionId,settlePositionQuantityChange,settlePositionQuantity,isPaidToInsurance,isHidden,isLiquidation,submitterId,assetId,tokenId,selectId,quoteType,quoteTargetUserId) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static PreparedStatement psExecutionReport = null;

  private static ArrayList<Connection> connections = new ArrayList<>();


  private Persister() {
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

    if (failed || (psExecutionReport == null)) {

      // Close prepared statements
      closePreparedStatement(psExecutionReport);

      // Close any active connections as we are about to create new ones.
      for (final Connection conn : connections) {
        try {
          conn.close();
        } catch (SQLException e) {
          LOGGER.warn(ERROR_LOG, e);
        }
      }
      connections.clear();

      psExecutionReport = buildPreparedStatement(INSERT_EXEC_REPORT);
    }

    if ((psExecutionReport != null)) {
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

  public static final long loadMaxKafkaRecordOffset() {
    long maxKafkaRecordOffset = 0;
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_MAX_KAFKA_RECORD_OFFSET);
        final ResultSet rs = ps.executeQuery();) {
      if (rs.next()) {
        maxKafkaRecordOffset = rs.getLong(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return 0;
    }
    return maxKafkaRecordOffset;
  }


  public static final long MULTIPLIER = 100_000_000;

  public static final void onMessage(final ExecutionReportMessage message) {
    if (message.getKafkaRecordOffset() > getMaxKafkaRecordOffset()) {
      active = true;
    }
    if (!active) {
      LOGGER.warn(Constants.WARN_LOG, "Persist message kafka offset: " + message.getKafkaRecordOffset() +
          " max previous kafka offset: " + getMaxKafkaRecordOffset());
      return;
    }

    try {
      String clOrdId = message.getClOrdId();
      if (null != clOrdId && clOrdId.length() > 38) {
        clOrdId = clOrdId.substring(0, 38);
      }

      psExecutionReport.setInt(1, message.getSecurityId());
      psExecutionReport.setInt(2, message.getAccount());
      psExecutionReport.setString(3, clOrdId);
      psExecutionReport.setString(4, message.getSymbol());
      psExecutionReport.setString(5, message.getSide() == null ? null : message.getSide().toString());
      psExecutionReport.setString(6, message.getOrdType() == null ? null : message.getOrdType().toString());
      psExecutionReport.setString(7, message.getExecType() == null ? null : message.getExecType().toString());
      psExecutionReport.setString(8, message.getOrdStatus() == null ? null : message.getOrdStatus().toString());
      psExecutionReport.setLong(9, message.getOrderId());
      psExecutionReport.setLong(10, message.getSecondaryOrderId());
      psExecutionReport.setLong(11, message.getOrigOrderId());
      psExecutionReport.setLong(12, message.getExecId());
      psExecutionReport.setLong(13, message.getSecondaryExecId());
      psExecutionReport.setLong(14, message.getCounterpartyId());
      psExecutionReport.setBoolean(15, message.isPositionSideCrossed());
      psExecutionReport.setInt(16, message.getTargetStrategy());
      psExecutionReport.setLong(17, message.getOrderQty());
      psExecutionReport.setInt(18, message.getOrderQtyScale());
      psExecutionReport.setLong(19, message.getLeavesQty());
      psExecutionReport.setInt(20, message.getLeavesQtyScale());
      psExecutionReport.setLong(21, message.getCumQty());
      psExecutionReport.setInt(22, message.getCumQtyScale());
      psExecutionReport.setLong(23, message.getCumQty()); // cumquoteqty, do we need this?
      psExecutionReport.setLong(24, message.getPrice());
      psExecutionReport.setInt(25, message.getPriceScale());
      psExecutionReport.setLong(26, message.getAvgPx());
      psExecutionReport.setInt(27, message.getAvgPxScale());
      psExecutionReport.setLong(28, message.getLastPx());
      psExecutionReport.setInt(29, message.getLastPxScale());
      psExecutionReport.setLong(30, message.getLastQty());
      psExecutionReport.setInt(31, message.getLastQtyScale());
      psExecutionReport.setLong(32, message.getStopPx());
      psExecutionReport.setInt(33, message.getStopPxScale());
      psExecutionReport.setString(34, message.getTimeInForce() == null ? null : message.getTimeInForce().toString());
      psExecutionReport.setLong(35, message.getExpireTime());
      psExecutionReport.setLong(36, message.getTimestamp());
      psExecutionReport.setLong(37, message.getExpireTimeMillis());
      psExecutionReport.setString(38, message.getAggressorSide() == null ? null : message.getAggressorSide().toString());
      psExecutionReport.setLong(39, message.getPrice2());
      psExecutionReport.setInt(40, message.getPrice2Scale());
      psExecutionReport.setString(41, message.getExecRestatementReason() == null ? null : message.getExecRestatementReason().toString());
      psExecutionReport.setLong(42, message.getSourceSeqNum());
      psExecutionReport.setLong(43, message.getSourceSendTime());
      psExecutionReport.setLong(44, message.getSnapId());
      psExecutionReport.setLong(45, message.getKafkaRecordOffset());
      psExecutionReport.setLong(46, message.getTransactionId());
      psExecutionReport.setBoolean(47, message.isLastMessageInTransaction());
      psExecutionReport.setLong(48, message.getDecodedTime());
      psExecutionReport.setLong(49, message.getMatchTime());
      psExecutionReport.setLong(50, System.currentTimeMillis());
      psExecutionReport.setDouble(51, message.getNotional());
      psExecutionReport.setInt(52, message.getFeePositionId());
      psExecutionReport.setLong(53, message.getFeePositionQuantityChange());
      psExecutionReport.setLong(54, message.getFeePositionQuantity());
      psExecutionReport.setInt(55, message.getSettlePositionId());
      psExecutionReport.setLong(56, message.getSettlePositionQuantityChange());
      psExecutionReport.setLong(57, message.getSettlePositionQuantity());

      psExecutionReport.setBoolean(58, message.isPaidToInsurance());
      psExecutionReport.setBoolean(59, message.isHidden());
      psExecutionReport.setBoolean(60, message.isLiquidation());
      psExecutionReport.setInt(61, message.getSubmitterId());
      psExecutionReport.setLong(62, message.getAssetId());
      psExecutionReport.setInt(63, message.getTokenId());
      psExecutionReport.setLong(64, message.getSelectId());
      psExecutionReport.setString(65, message.getQuoteType() == null ? null : message.getQuoteType().toString());
      psExecutionReport.setLong(66, message.getQuoteTargetUserId());

      psExecutionReport.addBatch();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;
    }
  }

  public static final boolean commitBatch() {
    try {
      psExecutionReport.executeBatch();
      psExecutionReport.getConnection().commit();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;

      try {
        psExecutionReport.getConnection().rollback();
      } catch (Exception e1) {
        LOGGER.error(ERROR_LOG, e1);
      }
    }

    return !failed;
  }

  public static long getMaxKafkaRecordOffset() {
    return maxKafkaRecordOffset;
  }


}
