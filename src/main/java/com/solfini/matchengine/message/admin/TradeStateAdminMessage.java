package com.solfini.matchengine.message.admin;

import java.util.Iterator;
import java.util.Map;
import com.solfini.common.AdminMessage;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.TradeStateAdminMessageDecoder;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.AssetGroupCache;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.report.check.ReconciliationResult;
import com.solfini.user.UserCache;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class TradeStateAdminMessage extends AdminMessage {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(TradeStateAdminMessage.class);

  public static final int BEGIN = 1;
  public static final int END = 2;

  private MarketStatus marketStatus;
  private int securityId;
  private int status; // 1==BEGIN, 2=END
  private long snapId;
  private long inputKafkaRecordOffset;

  public TradeStateAdminMessage() {}

  public TradeStateAdminMessage(final MarketStatus marketStatus, final int securityId) {
    this.marketStatus = marketStatus;
    this.securityId = securityId;
  }

  public TradeStateAdminMessage(final TradeStateAdminMessageDecoder TRADE_STATE_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.marketStatus = TRADE_STATE_DECODER.marketStatus();
    this.securityId = TRADE_STATE_DECODER.securityId();
    this.triggerTimeMillis = TRADE_STATE_DECODER.triggerTimeMillis();
    this.routeToDestination = TRADE_STATE_DECODER.routeToDestination();
    this.senderInstanceId = TRADE_STATE_DECODER.senderInstanceId();
    this.inputKafkaRecordOffset = TRADE_STATE_DECODER.kafkaRecordOffset();
  }

  public TradeStateAdminMessage(final TradeStateAdminMessage orig) {
    this.marketStatus = orig.marketStatus;
    this.securityId = orig.securityId;
    this.snapId = orig.snapId;
    this.connectionId = orig.connectionId;
    this.user = orig.user;
    this.sourceSendTime = orig.sourceSendTime;
    this.sourceSeqNum = orig.sourceSeqNum;
    this.senderInstanceId = orig.senderInstanceId;
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.TRADE_STATE_ADMIN;
  }

  public final MarketStatus getMarketStatus() {
    return marketStatus;
  }

  public final void setMarketStatus(final MarketStatus marketStatus) {
    this.marketStatus = marketStatus;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final long getInputKafkaRecordOffset() {
    return inputKafkaRecordOffset;
  }

  @Override
  public void onMatcher() {
    // Ignore null and failover messages
    if (null == marketStatus || MarketStatus.FAILOVER == marketStatus) {
      return;
    }

    // auction states
    if (MarketStatus.OPEN_AUCTION == marketStatus || MarketStatus.CLOSE_AUCTION == marketStatus
        || MarketStatus.CANCEL_AUCTION == marketStatus) {
      if (securityId > 0) {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(securityId);
        if (instrumentPair != null) {
          instrumentPair.changeState(marketStatus, snapId, this);
          Context.getMatcherToPublisherQueue().addGuaranteed(this);
        }
      }
      return;
    }

    if (MarketStatus.RESTATE != marketStatus) {
      return;
    }

    // Trigger the snapshot only if this message is destined for this matching engine instance,
    // or for all instances,
    // or for the primary instance and this is the current primary,
    // or for all secondary instances and this is a secondary.
    if (!Context.getInstanceId().equals(getRouteToDestination()) && !getRouteToDestination().equalsIgnoreCase("ALL")
        && !(getRouteToDestination().equalsIgnoreCase("PRIMARY") && (Context.getMarketStatus() == MarketStatus.OPEN))
        && !(getRouteToDestination().equalsIgnoreCase("SECONDARY") && (Context.getMarketStatus() == MarketStatus.DR_MODE))) {
      LOGGER.info("Snapshot request relayed: routeToDestination=" + getRouteToDestination());
      Context.getMatcherToPublisherQueue().add(this);
      return;
    }

    this.snapId = TimeUtil.getTime();
    this.status = BEGIN;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, ">>1onMatcher TradeStateAdminMessage=", this, " t=", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss(),
          ", snapId=", snapId, T0_EQ, System.currentTimeMillis());
    }
    Context.getMatcherToPublisherQueue().add(this);

    // restate instruments, pairs, fees
    InstrumentCache.restateAllInstrumentsPairsAndFees(snapId);

    // restate users
    UserCache.restateAllUsers(snapId);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, ">>2onMatcher TradeStateAdminMessage=", this, " t=", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss(),
          ", snapId=", snapId, T0_EQ, System.currentTimeMillis());
    }

    // restateAllAssetGroups
    AssetGroupCache.restateAllAssetGroups(snapId);

    // restate all positions
    UserCache.restateAllUserPositions(snapId);
    Map<Integer, ReconciliationResult> reconciliationResult = UserCache.processReconciliation();
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "Reconciliation done, result: ", reconciliationResult.toString());
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, ">>3onMatcher TradeStateAdminMessage=", this, " t=", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss(),
          ", snapId=", snapId, T0_EQ, System.currentTimeMillis());
    }

    // reset available balance if we are in DR mode
    if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
      UserCache.resetAvailableBalances();
    }

    // restate orderbooks
    final long t0 = System.currentTimeMillis();
    if (securityId > 0) {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(securityId);
      if (instrumentPair != null && (!(MarketStatus.RESTATE == marketStatus && MarketStatus.CLOSE == instrumentPair.getMarketStatus())))
        instrumentPair.changeState(marketStatus, snapId, this);
    } else { // restate all orderbooks
      final Iterator<InstrumentPair> iterator = InstrumentCache.getPairIterator();
      while (iterator.hasNext()) {
        final InstrumentPair instrumentPair = iterator.next();
        if (instrumentPair != null && (!(MarketStatus.RESTATE == marketStatus && MarketStatus.CLOSE == instrumentPair.getMarketStatus())))
          instrumentPair.changeState(marketStatus, snapId, this);
      }
    }
    if (LOGGER.isInfoEnabled())
      LOGGER.info(LOG_FMT_1, "Order book restatement took " + (System.currentTimeMillis() - t0) + " ms");

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, ">>4onMatcher TradeStateAdminMessage=", this, " t=", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss(),
          ", snapId=", snapId, T0_EQ, System.currentTimeMillis());
    }

    // send end marker
    final SnapResponseAdminMessage end = new SnapResponseAdminMessage(snapId, 0, 0, senderCompId);
    end.setSnapId(snapId);
    end.setOrderId(GlobalOrderBook.getOrderId());
    end.setExecId(GlobalOrderBook.getFilledCountGlobal());

    if (Context.getControllerMode() == Mode.PRIMARY) {
      end.setInputKafkaRecordOffset(getKafkaRecordOffset());
    } else {
      end.setInputKafkaRecordOffset(getInputKafkaRecordOffset());
      end.setOutputKafkaRecordOffset(getKafkaRecordOffset());
    }

    Context.getMatcherToPublisherQueue().addGuaranteed(end);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, ">>4onMatcher TradeStateAdminMessage=", this, " t=", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss(),
          ", snapId=", snapId, T0_EQ, System.currentTimeMillis());
    }

    this.status = END;
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("TradeStateAdminMessage [connectionId=").append(connectionId).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis)
        .append(", snapId=").append(snapId).append(STATUS_EQ).append(status).append(", marketStatus=").append(marketStatus)
        .append(SECURITYID_EQ).append(securityId).append(ROUTETODESTINATION_EQ).append(routeToDestination).append(SENDERCOMPID_EQ)
        .append(senderCompId).append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append("]");
    return s;
  }

  @Override
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"TradeStateAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId);
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append(",\"securityId\":").append(securityId).append(",\"marketStatus\":").append(marketStatus.value()).append(",\"status\":")
        .append(status);
    sb.append("}");
    return sb.toString();
  }

}
