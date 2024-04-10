package com.solfini.matchengine.persist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.InfluencerSubscription;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;

/**
 * @author Chris Mack
 */
public class Persister implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(Persister.class);
  private static final String SELECT_MAX_KAFKA_RECORD_OFFSET = "select max(kafkarecordoffset) from execution_report";
  private static long maxKafkaRecordOffset = loadMaxKafkaRecordOffset();
  private static boolean failed = false;
  private static boolean active = false;

  public static final String INSERT_EXEC_REPORT =
      "INSERT INTO execution_report (securityid,userid,clordid,symbol,side,ordtype,exectype,ordstatus,orderid,secondaryorderid,origorderid,execid,secondaryexecid,counterpartyid,ispositionsidecrossed,targetstrategy,orderqty,orderqtyscale,leavesqty,leavesqtyscale,cumqty,cumqtyscale,cumquoteqty,price,pricescale,avgpx,avgpxscale,lastpx,lastpxscale,lastqty,lastqtyscale,stoppx,stoppxscale,timeinforce,expiretime,timestampmillis,expiretimemillis,aggressorside,price2,price2scale,execrestatementreason,sourceseqnum,sourcesendtime,snapid,kafkarecordoffset,transactionid,islastmessageintransaction,decodedtime,matchtime,publishtime,notional,feePositionId,feePositionQuantityChange,feePositionQuantity,settlePositionId,settlePositionQuantityChange,settlePositionQuantity,isPaidToInsurance,isHidden,isLiquidation,submitterId,assetId,tokenId,groupAssetId,selectId,quoteType,quoteTargetUserId) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
  public static final String INSERT_COPY_TRADE =
      "INSERT INTO copy_trade_state (userid,securityid,clordid,platform,accountid,exchange,created,side,ordtype,timeinforce,orderqty,orderqtyscale,price,pricescale,\"result\",kafkarecordoffset,basesymbol,quotedsymbol,subscriptionId,externalId,originalAmount,cumulativeAmount,status,origclordid,signalpercentage,signalpercentagescale,signalprice,signalpricescale,xquantity,xprice,istoclose,closeClOrdId,closed,borrowedAmount,repaid,futuresEnabled,tradeValue) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
  public static final String UPDATE_COPY_TRADE =
      "UPDATE copy_trade_state set closed=true,closeClOrdId=?,price=?,originalamount=?,cumulativeamount=?,status=? WHERE clordid=? AND subscriptionId=?;";
  private static final String UPDATE_SUBSCRIPTION_STATUS = "UPDATE subscription_state SET hasPendingClose=?,lastUsedProxy=?,availableMaxAmount=? where id=?";
  private static PreparedStatement psExecutionReport = null;
  private static PreparedStatement psCopyTrade = null;
  private static PreparedStatement psUpdateCopyTrade = null;
  private static PreparedStatement psUpdateSubscription = null;

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

    if (failed || psExecutionReport == null || psCopyTrade == null || psUpdateCopyTrade == null || psUpdateSubscription == null) {
      // Close prepared statements
      closePreparedStatement(psExecutionReport);
      closePreparedStatement(psCopyTrade);
      closePreparedStatement(psUpdateCopyTrade);
      closePreparedStatement(psUpdateSubscription);

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
      psCopyTrade = buildPreparedStatement(INSERT_COPY_TRADE);
      psUpdateCopyTrade = buildPreparedStatement(UPDATE_COPY_TRADE);
      psUpdateSubscription = buildPreparedStatement(UPDATE_SUBSCRIPTION_STATUS);
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

  public static final void onMessage(final ExecutionReportMessage message) {
    if (message.getKafkaRecordOffset() > getMaxKafkaRecordOffset()) {
      active = true;
    }
    if (!active) {
      LOGGER.warn(Constants.WARN_LOG,
          "Persist ExecutionReportMessage message kafka offset: " + message.getKafkaRecordOffset() + " max previous kafka offset: " + getMaxKafkaRecordOffset());
      return;
    }
    LOGGER.info(
        "Persist message received (ExecutionReportMessage): " + message.getClOrdId() + " active: " + active + " symbol: " + message.getSymbol());
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
      psExecutionReport.setLong(64, message.getGroupAssetId());
      psExecutionReport.setLong(65, message.getSelectId());
      psExecutionReport.setString(66, message.getQuoteType() == null ? null : message.getQuoteType().toString());
      psExecutionReport.setLong(67, message.getQuoteTargetUserId());

      psExecutionReport.addBatch();
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(ERROR_LOG, e);
      failed = true;
    }
  }

  public static final void onMessage(final CopyTrade message) {
    //todo single input message generate multiple copy trades. handle kafka record offset accordingly
    //but here we have only consider the kafka offset of execution reports.
    //    if (message.getKafkaRecordOffset() > getMaxKafkaRecordOffset()) {
    active = true;
    //    }
    if (!active) {
      LOGGER.warn(Constants.WARN_LOG,
          "Persist CopyTrade message kafka offset: " + message.getKafkaRecordOffset() + " max previous kafka offset: " + getMaxKafkaRecordOffset());
      return;
    }
    try {
      String clOrdId = message.getClOrdId();
      if (null != clOrdId && clOrdId.length() > 38) {
        clOrdId = clOrdId.substring(0, 38);
      }
      if (!message.isToClose() && message.isClosed()) {// update close status in the open order
        LOGGER.info("Update message received (CopyTrade): " + message.getClOrdId() + " active: " + active);
        psUpdateCopyTrade.setString(1, message.getCloseClOrdId());
        psUpdateCopyTrade.setLong(2, message.getPrice());
        psUpdateCopyTrade.setDouble(3, message.getOriginalAmount());
        psUpdateCopyTrade.setDouble(4, message.getCumulativeAmount());
        psUpdateCopyTrade.setString(5, message.getStatus());
        psUpdateCopyTrade.setString(6, clOrdId);
        psUpdateCopyTrade.setLong(7, message.getSubscriptionId());

        psUpdateCopyTrade.addBatch();
      } else {
        LOGGER.info("Persist message received (CopyTrade): " + message.getClOrdId() + " active: " + active);
        psCopyTrade.setInt(1, message.getUserId());
        psCopyTrade.setInt(2, message.getSecurityId());
        psCopyTrade.setString(3, clOrdId);
        psCopyTrade.setString(4, message.getPlatform());
        psCopyTrade.setString(5, message.getAccountId());
        psCopyTrade.setString(6, message.getExchange());
        psCopyTrade.setLong(7, message.getCreated());
        psCopyTrade.setString(8, message.getSide() == null ? null : message.getSide().toString());
        psCopyTrade.setString(9, message.getOrdType() == null ? null : message.getOrdType().toString());
        psCopyTrade.setString(10, message.getTimeInForce() == null ? null : message.getTimeInForce().toString());
        psCopyTrade.setLong(11, message.getOrderQty());
        psCopyTrade.setInt(12, message.getOrderQtyScale());
        psCopyTrade.setLong(13, message.getPrice());
        psCopyTrade.setInt(14, message.getPriceScale());
        psCopyTrade.setString(15, message.getResult());
        psCopyTrade.setLong(16, message.getKafkaRecordOffset());
        psCopyTrade.setString(17, message.getBaseSymbol());
        psCopyTrade.setString(18, message.getQuotedSymbol());
        psCopyTrade.setLong(19, message.getSubscriptionId());
        psCopyTrade.setString(20, message.getExternalId());
        psCopyTrade.setDouble(21, message.getOriginalAmount());
        psCopyTrade.setDouble(22, message.getCumulativeAmount());
        psCopyTrade.setString(23, message.getStatus());
        psCopyTrade.setString(24, message.getOrigClOrdId());
        psCopyTrade.setLong(25, message.getSignalPercentage());
        psCopyTrade.setShort(26, message.getSignalPercentageScale());
        psCopyTrade.setLong(27, message.getSignalPrice());
        psCopyTrade.setShort(28, message.getSignalPriceScale());
        psCopyTrade.setString(29, message.getxQuantity() != null ? message.getxQuantity().toString() : null);
        psCopyTrade.setString(30, message.getxPrice() != null ? message.getxPrice().toString() : null);
        psCopyTrade.setBoolean(31, message.isToClose());
        psCopyTrade.setString(32, message.getCloseClOrdId());
        psCopyTrade.setBoolean(33, message.isClosed());
        psCopyTrade.setDouble(34, message.getBorrowedAmount());
        psCopyTrade.setBoolean(35, message.isRepaid());
        psCopyTrade.setBoolean(36, message.isFuturesEnabled());
        psCopyTrade.setDouble(37, message.getTradeValue());

        psCopyTrade.addBatch();
      }

    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(ERROR_LOG, e);
      failed = true;
    }
  }

  public static final void onMessage(final InfluencerSubscription message) {
    //todo single input message generate multiple copy trades. handle kafka record offset accordingly
    //but here we have only consider the kafka offset of execution reports.
    //if (message.getKafkaRecordOffset() >= getMaxKafkaRecordOffset()) {
    active = true;
    //}
    if (!active) {
      LOGGER.warn(Constants.WARN_LOG,
          "Persist InfluenceSubscription message kafka offset: " + message.getKafkaRecordOffset() + " max previous kafka offset: " + getMaxKafkaRecordOffset());
      return;
    }
    try {
      LOGGER.info("Update message received (InfluenceSubscription): " + message.getId() + " active: " + active);
      psUpdateSubscription.setBoolean(1, message.isHasPendingClose());
      psUpdateSubscription.setString(2, message.getLastUsedProxy());
      psUpdateSubscription.setLong(3, message.getAvailableMaxAmount());

      psUpdateSubscription.setLong(4, message.getId());

      psUpdateSubscription.addBatch();

    } catch (Exception e) {
      e.printStackTrace();
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

    try {
      psCopyTrade.executeBatch();
      psCopyTrade.getConnection().commit();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;

      try {
        psCopyTrade.getConnection().rollback();
      } catch (Exception e1) {
        LOGGER.error(ERROR_LOG, e1);
      }
    }

    try {
      psUpdateCopyTrade.executeBatch();
      psUpdateCopyTrade.getConnection().commit();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;

      try {
        psUpdateCopyTrade.getConnection().rollback();
      } catch (Exception e1) {
        LOGGER.error(ERROR_LOG, e1);
      }
    }

    try {
      psUpdateSubscription.executeBatch();
      psUpdateSubscription.getConnection().commit();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      failed = true;

      try {
        psUpdateSubscription.getConnection().rollback();
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
