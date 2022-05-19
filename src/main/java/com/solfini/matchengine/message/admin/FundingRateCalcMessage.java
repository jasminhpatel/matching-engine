package com.solfini.matchengine.message.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import com.solfini.common.AdminMessage;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.MessageType;
import com.solfini.db.DBManager;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageDecoder;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.FastArrayList;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class FundingRateCalcMessage extends AdminMessage {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(FundingRateCalcMessage.class);

  private static final int USDC_ID = 1;
  private static final int USDC_QUANTITY_SCALE = InstrumentCache.getSettleInstrumentQuantityScale();
  private static final long USDC_MULT = InstrumentCache.getSettleInstrumentQuantityScaleMult();
  private static final String INSERT_FUNDING_RATE_HISTORY =
      "insert into FUNDING_RATE_HISTORY(pairId,symbol,fundingRate,markInSettleCoin,txId,timestamp,vwap,last,usdMark,timePeriodInterest) values (?,?,?,?, ?, ?, ?, ?, ?, ?)";


  private final List<AssetFundingRate> assetFundingList = new FastArrayList<>();

  private UpdateType updateType;
  private int txId;
  private long timestamp;

  public FundingRateCalcMessage() {
    timestamp = System.currentTimeMillis();
  }

  public FundingRateCalcMessage(final FundingRateCalcAdminMessageDecoder FUNDING_RATE_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.updateType = FUNDING_RATE_DECODER.updateType();
    this.txId = FUNDING_RATE_DECODER.txId();
    this.triggerTimeMillis = FUNDING_RATE_DECODER.triggerTimeMillis();
    this.routeToDestination = FUNDING_RATE_DECODER.routeToDestination();
    this.senderInstanceId = FUNDING_RATE_DECODER.senderInstanceId();
    this.timestamp = triggerTimeMillis > 0 ? triggerTimeMillis : System.currentTimeMillis();

    int assetId = FUNDING_RATE_DECODER.assetId();
    final String symbol = FUNDING_RATE_DECODER.symbol();

    if (assetId == 0 && symbol != null) {
      assetId = lookupAssetIdFromSymbol(symbol);
    }

    long value = FUNDING_RATE_DECODER.rate().value();
    int scale = FUNDING_RATE_DECODER.rate().scale();

    long markValue = FUNDING_RATE_DECODER.markInSettleCoin().value();
    int markScale = FUNDING_RATE_DECODER.markInSettleCoin().scale();
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, FUNDINGRATECALCMESSAGE_UPDATETYPE_EQ, updateType != null ? updateType.toString() : "", TXID_EQ, txId,
          ASSETID_EQ, assetId, VALUE_EQ, value, SCALE_EQ, scale, MARKVALUE_EQ, markValue, MARKSCALE_EQ, markScale, TRIGGERTIMEMILLIS_EQ,
          triggerTimeMillis, CURRENTTIME_EQ, System.currentTimeMillis());
    }

    assetFundingList.add(new AssetFundingRate(assetId, new DecimalFloat(value, scale), new DecimalFloat(markValue, markScale)));

  }

  private final int lookupAssetIdFromSymbol(final String symbol) {
    try {
      final InstrumentPair pair = InstrumentCache.getPairBySymbol(symbol);
      if (pair != null) {
        LOGGER.info(LOG_FMT_4, "FundingRateCalcMessage lookup ", symbol, ", assetId=" + pair.getId());
        return pair.getId();
      }

      Instrument instrument = InstrumentCache.getBySymbol(symbol.trim());
      if (instrument == null) {
        LOGGER.warn(LOG_FMT_4, "FundingRateCalcMessage unable to lookup ", symbol);
        return 0;
      } else {
        return instrument.getId();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return 0;
    }
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.FUNDING_RATE_CALC;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public final List<AssetFundingRate> getAssetFundingList() {
    return assetFundingList;
  }

  public final int getTxId() {
    return txId;
  }

  public final void setTxId(int txId) {
    this.txId = txId;
  }

  // called from matching thread
  private void applyFundingRate() {
    final double usdSettlementMark = 1;
    // for all pairs
    for (final AssetFundingRate assetFundingRate : assetFundingList) {
      InstrumentPair instrumentPair = InstrumentCache.getPair(assetFundingRate.getAssetId());
      if (instrumentPair == null)
        continue;
      final double fundingRate = -StringUtil.toDouble(assetFundingRate.getRate()); // reverse sign on rate
      if (fundingRate == 0)
        continue;

      double markInSettleCoin =
          (assetFundingRate.getMarkInSettleCoin() == null) ? 0 : StringUtil.toDouble(assetFundingRate.getMarkInSettleCoin());
      if (markInSettleCoin == 0) {
        markInSettleCoin = instrumentPair.getIndexFeedUsdMark();
      }

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_6, ">>> applyFundingRate assetId=", assetFundingRate.getAssetId(), ", markInSettleCoin=", markInSettleCoin,
            ", fundingRate=", fundingRate);
      }
      // for all users
      for (int userId = 0; userId <= UserCache.getCapacity(); userId++) {
        final User user = UserCache.get(userId);
        if (user == null || !user.isActive() || user.getPositionArr() == null)
          continue;

        final Position position = user.getPositionArr()[assetFundingRate.getAssetId()];
        if (position == null || position.getQuantity() == 0)
          continue;

        final double notional = markInSettleCoin * instrumentPair.getQuantityScaleFactor() * position.getQuantity();

        final double adjustment = notional * fundingRate;
        final double adjSettleCoin = adjustment / usdSettlementMark;
        final long change = (long) (adjSettleCoin * USDC_MULT);

        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_20, ">>> applyFundingRate timestamp=", timestamp, ", id=", user.getId(), ", id=", instrumentPair.getId(),
              ", usdSettlementMark=", usdSettlementMark, ", position=", position, QUANTITY_EQ, position.getQuantity(), ", fundingRate=",
              fundingRate, ", adjustment=", adjustment, ", notional=", notional, ", change=", change);
        }

        final BalanceAdminMessage message = BalanceAdminMessageObjectPool.get();
        message.setUpdateType(UpdateType.PATCH);
        message.setRequestStatus(RequestStatus.SUCCESS);
        message.setChecksum(0);
        message.setPersistTime(timestamp);
        message.setUserId(user.getId());
        message.setUser(user);
        message.setFirmId(user.getFirmId());
        message.setFeeTier(user.getFeeTier());
        message.setTxType(Constants.TX_FUNDING_RATE);
        message.setTxId(instrumentPair.getId());
        message.setTriggerTimeMillis(triggerTimeMillis);
        message.setMatchTime(triggerTimeMillis);

        final Balance balance = new Balance();
        message.getBalanceList().add(balance);
        balance.setAssetId(USDC_ID);
        balance.setBalanceChange(change, USDC_QUANTITY_SCALE);

        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, ">>> applyFundingRate message=", message);
        }
        UserCache.addBalance(message);
      }

      Context.getMarketDataBuilderQueue().add(instrumentPair);
    }
  }

  @Override
  public final void onMatcher() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, ">>> calcFundingRate timestamp=", timestamp);
    }

    applyFundingRate();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "<<< calcFundingRate timestamp=", timestamp, ", now=", System.currentTimeMillis());
    }
  }

  @Override
  public final void onPublish() {
    // do nothing
  }

  @Override
  public final void onPersist() {
    final String timestamp = StringUtil.getCurrentDateYYYMMDDHHMMSSsss();

    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(INSERT_FUNDING_RATE_HISTORY);) {
      for (final AssetFundingRate assetFundingRate : assetFundingList) {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(assetFundingRate.getAssetId());
        final double fundingRate = -StringUtil.toDouble(assetFundingRate.getRate()); // reverse sign on rate

        double markInSettleCoin =
            (assetFundingRate.getMarkInSettleCoin() == null) ? 0 : StringUtil.toDouble(assetFundingRate.getMarkInSettleCoin());
        if (markInSettleCoin == 0) {
          if (instrumentPair != null)
            markInSettleCoin = instrumentPair.getIndexFeedUsdMark();
        }

        ps.setInt(1, assetFundingRate.getAssetId());
        ps.setString(2, instrumentPair != null ? instrumentPair.getSymbol() : "");
        ps.setDouble(3, fundingRate);
        ps.setDouble(4, markInSettleCoin);
        ps.setInt(5, txId);
        ps.setLong(6, this.triggerTimeMillis);
        ps.setDouble(7, assetFundingRate.getVwap());
        ps.setDouble(8, assetFundingRate.getLast());
        ps.setDouble(9, assetFundingRate.getUsdMark());
        ps.setDouble(10, assetFundingRate.getTimePeriodInterest());

        ps.executeUpdate();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("FundingRateCalcMessage [updateType=").append(updateType).append(TIMESTAMP_EQ).append(timestamp).append(ROUTETODESTINATION_EQ)
        .append(routeToDestination).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis).append(SOURCESEQNUM_EQ).append(sourceSeqNum)
        .append(SOURCESENDTIME_EQ).append(sourceSendTime).append("]");
    return s;
  }

  @Override
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"FundingRateCalcMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"timestamp\":").append(timestamp).append(",\"updateType\":").append(updateType.value());
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append(",\"assetFundingList\":[");
    boolean comma = false;
    for (final AssetFundingRate assetFundingRate : assetFundingList) {
      if (comma)
        sb.append(",");
      if (assetFundingRate != null) {
        sb.append(assetFundingRate.toJSON());
        comma = true;
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  // Overriding equals()
  @Override
  public boolean equals(final Object o) {
    if (o == this)
      return true;

    if (!(o instanceof FundingRateCalcMessage))
      return false;

    // typecast o to Complex so that we can compare data members
    FundingRateCalcMessage other = (FundingRateCalcMessage) o;

    if (timestamp != other.timestamp || txId != other.txId || assetFundingList.isEmpty())
      return false;
    return (assetFundingList.size() == other.getAssetFundingList().size());
  }

  @Override
  public int hashCode() {
    return (int) timestamp;
  }
}
