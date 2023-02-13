package com.solfini.matchengine.message.admin;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.MessageType;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageDecoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.util.FastArrayList;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class ExpireContractMessage extends AdminMessage {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExpireContractMessage.class);
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  private static final int USDC_ID = 1;
  private static final int USDC_QUANTITY_SCALE = InstrumentCache.getSettleInstrumentQuantityScale();
  private static final long USDC_MULT = InstrumentCache.getSettleInstrumentQuantityScaleMult();

  public static final long ONE_DAY = 86_400_000;
  public static final long TWO_DAYS = 172_800_000;
  public static final long ONE_WEEK = 604_800_000;
  public static final long TWO_WEEKS = 1_209_600_000;
  public static final long ONE_MONTH = 2_592_000_000L;
  public static final long ONE_QUARTER = 7_862_400_000L;
  public static final long ONE_YEAR = 22_896_000_000L;

  private final List<AssetFundingRate> expireList = new FastArrayList<>();

  private UpdateType updateType;
  private int txId;
  private long timestamp;

  public ExpireContractMessage() {
    timestamp = System.currentTimeMillis();
  }

  public ExpireContractMessage(final FundingRateCalcAdminMessageDecoder FUNDING_RATE_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.updateType = FUNDING_RATE_DECODER.updateType();
    this.txId = FUNDING_RATE_DECODER.txId();
    this.triggerTimeMillis = FUNDING_RATE_DECODER.triggerTimeMillis();
    this.routeToDestination = FUNDING_RATE_DECODER.routeToDestination();
    this.senderInstanceId = FUNDING_RATE_DECODER.senderInstanceId();

    int assetId = FUNDING_RATE_DECODER.assetId();
    final String symbol = FUNDING_RATE_DECODER.symbol();

    if (assetId == 0 && symbol != null) {
      assetId = lookupAssetIdFromSymbol(symbol);
    }

    final long value = FUNDING_RATE_DECODER.rate().value();
    final int scale = FUNDING_RATE_DECODER.rate().scale();

    final long markValue = FUNDING_RATE_DECODER.markInSettleCoin().value();
    final int markScale = FUNDING_RATE_DECODER.markInSettleCoin().scale();
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, FUNDINGRATECALCMESSAGE_UPDATETYPE_EQ, updateType != null ? updateType.toString() : "", TXID_EQ, txId,
          ASSETID_EQ, assetId, VALUE_EQ, value, SCALE_EQ, scale, MARKVALUE_EQ, markValue, MARKSCALE_EQ, markScale, TRIGGERTIMEMILLIS_EQ,
          triggerTimeMillis, CURRENTTIME_EQ, System.currentTimeMillis());
    }

    expireList.add(new AssetFundingRate(assetId, new DecimalFloat(value, scale), new DecimalFloat(markValue, markScale)));

  }

  private final int lookupAssetIdFromSymbol(final String symbol) {
    try {
      final InstrumentPair pair = InstrumentCache.getPairBySymbol(symbol);
      if (pair != null) {
        LOGGER.info(LOG_FMT_4, "FundingRateCalcMessage lookup ", symbol, ", assetId=" + pair.getId());
        return pair.getId();
      }

      final Instrument instrument = InstrumentCache.getBySymbol(symbol.trim());
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
    return MessageType.EXPIRE_CALC;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public final List<AssetFundingRate> getAssetExpireList() {
    return expireList;
  }

  public final int getTxId() {
    return txId;
  }

  public final void setTxId(int txId) {
    this.txId = txId;
  }

  // called from matching thread
  private void applyExpiration() {
    final double usdSettlementMark = 1;
    // for all pairs
    for (final AssetFundingRate assetFundingRate : expireList) {
      try {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(assetFundingRate.getAssetId());
        if (instrumentPair == null)
          continue;

        final OrderBook orderBook = instrumentPair.getOrderBook();

        // calc mark price
        int markInSettleCoin;
        final InstrumentPair underlyerPair = InstrumentCache.getPair(instrumentPair.getUnderlyerId());
        if (underlyerPair == null) {
          LOGGER.error("Missing underlyerPair for " + instrumentPair);
          // markInSettleCoin = instrumentPair.getIndexFeedUsdMark();
          return;
        } else {
          final OrderBook underlyerOrderBook = underlyerPair.getOrderBook();
          if (underlyerOrderBook == null) {
            LOGGER.error("Missing orderBook for " + instrumentPair);
            return;
          }
          if (instrumentPair.getAssetType() == AssetType.OPTION_CALL) {
            markInSettleCoin = Math.max(underlyerOrderBook.getMark() - instrumentPair.getStrikePrice(), 0);
          } else if (instrumentPair.getAssetType() == AssetType.OPTION_PUT) {
            markInSettleCoin = Math.max(instrumentPair.getStrikePrice() - underlyerOrderBook.getMark(), 0);
          } else
            markInSettleCoin = underlyerOrderBook.getMark();
        }
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, ">>> applyExpiration assetId=", assetFundingRate.getAssetId(), ", markInSettleCoin=",
              (double) markInSettleCoin);
        }

        // expire positions in orderbook
        orderBook.expireSettlePosition(markInSettleCoin);

        // reset expiration time
        if (instrumentPair.getExpireRollTimeMillis() > 0 && instrumentPair.getSymbol() != null) {
          final String prevSymbol = instrumentPair.getSymbol();
          final long prevExpireRollTimeMillis = instrumentPair.getExpireRollTimeMillis();
          final int prevSymbolRollCount = instrumentPair.getSymbolRollCount();
          final long prevFundingRateTime = instrumentPair.getFundingRateTime();
          final long prevContractExpireTime = instrumentPair.getContractExpireTime();

          instrumentPair.setSymbolRollCount(instrumentPair.getSymbolRollCount() + 1);
          instrumentPair.setSymbol(rollSymbol(instrumentPair));
          instrumentPair.setName(instrumentPair.getSymbol().replace("[C]", "[Call]").replace("[P]", "[Put]"));

          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_6, ">>> applyExpiration buildNewSymbol now=",
                StringUtil.getCurrentDateYYYMMDDHHMMSSsss(System.currentTimeMillis()), ", prevSymbol=", prevSymbol,
                ", prevExpireRollTimeMillis=", prevExpireRollTimeMillis, ", prevSymbolRollCount=", prevSymbolRollCount,
                ", prevFundingRateTime=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(prevFundingRateTime), ", prevContractExpireTime=",
                StringUtil.getCurrentDateYYYMMDDHHMMSSsss(prevContractExpireTime), ", newSymbol=", instrumentPair.getSymbol(),
                ", newExpireRollTimeMillis=", instrumentPair.getExpireRollTimeMillis(), ", newSymbolRollCount=",
                instrumentPair.getSymbolRollCount(), ", newFundingRateTime=",
                StringUtil.getCurrentDateYYYMMDDHHMMSSsss(instrumentPair.getFundingRateTime()), ", newContractExpireTime=",
                StringUtil.getCurrentDateYYYMMDDHHMMSSsss(instrumentPair.getContractExpireTime()), ", instrumentPair=", instrumentPair);
          }
          orderBook.updateSecurityDefinition(instrumentPair);

          // publish the updated instrumentPair market data
          if (instrumentPair.isInMarketDataQueue().compareAndSet(0, 1))
            Context.getMarketDataBuilderQueue().add(instrumentPair);
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        e.printStackTrace();
      }
    }
  }

  public static final Calendar getLastFridayOfMonth(final Calendar calendar) {
    calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, 1);
    calendar.add(Calendar.DAY_OF_MONTH, -(calendar.get(Calendar.DAY_OF_WEEK) % 7 + 1));
    return calendar;
  }

  public static final Calendar getNextFridayOfWeek(final Calendar calendar) {
    int add = 7 - (calendar.get(Calendar.DAY_OF_WEEK) + 1) % 7;
    calendar.add(Calendar.DAY_OF_WEEK, add);

    return calendar;
  }

  private static final String buildNewSymbol(final InstrumentPair instrumentPair) {
    final GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTimeZone(GMT);
    calendar.setTimeInMillis(instrumentPair.getContractExpireTime());
    final String[] symbolArr = instrumentPair.getSymbol().split("]");
    if (symbolArr.length == 1)
      return instrumentPair.getSymbol();
    final String[] symbolArr2 = symbolArr[1].split("_");

    if (instrumentPair.getExpireRollTimeMillis() <= ONE_DAY || instrumentPair.getExpireRollTimeMillis() == TWO_DAYS) {
      instrumentPair.setContractExpireTime(instrumentPair.getContractExpireTime() + instrumentPair.getExpireRollTimeMillis());
    } else if (instrumentPair.getExpireRollTimeMillis() == ONE_WEEK) {
      calendar.add(Calendar.WEEK_OF_YEAR, 1);
      getNextFridayOfWeek(calendar);
      instrumentPair.setContractExpireTime(calendar.getTimeInMillis());
    } else if (instrumentPair.getExpireRollTimeMillis() == TWO_WEEKS) {
      calendar.add(Calendar.WEEK_OF_YEAR, 2);
      getNextFridayOfWeek(calendar);
      instrumentPair.setContractExpireTime(calendar.getTimeInMillis());
    } else if (instrumentPair.getExpireRollTimeMillis() == ONE_MONTH) {
      calendar.add(Calendar.MONTH, 1);
      getLastFridayOfMonth(calendar);
      instrumentPair.setContractExpireTime(calendar.getTimeInMillis());
    } else if (instrumentPair.getExpireRollTimeMillis() == ONE_QUARTER) {
      calendar.add(Calendar.MONTH, 3);
      getLastFridayOfMonth(calendar);
      instrumentPair.setContractExpireTime(calendar.getTimeInMillis());
    } else if (instrumentPair.getExpireRollTimeMillis() == ONE_YEAR) {
      calendar.add(Calendar.YEAR, 1);
      getLastFridayOfMonth(calendar);
      instrumentPair.setContractExpireTime(calendar.getTimeInMillis());
    }
    instrumentPair.setFundingRateTime(instrumentPair.getContractExpireTime());

    final StringBuilder newSymbol = new StringBuilder(symbolArr[0]);
    newSymbol.append("]");

    // calc time
    calendar.setTimeInMillis(instrumentPair.getContractExpireTime());
    if (instrumentPair.getExpireRollTimeMillis() >= 86_400_000) {
      final int month = calendar.get(Calendar.MONTH);
      newSymbol.append(StringUtil.intMonthToMMM(month));
    } else {
      newSymbol.append("Now");
    }
    newSymbol.append(calendar.get(Calendar.DAY_OF_MONTH));

    if (symbolArr2.length == 2)
      newSymbol.append("_").append(symbolArr2[1]);

    return newSymbol.toString();
  }

  // called from matching thread
  // for BTC/USD[Call]Dec25_5000 or BTC/USD[DF]Jun26
  // if daily rolls on another contract, roll another day
  private static final String rollSymbol(final InstrumentPair instrumentPair) {
    String symbol = buildNewSymbol(instrumentPair);

    InstrumentPair oldPair = InstrumentCache.getPairBySymbol(symbol);
    if (oldPair == null) {
      InstrumentCache.updatePairSymbol(symbol, instrumentPair);
    } /*
       * else if (instrumentPair.getExpireRollTimeMillis() <= ONE_DAY || instrumentPair.getExpireRollTimeMillis() == TWO_DAYS) { for (int i
       * = 0; i < 365; i++) { symbol = buildNewSymbol(instrumentPair); oldPair = InstrumentCache.getPairBySymbol(symbol); if (oldPair ==
       * null) { InstrumentCache.updatePairSymbol(symbol, instrumentPair); break; } } }
       */

    return symbol;
  }


  @Override
  public final void onMatcher() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, ">>> applyExpiration timestamp=", timestamp);
    }

    applyExpiration();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "<<< applyExpiration timestamp=", timestamp, ", now=", System.currentTimeMillis(), ", message=", this);
    }
  }

  @Override
  public final void onPublish() {
    // do nothing
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("ExpireContractMessage [updateType=").append(updateType).append(TIMESTAMP_EQ).append(timestamp).append(ROUTETODESTINATION_EQ)
        .append(routeToDestination).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis).append(SOURCESEQNUM_EQ).append(sourceSeqNum)
        .append(SOURCESENDTIME_EQ).append(sourceSendTime).append("]");
    return s;
  }

  @Override
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ExpireContractMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"timestamp\":").append(timestamp).append(",\"updateType\":").append(updateType.value());
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append(",\"assetFundingList\":[");
    boolean comma = false;
    for (final AssetFundingRate assetFundingRate : expireList) {
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

    if (!(o instanceof ExpireContractMessage))
      return false;

    // typecast o to Complex so that we can compare data members
    ExpireContractMessage other = (ExpireContractMessage) o;

    if (timestamp != other.timestamp || txId != other.txId || other.getAssetExpireList().isEmpty() || expireList.isEmpty())
      return false;
    return (expireList.size() == other.getAssetExpireList().size());
  }

  @Override
  public int hashCode() {
    return (int) timestamp;
  }

  public static void main(String[] args) {
    String s = "BTC/USD[C]".replace("[C]", "[Call]").replace("[P]", "[Put]");
    final GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTimeZone(GMT);
    getLastFridayOfMonth(calendar);
    System.out.println("d1=" + calendar.getTime());
    calendar.add(Calendar.MONTH, 7);
    System.out.println("d2=" + calendar.getTime());

  }
}
