package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.DecimalFloatEncoder;
import com.solfini.internal.admin.schema.FeeAdminMessageEncoder;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageEncoder;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageEncoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder;
import com.solfini.matchengine.PublisherEncoderCache;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.MarketDataFeed;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.CancelRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.outbound.MarketDataSnapMessage;
import com.solfini.matchengine.message.outbound.OptionPricingMessage;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.message.session.HeartbeatMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.matchengine.message.session.LogoutMessage;
import com.solfini.matchengine.message.session.NetworkStatusMessage;
import com.solfini.matchengine.message.session.ResendRequestMessage;
import com.solfini.matchengine.message.session.SequenceResetMessage;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.pool.BusinessRejectObjectPool;
import com.solfini.pool.CancelRejectObjectPool;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.MarketDataFeedObjectPool;
import com.solfini.pool.OptionPricingObjectPool;
import com.solfini.pool.PositionReportObjectPool;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.AssetType;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.BusinessRejectEncoder;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.ExecutionReportEncoder;
import com.solfini.sbe.encoder.HeartbeatEncoder;
import com.solfini.sbe.encoder.LogonEncoder;
import com.solfini.sbe.encoder.LogoutEncoder;
import com.solfini.sbe.encoder.MarketDataFeedEncoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MassCancelOrderEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.NetworkStatusEncoder;
import com.solfini.sbe.encoder.OptionPricingFeedEncoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrderCancelRejectEncoder;
import com.solfini.sbe.encoder.PositionReportEncoder;
import com.solfini.sbe.encoder.PositionReportEncoder.PositionsGroupEncoder;
import com.solfini.sbe.encoder.PositionReportEncoder.PositionsGroupEncoder.PositionsAssetIdGroupEncoder;
import com.solfini.sbe.encoder.ResendRequestEncoder;
import com.solfini.sbe.encoder.SequenceResetEncoder;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.user.UserOpenOrdersByPair;
import com.solfini.util.MbxMath;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class MessagePublisher implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MessagePublisher.class);

  public static final String ME_KAFKA_TOPIC_PRIMARY = PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "me1");
  public static final String ME_KAFKA_TOPIC_SECONDARY = PropertyReader.getProperty("ME_KAFKA_TOPIC_SECONDARY", "me2");
  public static final String DEFAULT_SENDER_COMP_ID = PropertyReader.getProperty("DEFAULT_SENDER_COMP_ID", "1000000009");

  public static final short COST_BASIS_PUBLISH_SCALE = StringUtil.toShort(PropertyReader.getProperty("COST_BASIS_PUBLISH_SCALE", "2"));
  public static final short RISK_PUBLISH_SCALE = StringUtil.toShort(PropertyReader.getProperty("RISK_PUBLISH_SCALE", "2"));
  public static final long COST_BASIS_PUBLISH_MULT = MbxMath.multiplier(COST_BASIS_PUBLISH_SCALE);
  public static final long RISK_PUBLISH_MULT = MbxMath.multiplier(RISK_PUBLISH_SCALE);

  private String topic;

  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  private static final int USDC_ID = 1;
  private final int ME_SEQ_ID = Context.getMESeqId(); // this can be incremented with failover

  private static final short SHORT_ZERO = 0;

  private long sequenceNumber = 0; // loaded from snap now //StateLoader.getInitialOutputSequenceNumber(); // load initial and increment
  private boolean useKafka = true;

  private static final PreOrderCheck marginCheck = new MarginPreOrderCheckAndSettle();
  private volatile SnapUtil snapUtil = null;

  public MessagePublisher() {
    // Constructor
  }

  public void setOutputTopic(final String topic) {
    this.topic = topic;
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Switching message publication topic to " + ((topic == null) ? "<none>" : topic));
    }
  }

  public final void setSequenceNumber(final long seqNum) {
    // Not required
  }

  public final void publishToKafka(final boolean useKafka) {
    this.useKafka = useKafka;
  }

  private void addRiskDataToPositionMessage(final PositionReportEncoder positionReportEncoder, final User user) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_2, ">>> addRiskDataToPositionMessage user=", user);
    }
    positionReportEncoder.usdValue((long) (user.getUsdValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdMarginableValue((long) (user.getUsdMarginableValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdMarginableValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdNotionalPositionValue((long) (user.getUsdNotionalPositionValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdNotionalPositionValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder
        .usdMaxExposurePositionAndOpenOrdersValue((long) (user.getUsdMaxExposurePositionAndOpenOrdersValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdMaxExposurePositionAndOpenOrdersValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdOpenOrdersRequiredValue((long) (user.getUsdOpenOrdersRequiredValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdOpenOrdersRequiredValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdMarginValue((long) (user.getUsdMarginValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdMarginValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdMarginRequiredValue((long) (user.getUsdMarginRequiredValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdMarginRequiredValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdMarginMaintValue((long) (user.getUsdMarginMaintValue() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdMarginMaintValueScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.leverageRatio((long) (user.getLeverageRatio() * RISK_PUBLISH_MULT));
    positionReportEncoder.leverageRatioScale(RISK_PUBLISH_SCALE);

    positionReportEncoder.usdUnrealized((long) (user.getUsdUnrealized() * RISK_PUBLISH_MULT));
    positionReportEncoder.usdUnrealizedScale(RISK_PUBLISH_SCALE);
  }


  private void addPositionDataToPositionMessage(final PositionReportEncoder positionReportEncoder, final User user,
      final Position[] positionArr, final int positionsLength) {

    if (LOGGER.isDebugEnabled() && (user.getId() == 18)) {
      LOGGER.debug(LOG_FMT_6, ">>> 18publish3a positionsLength=", positionsLength, POSITIONARR_EQ, Arrays.toString(positionArr),
          POSITIONSGROUPENCODER_EQ, positionReportEncoder.toString());
    }
    if (positionsLength == 0 || positionArr == null || positionArr.length == 0) // skip if no positions to add
      return;

    PositionsGroupEncoder groupEncoder = positionReportEncoder.positionsGroupCount(positionsLength - 1); // we skip 0
    for (int i = 1; i < positionsLength; i++) {
      groupEncoder = groupEncoder.next();

      final Position position = positionArr[i];

      final Instrument instrument = (position == null) ? null : InstrumentCache.get(position.getInstrumentId());
      final InstrumentPair pair = (position == null) ? null : InstrumentCache.getPair(position.getInstrumentId());
      LOGGER.info("instrument: " + instrument);
      LOGGER.info("pair: " + pair);

      if (instrument != null) {
        groupEncoder.assetType(AssetType.ASSET);
        groupEncoder.instrumentId(instrument.getId());
        groupEncoder.quantity(position.getQuantity());
        groupEncoder.quantityScale(instrument.getQuantityScale());
        groupEncoder.availableQuantity(position.getAvailableQuantity());
        groupEncoder.availableQuantityScale(instrument.getQuantityScale());
        groupEncoder.usdCostBasis((long) (position.getUsdCostBasis() * COST_BASIS_PUBLISH_MULT));
        groupEncoder.usdCostBasisScale(COST_BASIS_PUBLISH_SCALE);
        groupEncoder.usdAvgCostBasis((long) (position.getUsdAvgCostBasisDouble() * COST_BASIS_PUBLISH_MULT));
        groupEncoder.usdAvgCostBasisScale(COST_BASIS_PUBLISH_SCALE);
        groupEncoder.usdValue((long) (position.getUsdValue() * RISK_PUBLISH_MULT));
        groupEncoder.usdValueScale(RISK_PUBLISH_SCALE);
        groupEncoder.usdUnrealized((long) (position.getUsdUnrealized() * RISK_PUBLISH_MULT));
        groupEncoder.usdUnrealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.usdRealized((long) (position.getUsdRealizedDouble() * RISK_PUBLISH_MULT));
        groupEncoder.usdRealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.quotedUsdMark((long) (position.getQuotedUsdMark() * RISK_PUBLISH_MULT));
        groupEncoder.quotedUsdMarkScale(RISK_PUBLISH_SCALE);
        groupEncoder.settleCoinUsdMark((long) (position.getSettleCoinUsdMark() * RISK_PUBLISH_MULT));
        groupEncoder.settleCoinUsdMarkScale(RISK_PUBLISH_SCALE);
        groupEncoder.settleCoinUnrealized((long) (position.getSettleCoinUnrealized() * RISK_PUBLISH_MULT));
        groupEncoder.settleCoinUnrealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.settleCoinRealized((long) (position.getSettleCoinRealized() * RISK_PUBLISH_MULT));
        groupEncoder.settleCoinRealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.bankruptPriceInt(position.getBankruptPriceInt());
        groupEncoder.bankruptPriceIntScale(instrument.getPriceScale());

        // add assetId,tokenId set
        final Set<long[]> assetIdtreeSet = position.getAssetIdtreeSet();
        LOGGER.info("assetIdtreeSet: " + assetIdtreeSet);
        if (assetIdtreeSet != null) {
          LOGGER.info("assetIdtreeSet.size: " + assetIdtreeSet.size());
          PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(assetIdtreeSet.size());
          for (final long[] value : assetIdtreeSet) {
            assetGroupEncoder = assetGroupEncoder.next();
            assetGroupEncoder.assetId(value[0]);
            assetGroupEncoder.tokenId((int) value[1]);
          }
        } else {
          LOGGER.info("append dummy");
          PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(1);
          assetGroupEncoder = assetGroupEncoder.next();
          assetGroupEncoder.assetId(0);
          assetGroupEncoder.tokenId(0);
        }

      } else if (pair != null) {
        groupEncoder.assetType(AssetType.get(pair.getAssetType().value()));
        groupEncoder.instrumentId(pair.getId());
        groupEncoder.quantity(position.getQuantity());
        groupEncoder.quantityScale(pair.getQuantityScale());
        groupEncoder.availableQuantity(position.getAvailableQuantity());
        groupEncoder.availableQuantityScale(pair.getQuantityScale());
        groupEncoder.usdCostBasis((long) (position.getUsdCostBasis() * COST_BASIS_PUBLISH_MULT));
        groupEncoder.usdCostBasisScale(COST_BASIS_PUBLISH_SCALE);
        groupEncoder.usdAvgCostBasis((long) (position.getUsdAvgCostBasisDouble() * COST_BASIS_PUBLISH_MULT));
        groupEncoder.usdAvgCostBasisScale(COST_BASIS_PUBLISH_SCALE);
        groupEncoder.usdValue((long) (position.getUsdValue() * RISK_PUBLISH_MULT));
        groupEncoder.usdValueScale(RISK_PUBLISH_SCALE);
        groupEncoder.usdUnrealized((long) (position.getUsdUnrealized() * RISK_PUBLISH_MULT));
        groupEncoder.usdUnrealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.usdRealized((long) (position.getUsdRealizedDouble() * RISK_PUBLISH_MULT));
        groupEncoder.usdRealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.quotedUsdMark((long) (position.getQuotedUsdMark() * RISK_PUBLISH_MULT));
        groupEncoder.quotedUsdMarkScale(RISK_PUBLISH_SCALE);
        groupEncoder.settleCoinUsdMark((long) (position.getSettleCoinUsdMark() * RISK_PUBLISH_MULT));
        groupEncoder.settleCoinUsdMarkScale(RISK_PUBLISH_SCALE);
        groupEncoder.settleCoinUnrealized((long) (position.getSettleCoinUnrealized() * RISK_PUBLISH_MULT));
        groupEncoder.settleCoinUnrealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.settleCoinRealized((long) (position.getSettleCoinRealized() * RISK_PUBLISH_MULT));
        groupEncoder.settleCoinRealizedScale(RISK_PUBLISH_SCALE);
        groupEncoder.bankruptPriceInt(position.getBankruptPriceInt());
        groupEncoder.bankruptPriceIntScale(pair.getPriceScale());

        //dummy value
        PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(1);
        assetGroupEncoder = assetGroupEncoder.next();
        assetGroupEncoder.assetId(0);
        assetGroupEncoder.tokenId(0);
      }
    }
  }

  public static final int POSITION_DATA_COUNT = 10;
  static final byte[] ZERO_STR_TO_BYTES = "0".getBytes();


  public void publish(final PositionReportMessage positionReportMessage, final int posReqResult, final PositionReportEncoderCache cache) {
    final PositionReportEncoder positionReportEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    positionReportEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, positionReportMessage);
    encodedLength += headerEncoder.encodedLength();

    positionReportEncoder.userId(positionReportMessage.getUser() == null ? 0 : positionReportMessage.getUser().getId());
    positionReportEncoder.posReqResult(posReqResult);
    positionReportEncoder.execId(positionReportMessage.getExecId());
    positionReportEncoder.orderId(positionReportMessage.getOrderId());
    positionReportEncoder.txnId(positionReportMessage.getTxnId());
    positionReportEncoder.transactTime(System.currentTimeMillis());

    final User user = positionReportMessage.getUser();
    final Position[] positionArr = positionReportMessage.getPositions();
    final int positionsLength = positionReportMessage.getPositionsLength();

    positionReportEncoder.settleCoinChange(0);
    positionReportEncoder.settleCoinChangeScale(SHORT_ZERO);

    // add user risk to first position
    addRiskDataToPositionMessage(positionReportEncoder, user);

    // add position data
    // if ((user.getId() == 18) && (positionArr.length > 31) && (positionArr[31] != null)) {
    // LOGGER.info(LOG_FMT_6, "TRACK USD ", positionArr[31].getQuantity(), " ", positionArr[31].getAvailableQuantity(),
    // " ", positionArr[31].getQuantity() - positionArr[31].getAvailableQuantity());
    // }
    addPositionDataToPositionMessage(positionReportEncoder, user, positionArr, positionsLength);

    if (LOGGER.isDebugEnabled() && (positionReportMessage.getUser().getId() == 18)) {
      LOGGER.debug(LOG_FMT_8, ">>> 18publish3 positionReportMessage=", positionReportMessage, TOTALCOUNT_EQ, positionsLength,
          POSITIONARR_EQ, Arrays.toString(positionArr), POSITIONREPORTENCODER_EQ, positionReportEncoder.toString());
    }

    // convert and publish
    encodedLength += positionReportEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    decodeAndPrint(bytesWithKafkaOffset);//todo remove after testing
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, positionReportMessage);

    PositionReportObjectPool.returnObject(positionReportMessage);
  }

  private final void publishPositionReport(final ExecutionReportMessage executionReport, final int posReqResult,
      final PositionReportEncoderCache cache) {
    final PositionReportEncoder positionReportEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    positionReportEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    positionReportEncoder.userId(executionReport.getUser() == null ? 0 : executionReport.getUser().getId());
    positionReportEncoder.posReqResult(posReqResult);
    positionReportEncoder.execId(executionReport.getExecId());
    positionReportEncoder.orderId(executionReport.getOrderId());
    positionReportEncoder.txnId(0);
    positionReportEncoder.transactTime(System.currentTimeMillis());

    final User user = executionReport.getUser();
    final Position[] positionArr = executionReport.getPositionArr();
    final int positionsLength = executionReport.getPositionsLength();

    positionReportEncoder.settleCoinChange(executionReport.getSettlePositionQuantityChange());
    positionReportEncoder.settleCoinChangeScale(SHORT_ZERO);

    // add user risk to first position
    addRiskDataToPositionMessage(positionReportEncoder, user);

    // add position data
    // if ((user.getId() == 18) && (positionArr.length > 31) && (positionArr[31] != null)) {
    // LOGGER.info(LOG_FMT_6, "TRACK USD ", positionArr[31].getQuantity(), " ", positionArr[31].getAvailableQuantity(),
    // " ", positionArr[31].getQuantity() - positionArr[31].getAvailableQuantity());
    // }
    addPositionDataToPositionMessage(positionReportEncoder, user, positionArr, positionsLength);

    // wrap header after populating groups
    populateHeader(headerEncoder, executionReport);
    encodedLength += headerEncoder.encodedLength();
    encodedLength += positionReportEncoder.encodedLength();
    // convert and publish
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    if (LOGGER.isDebugEnabled() && (user.getId() == 18)) {
      LOGGER.debug(LOG_FMT_8, ">>> 18publish3 executionReport=", executionReport, TOTALCOUNT_EQ, positionsLength, POSITIONARR_EQ,
          Arrays.toString(positionArr), POSITIONREPORTENCODER_EQ, positionReportEncoder.toString());
    }

    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    if (executionReport.getSnapId() == 0) {
      // for normal case with no snap, reuse the executionReport in publish
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, executionReport);
      if (LOGGER.isDebugEnabled() && user.getId() == 15) {
        LOGGER.debug(LOG_FMT_2, USER18PUBLISH2_EXECUTIONREPORT_EQ, executionReport);
      }
    } else {
      final PositionReportMessage positionReportMessage = PositionReportMessage.createPositionReportMessage(posReqResult, user,
          executionReport.getSenderCompId(), executionReport.getPositionArr(), executionReport.getPositionsLength(),
          executionReport.getExecId(), executionReport.getOrderId());
      positionReportMessage.setSourceSeqNum(executionReport.getSourceSeqNum());
      positionReportMessage.setKafkaRecordOffset(executionReport.getKafkaRecordOffset());
      positionReportMessage.setTransactionId(executionReport.getTransactionId());

      if (LOGGER.isDebugEnabled() && user.getId() == 15) {
        LOGGER.debug(LOG_FMT_2, USER18PUBLISH2_EXECUTIONREPORT_EQ, executionReport, POSITIONREPORTMESSAGE_EQ, positionReportMessage);
      }
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, positionReportMessage);
      PositionReportObjectPool.returnObject(positionReportMessage);
    }

  }


  private final void publishPositionReport(final BalanceAdminMessage balanceAdminMessage, final int posReqResult,
      final PositionReportEncoderCache cache) {
    final PositionReportEncoder positionReportEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    positionReportEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, balanceAdminMessage);
    encodedLength += headerEncoder.encodedLength();

    positionReportEncoder.userId(balanceAdminMessage.getUser() == null ? 0 : balanceAdminMessage.getUser().getId());
    LOGGER.info("userId: " + (balanceAdminMessage.getUser() == null ? 0 : balanceAdminMessage.getUser().getId()));
    positionReportEncoder.posReqResult(posReqResult);
    positionReportEncoder.transactTime(System.currentTimeMillis());
    if (posReqResult == TX_FUNDING_RATE)
      positionReportEncoder.execId(balanceAdminMessage.getTriggerTimeMillis());
    else
      positionReportEncoder.execId(0); // reset encoder value
    positionReportEncoder.orderId(0); // reset encoder value
    positionReportEncoder.txnId(balanceAdminMessage.getTxId());

    final User user = balanceAdminMessage.getUser();
    final Position[] positionArr = balanceAdminMessage.getPositionArr();
    final int positionsLength = balanceAdminMessage.getPositionsLength();

    LOGGER.info("positionArr.length: " + positionArr.length);
    LOGGER.info("positionsLength: " + positionsLength);

    // set balance change for settleCoin to positionReport settlPrice
    final List<Balance> balanceList = balanceAdminMessage.getBalanceList();
    if (balanceList != null) {
      for (final Balance balance : balanceList) {
        if (balance != null && balance.getAssetId() == USDC_ID) {
          final DecimalFloat change = balance.getBalanceChange();
          positionReportEncoder.settleCoinChange(change.value());
          positionReportEncoder.settleCoinChangeScale((short) change.scale());
          break;
        }
      }
    }

    // add user risk to first position
    addRiskDataToPositionMessage(positionReportEncoder, user);

    // add position data
    // if ((user.getId() == 18) && (positionArr.length > 31) && (positionArr[31] != null)) {
    // LOGGER.info(LOG_FMT_6, "TRACK USD ", positionArr[31].getQuantity(), " ", positionArr[31].getAvailableQuantity(),
    // " ", positionArr[31].getQuantity() - positionArr[31].getAvailableQuantity());
    // }
    addPositionDataToPositionMessage(positionReportEncoder, user, positionArr, positionsLength);

    if (LOGGER.isDebugEnabled() && (user.getId() == 18)) {
      LOGGER.debug(LOG_FMT_8, ">>> 18publish3 balanceAdminMessage=", balanceAdminMessage, TOTALCOUNT_EQ, positionsLength, POSITIONARR_EQ,
          Arrays.toString(positionArr), POSITIONREPORTENCODER_EQ, positionReportEncoder.toString());
    }

    // convert and publish
    encodedLength += positionReportEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    decodeAndPrint(bytesWithKafkaOffset);//todo remove after testing

    if (balanceAdminMessage.getSnapId() == 0) {
      // for normal case with no snap, reuse the balanceAdminMessage in publish
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, balanceAdminMessage);

      if (LOGGER.isDebugEnabled() && balanceAdminMessage.getUser().getId() == 15) {
        LOGGER.debug(LOG_FMT_4, USER18PUBLISH2_EXECUTIONREPORT_EQ, balanceAdminMessage, EXECUTIONREPORT_EQ, balanceAdminMessage);
      }
    } else {
      final PositionReportMessage positionReportMessage = PositionReportMessage.createPositionReportMessage(posReqResult, user,
          balanceAdminMessage.getSenderCompId(), balanceAdminMessage.getPositionArr(), balanceAdminMessage.getPositionsLength(), 0, 0);
      positionReportMessage.setSnapId(balanceAdminMessage.getSnapId());
      positionReportMessage.setSourceSeqNum(balanceAdminMessage.getSourceSeqNum());
      positionReportMessage.setKafkaRecordOffset(balanceAdminMessage.getKafkaRecordOffset());
      positionReportMessage.setTransactionId(balanceAdminMessage.getTransactionId());
      positionReportMessage.setLastMessageInTransaction(balanceAdminMessage.isLastMessageInTransaction());

      if (LOGGER.isDebugEnabled() && balanceAdminMessage.getUser().getId() == 15) {
        LOGGER.debug(LOG_FMT_4, USER18PUBLISH2_EXECUTIONREPORT_EQ, balanceAdminMessage, POSITIONREPORTMESSAGE_EQ, positionReportMessage);
      }
      decodeAndPrint(bytesWithKafkaOffset);//todo remove after testing
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, positionReportMessage);
      PositionReportObjectPool.returnObject(positionReportMessage);
    }
  }

  public void publish(final BalanceAdminMessage balanceAdminMessage) {
    try {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_2, PUBLISH_BALANCEADMINMESSAGE_EQ, balanceAdminMessage);
      }

      // this is the original code to publish position update
      final User user = UserCache.get(balanceAdminMessage.getUserId());
      final PositionReportEncoderCache positionReportEncoderCache = PositionReportEncoderCache.get();
      marginCheck.updateRiskAndCalcBankruptcyPrices(user, positionReportEncoderCache.getUsdMarkPricesToSet());

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_6, ">> publish2 balanceAdminMessage=", balanceAdminMessage, USERID_EQ, balanceAdminMessage.getUserId(),
            ">> publish2 user=", user);
      }

      final int posReqResult = (balanceAdminMessage.getTxType() > 4000) ? balanceAdminMessage.getTxType() : BALANCE_ADMIN_POS_RPT;
      publishPositionReport(balanceAdminMessage, posReqResult, positionReportEncoderCache);

      BalanceAdminMessageObjectPool.returnObject(balanceAdminMessage);
    } catch (Exception e) {
      LOGGER.error("error, balanceAdminMessage=" + balanceAdminMessage, e);
    }
  }

  public void publish(final UserAdminMessage userAdminMessage) {

    try {
      short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);
      final UserAdminEncoderCache cache = UserAdminEncoderCache.get();
      final UserAdminMessageEncoder userAdminMessageEncoder = cache.getEncoder();

      final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      userAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      headerEncoder.transactionId(userAdminMessage.getTransactionId());
      headerEncoder.transactionEnd(userAdminMessage.isLastMessageInTransaction() ? (short) 1 : (short) 0);
      encodedLength += headerEncoder.encodedLength();

      userAdminMessageEncoder.updateType(userAdminMessage.getUpdateType());
      userAdminMessageEncoder.userId(userAdminMessage.getUserId());
      userAdminMessageEncoder.username(userAdminMessage.getUsername());
      userAdminMessageEncoder.password(userAdminMessage.getPassword());
      userAdminMessageEncoder.firmId(userAdminMessage.getFirmId());
      userAdminMessageEncoder.feeTier(userAdminMessage.getFeeTier());
      userAdminMessageEncoder.requestStatus(userAdminMessage.getRequestStatus());
      userAdminMessageEncoder.lmm(userAdminMessage.isLmm() ? (short) 1 : 0);
      userAdminMessageEncoder.externalId(userAdminMessage.getExternalId());
      userAdminMessageEncoder.userType(userAdminMessage.getUserType());
      userAdminMessageEncoder.status(userAdminMessage.getStatus());
      userAdminMessageEncoder.accountType(userAdminMessage.getAccountType());
      userAdminMessageEncoder.useDiscountFeesCoin(userAdminMessage.isUseDiscountFeesCoin() ? (short) 1 : 0);

      userAdminMessageEncoder.sourceSeqNum(userAdminMessage.getSourceSeqNum()); // sourceSeqNum
      userAdminMessageEncoder.kafkaRecordOffset(userAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset
      userAdminMessageEncoder.triggerTimeMillis(userAdminMessage.getTriggerTimeMillis());
      userAdminMessageEncoder.routeToDestination(userAdminMessage.getRouteToDestination());
      userAdminMessageEncoder.senderInstanceId(Context.getInstanceId());

      encodedLength += userAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LOGGER.isTraceEnabled()) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < encodedLength; i++) {
          s.append((int) adminMessageBuffer.get(i)).append(",");
        }
        LOGGER.trace(LOG_FMT_6, ADMINMESSAGEBUFFER_EQ, (int) encodedLength, SB_EQ, s, ADMINMESSAGEBUFFER_EQ, adminMessageBuffer.toString());
      }

      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.ADMIN_API, userAdminMessage);

    } catch (Exception e) {
      LOGGER.error("error, userAdminMessage=" + userAdminMessage, e);
    }
  }

  public void publish(final OptionPricingMessage optionPricingMessage) {
    final OptionPricingEncoderCache cache = OptionPricingEncoderCache.get();
    final OptionPricingFeedEncoder encoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    encoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, optionPricingMessage);
    encodedLength += headerEncoder.encodedLength();

    encoder.messageSequenceNumber(optionPricingMessage.getMessageSequenceNumber());
    encoder.sentTime(optionPricingMessage.getSentTime());
    encoder.securityId(optionPricingMessage.getSecurityId());
    encoder.updateType(optionPricingMessage.getUpdateType());
    encoder.underlyerId(optionPricingMessage.getUnderlyerId());
    encoder.strikePrice(optionPricingMessage.getStrikePrice());
    encoder.expireTimeMillis(optionPricingMessage.getExpireTimeMillis());
    encoder.usdStrikePrice(optionPricingMessage.getUsdStrikePrice());
    encoder.usdUnderlyerPrice(optionPricingMessage.getUsdUnderlyerPrice());
    encoder.usdModelPrice(optionPricingMessage.getUsdModelPrice());
    encoder.interestRate(optionPricingMessage.getInterestRate());
    encoder.timeToExpire(optionPricingMessage.getTimeToExpire());
    encoder.dividend(optionPricingMessage.getDividend());
    encoder.delta(optionPricingMessage.getDelta());
    encoder.theta(optionPricingMessage.getTheta());
    encoder.rho(optionPricingMessage.getRho());
    encoder.normalCDF(optionPricingMessage.getNormalCDF());
    encoder.gamma(optionPricingMessage.getGamma());
    encoder.vega(optionPricingMessage.getVega());
    encoder.sigma(optionPricingMessage.getSigma());
    encoder.bid(optionPricingMessage.getBid());
    encoder.ask(optionPricingMessage.getAsk());
    encoder.last(optionPricingMessage.getLast());
    encoder.openQty(optionPricingMessage.getOpenQty());

    // convert and publish
    encodedLength += encoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, optionPricingMessage);

    // return to pool
    OptionPricingObjectPool.returnObject(optionPricingMessage);
  }

  public void publish(final MassCancelOrder massCancelOrder) {
    final MassCancelEncoderCache cache = MassCancelEncoderCache.get();
    final MassCancelOrderEncoder massCancelEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    massCancelEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, massCancelOrder);
    encodedLength += headerEncoder.encodedLength();

    massCancelEncoder.securityId(massCancelOrder.getSecurityId());
    massCancelEncoder.cancelId(massCancelOrder.getCancelId());
    massCancelEncoder.cancelPriority(massCancelOrder.getCancelPriority());
    massCancelEncoder.secondaryOrderId(massCancelOrder.getSecondaryOrderId());
    massCancelEncoder.clOrdID(massCancelOrder.getClOrdId());
    massCancelEncoder.userId(massCancelOrder.getAccount());
    if (massCancelOrder.getOrdType() != null) {
      massCancelEncoder.ordType(massCancelOrder.getOrdType());
    }
    massCancelEncoder.type((short) massCancelOrder.getType());
    massCancelEncoder.submitterId(massCancelOrder.getSubmitterId());

    // convert and publish
    encodedLength += massCancelEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, massCancelOrder);
  }

  public void publish(final ExecutionReportMessage executionReport) {
    final ExecutionReportEncoderCache cache = ExecutionReportEncoderCache.get();
    final ExecutionReportEncoder executionReportEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    executionReportEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, executionReport);
    encodedLength += headerEncoder.encodedLength();

    executionReportEncoder.clOrdID(String.valueOf(executionReport.getClOrdId()));
    executionReportEncoder.securityId(executionReport.getSecurityId());
    executionReportEncoder.symbol(executionReport.getSymbol());
    executionReportEncoder.userId(executionReport.getUser() == null ? 0 : executionReport.getUser().getId());
    executionReportEncoder.transactTime(executionReport.getTimestamp());
    executionReportEncoder.side(executionReport.getSide());
    executionReportEncoder.ordType(executionReport.getOrdType());
    executionReportEncoder.execType(executionReport.getExecType());
    executionReportEncoder.ordStatus(executionReport.getOrdStatus());
    executionReportEncoder.submitterId(executionReport.getSubmitterId());
    executionReportEncoder.cancelId(executionReport.getCancelId());
    executionReportEncoder.feeEstimatedQuantity(executionReport.getFeeEstimatedQuantity());
    executionReportEncoder.feeAccumulatedQuantity(executionReport.getFeeAccumulatedQuantity());
    executionReportEncoder.availableEstimatedQuantity(executionReport.getAvailableEstimatedQuantity());
    executionReportEncoder.availableAccumulatedQuantity(executionReport.getAvailableAccumulatedQuantity());

    // Time in force is not set for cancel orders.
    if (executionReport.getTimeInForce() != null) {
      executionReportEncoder.timeInForce(executionReport.getTimeInForce());
    }

    if (executionReport.getExecType() == ExecType.CANCELED) {
      executionReportEncoder.cancelType(executionReport.getCancelType());
    } else {
      executionReportEncoder.cancelType((short) 0);
    }

    executionReportEncoder.targetStrategy(executionReport.getTargetStrategy());
    executionReportEncoder.isHidden(executionReport.isHidden() ? BooleanType.TRUE : BooleanType.FALSE);
    executionReportEncoder.isLiquidation(executionReport.isLiquidation() ? BooleanType.TRUE : BooleanType.FALSE);
    executionReportEncoder.expireTime(executionReport.getExpireTime());
    executionReportEncoder.orderId(executionReport.getOrderId());
    executionReportEncoder.secondaryOrderId(executionReport.getSecondaryOrderId());
    executionReportEncoder.execId(executionReport.getExecId());
    executionReportEncoder.secondaryExecId(executionReport.getSecondaryExecId());
    executionReportEncoder.leavesQty(executionReport.getLeavesQty());
    executionReportEncoder.leavesQtyScale((short) executionReport.getLeavesQtyScale());
    executionReportEncoder.cumQty(executionReport.getCumQty());
    executionReportEncoder.cumQtyScale((short) executionReport.getCumQtyScale());
    executionReportEncoder.lastQty(executionReport.getLastQty());
    executionReportEncoder.lastQtyScale((short) executionReport.getLastQtyScale());
    executionReportEncoder.avgPx(executionReport.getAvgPx());
    executionReportEncoder.avgPxScale((short) executionReport.getAvgPxScale());
    executionReportEncoder.price(executionReport.getPrice());
    executionReportEncoder.priceScale((short) executionReport.getPriceScale());
    executionReportEncoder.price2(executionReport.getPrice2()); // was setting discretionPrice
    executionReportEncoder.price2Scale((short) executionReport.getPrice2Scale());
    executionReportEncoder.stopPx(executionReport.getStopPx());
    executionReportEncoder.stopPxScale((short) executionReport.getStopPxScale());

    executionReportEncoder.assetId(executionReport.getAssetId());
    executionReportEncoder.tokenId(executionReport.getTokenId());
    executionReportEncoder.selectId(executionReport.getSelectId());


    executionReportEncoder.openOrderCount(executionReport.getOpenOrderCount());
    executionReportEncoder.bestBidPx(executionReport.getBestBidPx());
    executionReportEncoder.bestAskPx(executionReport.getBestAskPx());
    executionReportEncoder.bestPxScale(executionReport.getBestPxScale());
    executionReportEncoder.notional(executionReport.getNotional());

    // encode BidsNotional in startCash
    // encode AsksNotional in endCash
    final Position[] positionArr = executionReport.getUser().getPositionArr();
    if ((positionArr != null) && (positionArr.length > executionReport.getSecurityId())) {
      final Position position = positionArr[executionReport.getSecurityId()];
      if (position != null) {
        final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();
        if (userOpenOrdersByPair != null) {
          executionReportEncoder.bidsNotional(MbxMath.roundToBestPrecision(userOpenOrdersByPair.getBidsNotional()));
          executionReportEncoder.asksNotional(MbxMath.roundToBestPrecision(userOpenOrdersByPair.getAsksNotional()));
        }
      }
    }
    // flag indicating if this is the second execution, crossing position sides
    if (executionReport.isPositionSideCrossed())
      executionReportEncoder.isPositionSideCrossed(BooleanType.TRUE);
    else
      executionReportEncoder.isPositionSideCrossed(BooleanType.FALSE);


    executionReportEncoder.orderQty(executionReport.getOrderQty());
    executionReportEncoder.orderQtyScale((short) executionReport.getOrderQtyScale());


    final Instrument feeInstrument = InstrumentCache.get(executionReport.getFeePositionId());
    if (feeInstrument != null) {
      executionReportEncoder.feePositionId(executionReport.getFeePositionId());
      executionReportEncoder.feePositionQuantityChange(executionReport.getFeePositionQuantityChange());
      executionReportEncoder.feePositionQuantityChangeScale((short) feeInstrument.getQuantityScale());
      executionReportEncoder.isPaidToInsurance(executionReport.isPaidToInsurance() ? BooleanType.TRUE : BooleanType.FALSE);
    } else {
      executionReportEncoder.feePositionId(0);
      executionReportEncoder.feePositionQuantityChange(0);
      executionReportEncoder.feePositionQuantityChangeScale((short) 0);
      executionReportEncoder.isPaidToInsurance(BooleanType.FALSE);
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_8, EXECPUB_GETFEEPOSITIONID_EQ, executionReport.getFeePositionId(), FEEINSTRUMENT_EQ, feeInstrument, CHANGE_EQ,
          executionReport.getFeePositionQuantityChange(), EXECUTIONREPORT_EQ, executionReport);
    }

    // Handle trade specific fields
    if (ExecType.TRADE.equals(executionReport.getExecType()) || ExecType.CALCULATED.equals(executionReport.getExecType())) {
      executionReportEncoder.lastPx(executionReport.getLastPx());
      executionReportEncoder.lastPxScale((short) executionReport.getLastPxScale());
      executionReportEncoder.avgPx(executionReport.getAvgPx());
      executionReportEncoder.avgPxScale((short) executionReport.getAvgPxScale());
      executionReportEncoder.aggresorSide(executionReport.getAggressorSide()); // taker side

      executionReportEncoder.settlePositionId(executionReport.getSettlePositionId());

      final Instrument settleInstrument = InstrumentCache.get(executionReport.getSettlePositionId());
      executionReportEncoder.settlePositionQuantityChange(executionReport.getSettlePositionQuantityChange());
      if (settleInstrument != null) {
        executionReportEncoder.settlePositionQuantityChangeScale((short) settleInstrument.getQuantityScale());
      } else {
        executionReportEncoder.settlePositionQuantityChangeScale((short) 0);
      }
      executionReportEncoder.counterPartyId(executionReport.getCounterpartyId());
    } else { // reset values that aren't set in executionReport
      executionReportEncoder.lastPx(0);
      executionReportEncoder.lastPxScale((short) 0);
      executionReportEncoder.counterPartyId(0);
      executionReportEncoder.settlePositionQuantityChange(0);
    }

    if (Context.isPublishMarketData()) {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(executionReport.getSecurityId());
      if (instrumentPair.isInMarketDataQueue().compareAndSet(0, 1))
        Context.getMarketDataBuilderQueue().add(instrumentPair);
      else if (Context.getMarketDataBuilderQueue().isEmpty()) {
        instrumentPair.isInMarketDataQueue().set(0);
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_2, ">>> isPublishMarketData reset: ", instrumentPair.getSymbol(), ", lock=",
              "" + instrumentPair.isInMarketDataQueue().get(), ", pair=", instrumentPair);
        }
      } else {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_2, ">>> isPublishMarketData skip: ", instrumentPair.getSymbol(), ", lock=",
              "" + instrumentPair.isInMarketDataQueue().get(), ", pair=", instrumentPair);
        }
      }
    }


    if (LOGGER.isTraceEnabled() && executionReport.getUser().getId() == 18) {
      LOGGER.trace(LOG_FMT_2, ">>> 18publish1 executionReport=", executionReport);
    }

    if (executionReport.getExecType() == ExecType.TRADE || executionReport.getExecType() == ExecType.NEW
        || executionReport.getExecType() == ExecType.CANCELED || executionReport.getExecType() == ExecType.EXPIRED
        || executionReport.getOrdStatus() == OrdStatus.PARTIALLY_FILLED || executionReport.getOrdStatus() == OrdStatus.FILLED
        || executionReport.getOrdStatus() == OrdStatus.CALCULATED) {
      final User user = executionReport.getUser();
      final PositionReportEncoderCache positionReportEncoderCache = PositionReportEncoderCache.get();
      marginCheck.updateRiskAndCalcBankruptcyPrices(user, positionReportEncoderCache.getUsdMarkPricesToSet());

      final boolean isLastMessage = executionReport.isLastMessageInTransaction();
      try {
        executionReport.setLastMessageInTransaction(false);
        final int posReqResult = executionReport.getExecType() == ExecType.TRADE ? TX_TRADE_FILL : DEFAULT_POS_RPT;
        publishPositionReport(executionReport, posReqResult, positionReportEncoderCache);
      } catch (Exception e) {
        // Do nothing
      } finally {
        executionReport.setLastMessageInTransaction(isLastMessage);
      }
    }

    // convert and publish
    encodedLength += executionReportEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, executionReport);

    // return to pool
    ExecutionReportObjectPool.returnObject(executionReport);
  }

  // this publish method is different because its encoded separately
  // build must be called first from separate thread
  public void publish(final MarketDataSnapMessage marketDataSnapMessage) {
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, "publish marketDataSnapMessage: ", marketDataSnapMessage.getSymbol());
      }

      final MarketDataSnapshotFullRefreshEncoder mdEncoder = marketDataSnapMessage.getEncoder();
      final ByteBuffer directBuffer = marketDataSnapMessage.getDirectBuffer();
      final UnsafeBuffer unsafeBuffer = marketDataSnapMessage.getUnsafeBuffer();
      final MessageHeaderEncoder headerEncoder = marketDataSnapMessage.getHeaderEncoder();

      short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
      encodedLength += headerEncoder.encodedLength();

      // convert and publish
      encodedLength += mdEncoder.encodedLength();
      directBuffer.limit(encodedLength);
      unsafeBuffer.putShort(0, encodedLength);
      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, marketDataSnapMessage);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public void publish(final CancelRejectMessage cancelRejectMessage) {
    final CancelRejectEncoderCache cache = CancelRejectEncoderCache.get();
    final OrderCancelRejectEncoder cancelRejectEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    cancelRejectEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, cancelRejectMessage);
    encodedLength += headerEncoder.encodedLength();

    cancelRejectEncoder.orderId(cancelRejectMessage.getOrderId());
    cancelRejectEncoder.clOrdID(String.valueOf(cancelRejectMessage.getClOrdId()));
    cancelRejectEncoder.userId(cancelRejectMessage.getAccount());
    cancelRejectEncoder.ordStatus(cancelRejectMessage.getOrdStatus());
    cancelRejectEncoder.cxlRejReason(cancelRejectMessage.getCxlRejReason());
    cancelRejectEncoder.secondaryOrderId(cancelRejectMessage.getSecondaryOrderId());
    cancelRejectEncoder.cancelId(cancelRejectMessage.getCancelId());
    cancelRejectEncoder.cancelReplaceId(cancelRejectMessage.getCancelReplaceId());
    cancelRejectEncoder.submitterId(cancelRejectMessage.getSubmitterId());

    // convert and publish
    encodedLength += cancelRejectEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, cancelRejectMessage);

    // return to pool
    CancelRejectObjectPool.returnObject(cancelRejectMessage);
  }

  public void publish(final BusinessRejectMessage businessRejectMessage) {
    final BusinessRejectEncoderCache cache = BusinessRejectEncoderCache.get();
    final BusinessRejectEncoder businessRejectEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    businessRejectEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, businessRejectMessage);
    encodedLength += headerEncoder.encodedLength();

    businessRejectEncoder.refMsgType(businessRejectMessage.getRefMsgType());
    businessRejectEncoder.businessRejectReason(businessRejectMessage.getBusinessRejectReason());
    businessRejectEncoder.businessRejectRefId(businessRejectMessage.getOrderId());
    businessRejectEncoder.orderId(businessRejectMessage.getOrderId());
    businessRejectEncoder.text(businessRejectMessage.getText());
    businessRejectEncoder.cancelId(businessRejectMessage.getCancelId());
    businessRejectEncoder.cancelReplaceId(businessRejectMessage.getCancelReplaceId());
    businessRejectEncoder.clOrdId(businessRejectMessage.getClOrdId());
    businessRejectEncoder.pairId(businessRejectMessage.getPairId());
    businessRejectEncoder.secondaryOrderId(businessRejectMessage.getSecondaryOrderId());

    // convert and publish
    encodedLength += businessRejectEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, businessRejectMessage);

    BusinessRejectObjectPool.returnObject(businessRejectMessage);
  }

  public void publish(final LogonMessage logonMessage) {
    final LogonEncoderCache cache = LogonEncoderCache.get();
    final LogonEncoder logonEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    logonEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, logonMessage);
    encodedLength += headerEncoder.encodedLength();

    final SessionInfo sessionInfo = logonMessage.getSessionInfo();

    SessionInfoCache.put(sessionInfo.getSenderCompId(), sessionInfo);
    if (sessionInfo.getSenderCompId().equals("1000000009")) // tradeapi
      SessionInfoCache.putTradeApi(sessionInfo);


    logonEncoder.heartBtInt(logonMessage.getHeartBeatInterval());

    // convert and publish
    encodedLength += logonEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, logonMessage);


    // we may need to get a clone of the positionArr here
    final User user = logonMessage.getUser();
    final PositionReportMessage positionReportMessage = PositionReportMessage.createPositionReportMessage(TX_RESTATE, user,
        logonMessage.getSenderCompId(), logonMessage.getPositionArr(), logonMessage.getPositionsLength(), 0, 0);

    final PositionReportEncoderCache positionEncoderCache = PositionReportEncoderCache.get();
    publish(positionReportMessage, DEFAULT_POS_RPT, positionEncoderCache);
  }

  public void publish(final LogoutMessage logoutMessage) {
    final LogoutEncoderCache cache = LogoutEncoderCache.get();
    final LogoutEncoder logoutEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    logoutEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, logoutMessage);
    encodedLength += headerEncoder.encodedLength();

    final String senderCompId = logoutMessage.getSenderCompId();
    if (logoutMessage.isLogonFailure() || !SessionInfoCache.containsKey(senderCompId)) {
      SessionInfoCache.put(senderCompId, logoutMessage.getSessionInfo());
    }

    if (logoutMessage.getText() != null) {
      logoutEncoder.text(logoutMessage.getText());
    }

    // convert and publish
    encodedLength += logoutEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, logoutMessage);
  }

  public void publish(final HeartbeatMessage message) {
    final HearbeatEncoderCache cache = HearbeatEncoderCache.get();
    final HeartbeatEncoder heartbeatEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    heartbeatEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    encodedLength += headerEncoder.encodedLength();


    // convert and publish
    encodedLength += heartbeatEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, message);
  }


  public void publish(final SequenceResetMessage message) {
    final SequenceResetEncoderCache cache = SequenceResetEncoderCache.get();
    final SequenceResetEncoder sequenceResetEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    sequenceResetEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    encodedLength += headerEncoder.encodedLength();


    final SessionInfo sessionInfo = SessionInfoCache.get(message.getSenderCompId());
    if (sessionInfo == null) {
      LOGGER.debug(LOG_FMT_4, "error sessionInfo is null, message=", message, ", senderComp=", message.getSenderCompId());
    } else {
      sequenceResetEncoder.newSequenceNo(sessionInfo.getMessageSequenceNumber());
    }

    sequenceResetEncoder.gapFillFlag(message.isGapFillFlag() ? BooleanType.TRUE : BooleanType.FALSE);

    // convert and publish
    encodedLength += sequenceResetEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, message);
  }

  public void publish(final ResendRequestMessage resendRequestMessage) {
    final ResendRequestEncoderCache cache = ResendRequestEncoderCache.get();
    final ResendRequestEncoder resendRequestEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    resendRequestEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, resendRequestMessage);
    encodedLength += headerEncoder.encodedLength();

    if (resendRequestMessage.isOriginated()) {
      resendRequestEncoder.beginSequenceNo(resendRequestMessage.getBeginSeqNo());
      resendRequestEncoder.endSequenceNo(resendRequestMessage.getEndSeqNo());

      // convert and publish
      encodedLength += resendRequestEncoder.encodedLength();
      directBuffer.limit(encodedLength);
      unsafeBuffer.putShort(0, encodedLength);
      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, resendRequestMessage);

      return;
    }

    // resend
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, "Replay resendRequestMessage=", resendRequestMessage);
    }
  }

  public void publish(final NetworkStatusMessage message) {
    final NetworkStatusEncoderCache cache = NetworkStatusEncoderCache.get();
    final NetworkStatusEncoder networkStatusEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    networkStatusEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    encodedLength += headerEncoder.encodedLength();

    networkStatusEncoder.requestId(message.getRequestId());
    networkStatusEncoder.responseId(message.getResponseId());
    networkStatusEncoder.orderSequenceNumber(message.getOrderSequenceNumber());
    networkStatusEncoder.snapId(message.getSnapId());
    networkStatusEncoder.sequenceNumber(message.getSequenceNumber());

    // convert and publish
    encodedLength += networkStatusEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, message);
  }

  public void publish(final MarketDataFeed mdFeed) {
    final MarketDataFeedEncoderCache cache = MarketDataFeedEncoderCache.get();
    final MarketDataFeedEncoder mdFeedEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    mdFeedEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, mdFeed);
    encodedLength += headerEncoder.encodedLength();

    mdFeedEncoder.messageSequenceNumber(mdFeed.getSequenceNumber());
    mdFeedEncoder.sentTime(mdFeed.getSentTime());

    MarketDataFeedEncoder.MdEntrieGroupEncoder mdEntryGroupEncoder = mdFeedEncoder.mdEntrieGroupCount(mdFeed.getEntryCount());
    final int usdMarkArrSize = mdFeed.getUsdMarkArr().length;

    for (int i = 0; i < usdMarkArrSize; i++) {
      if (mdFeed.getUsdMarkArr()[i] > 0) {
        mdEntryGroupEncoder = mdEntryGroupEncoder.next();
        mdEntryGroupEncoder.securityId(i);
        mdEntryGroupEncoder.usdMark(mdFeed.getUsdMarkArr()[i]);
        mdEntryGroupEncoder.usdSpotIndex(mdFeed.getUsdSpotIndexArr()[i]);
      }
    }

    // convert and publish
    encodedLength += mdFeedEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, mdFeed);

    // return to pool
    MarketDataFeedObjectPool.returnObject(mdFeed);
  }

  public void publish(final SnapResponseAdminMessage message) {
    try {
      short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      final SnapResponseAdminMessageEncoder snapResponseAdminMessageEncoder = new SnapResponseAdminMessageEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder = new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      snapResponseAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      headerEncoder.transactionId(message.getTransactionId());
      headerEncoder.transactionEnd(message.isLastMessageInTransaction() ? (short) 1 : (short) 0);
      encodedLength += headerEncoder.encodedLength();

      if (useKafka) {
        if (message.getOutputKafkaRecordOffset() <= 0) {
          PublisherEncoderCache.blockWaitGetLock();
          Context.getKafkaPublisher().enqueueFlushAndWait();

          long lastSendOffset = Context.getKafkaPublisher().getLastSendOffset();
          if (-1 != lastSendOffset) {
            ++lastSendOffset;
          }
          message.setOutputKafkaRecordOffset(lastSendOffset);
        }

        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_4, "Snapshot: snapId=", message.getSnapId(), SENDSTATEADMIN_ENCODEDLENGTH_EQ,
              message.getOutputKafkaRecordOffset());
        }
      }

      snapResponseAdminMessageEncoder.externalId(message.getExternalId());
      snapResponseAdminMessageEncoder.inputKafkaRecordOffset(message.getInputKafkaRecordOffset());
      snapResponseAdminMessageEncoder.outputKafkaRecordOffset(message.getOutputKafkaRecordOffset());
      snapResponseAdminMessageEncoder.orderSequenceNumber(message.getOrderSequenceNumber());
      snapResponseAdminMessageEncoder.sequenceNumber(message.getSequenceNumber());
      snapResponseAdminMessageEncoder.snapId(message.getSnapId());
      snapResponseAdminMessageEncoder.sourceSeqNum(message.getSourceSeqNum());
      snapResponseAdminMessageEncoder.triggerTimeMillis(message.getTriggerTimeMillis());
      snapResponseAdminMessageEncoder.routeToDestination(message.getRouteToDestination());
      snapResponseAdminMessageEncoder.senderInstanceId(Context.getInstanceId());
      snapResponseAdminMessageEncoder.orderId(message.getOrderId());
      snapResponseAdminMessageEncoder.execId(message.getExecId());

      encodedLength += snapResponseAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LOGGER.isDebugEnabled()) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encodedLength; i++) {
          sb.append((int) adminMessageBuffer.get(i)).append(",");
        }
        LOGGER.debug(LOG_FMT_4, ">>> snapResponseAdminMessageEncoder encodedLength=", (int) encodedLength, SB_EQ, sb);
      }


      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.ADMIN_API, message);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final boolean publish(final TradeStateAdminMessage tradeStateAdminMessage) {
    try {
      short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      final TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder = new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      headerEncoder.transactionId(tradeStateAdminMessage.getTransactionId());
      headerEncoder.transactionEnd(tradeStateAdminMessage.isLastMessageInTransaction() ? (short) 1 : (short) 0);
      encodedLength += headerEncoder.encodedLength();

      tradeStateAdminMessageEncoder.marketStatus(tradeStateAdminMessage.getMarketStatus()); // MarketStatus.RESTATE
      tradeStateAdminMessageEncoder.securityId(tradeStateAdminMessage.getSecurityId());
      tradeStateAdminMessageEncoder.triggerTimeMillis(tradeStateAdminMessage.getTriggerTimeMillis());
      tradeStateAdminMessageEncoder.routeToDestination(tradeStateAdminMessage.getRouteToDestination());
      tradeStateAdminMessageEncoder.senderInstanceId(Context.getInstanceId());

      tradeStateAdminMessageEncoder.externalId(tradeStateAdminMessage.getExternalId());
      tradeStateAdminMessageEncoder.sourceSeqNum(tradeStateAdminMessage.getSourceSeqNum()); // sourceSeqNum
      tradeStateAdminMessageEncoder.kafkaRecordOffset(tradeStateAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset

      encodedLength += tradeStateAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LOGGER.isDebugEnabled()) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encodedLength; i++) {
          sb.append((int) adminMessageBuffer.get(i)).append(",");
        }
        LOGGER.debug(LOG_FMT_6, SENDSTATEADMIN_ENCODEDLENGTH_EQ, (int) encodedLength, SB_EQ, sb, TRADESTATEADMINMESSAGE_EQ,
            tradeStateAdminMessage);
      }


      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.ADMIN_API, tradeStateAdminMessage);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return true;
  }


  public final boolean publish(final SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
    try {
      short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      final SecurityDefinitionAdminMessageEncoder securityDefinitionAdminMessageEncoder = new SecurityDefinitionAdminMessageEncoder();
      final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      securityDefinitionAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      headerEncoder.transactionId(securityDefinitionAdminMessage.getTransactionId());
      headerEncoder.transactionEnd(securityDefinitionAdminMessage.isLastMessageInTransaction() ? (short) 1 : (short) 0);
      encodedLength += headerEncoder.encodedLength();

      securityDefinitionAdminMessageEncoder.updateType(securityDefinitionAdminMessage.getUpdateType());
      securityDefinitionAdminMessageEncoder.securityId(securityDefinitionAdminMessage.getSecurityId());
      securityDefinitionAdminMessageEncoder.assetType(securityDefinitionAdminMessage.getAssetType());
      securityDefinitionAdminMessageEncoder.baseId(securityDefinitionAdminMessage.getBaseId());
      securityDefinitionAdminMessageEncoder.quotedId(securityDefinitionAdminMessage.getQuotedId());
      securityDefinitionAdminMessageEncoder.priceScale((short) securityDefinitionAdminMessage.getPriceScale());
      securityDefinitionAdminMessageEncoder.quantityScale((short) securityDefinitionAdminMessage.getQuantityScale());
      securityDefinitionAdminMessageEncoder.orderBookStrategy((short) securityDefinitionAdminMessage.getOrderBookStrategy());
      securityDefinitionAdminMessageEncoder.preOrderCheckStrategy((short) securityDefinitionAdminMessage.getPreOrderCheckStrategy());
      securityDefinitionAdminMessageEncoder.symbol(securityDefinitionAdminMessage.getSymbol());
      securityDefinitionAdminMessageEncoder.name(securityDefinitionAdminMessage.getName());
      securityDefinitionAdminMessageEncoder.settleType(securityDefinitionAdminMessage.getSettleType());
      securityDefinitionAdminMessageEncoder.maintMarginPercent(securityDefinitionAdminMessage.getMaintMarginBasisPoints());
      securityDefinitionAdminMessageEncoder.requiredMarginPercent(securityDefinitionAdminMessage.getRequiredMarginBasisPoints());
      securityDefinitionAdminMessageEncoder.minQty(securityDefinitionAdminMessage.getMinQty());
      securityDefinitionAdminMessageEncoder.maxQty(securityDefinitionAdminMessage.getMaxQty());
      securityDefinitionAdminMessageEncoder.maxPrice(securityDefinitionAdminMessage.getMaxPrice());
      securityDefinitionAdminMessageEncoder.supportOrderType(securityDefinitionAdminMessage.getSupportOrderType());
      securityDefinitionAdminMessageEncoder.commissionType(securityDefinitionAdminMessage.getCommissionType());
      securityDefinitionAdminMessageEncoder.triggerTimeMillis(securityDefinitionAdminMessage.getTriggerTimeMillis());
      securityDefinitionAdminMessageEncoder.routeToDestination(securityDefinitionAdminMessage.getRouteToDestination());
      securityDefinitionAdminMessageEncoder.senderInstanceId(Context.getInstanceId());
      securityDefinitionAdminMessageEncoder.secondaryOrderId(securityDefinitionAdminMessage.getSecondaryOrderId());
      securityDefinitionAdminMessageEncoder.secondaryExecId(securityDefinitionAdminMessage.getSecondaryExecId());
      securityDefinitionAdminMessageEncoder.arrSize(securityDefinitionAdminMessage.getArrSize());
      securityDefinitionAdminMessageEncoder.underlyerId(securityDefinitionAdminMessage.getUnderlyerId());
      securityDefinitionAdminMessageEncoder.strikePrice(securityDefinitionAdminMessage.getStrikePrice());
      securityDefinitionAdminMessageEncoder.expireTimeMillis(securityDefinitionAdminMessage.getExpireTimeMillis());
      securityDefinitionAdminMessageEncoder.expireRollTimeMillis(securityDefinitionAdminMessage.getExpireRollTimeMillis());
      securityDefinitionAdminMessageEncoder.symbolRollCount(securityDefinitionAdminMessage.getSymbolRollCount());
      securityDefinitionAdminMessageEncoder.marginCurveId(securityDefinitionAdminMessage.getMarginCurveId());
      securityDefinitionAdminMessageEncoder
          .collateralMarginPercentDiscount(securityDefinitionAdminMessage.getCollateralMarginPercentDiscount());

      securityDefinitionAdminMessageEncoder.auctionStartTimeHrGMT(securityDefinitionAdminMessage.getAuctionStartTimeHrGMT());
      securityDefinitionAdminMessageEncoder.auctionDurationTime(securityDefinitionAdminMessage.getAuctionDurationTime());
      securityDefinitionAdminMessageEncoder.auctionFixingAttempts(securityDefinitionAdminMessage.getAuctionFixingAttempts());
      securityDefinitionAdminMessageEncoder.auctionFixingWaitTime(securityDefinitionAdminMessage.getAuctionFixingWaitTime());

      securityDefinitionAdminMessageEncoder.usdStrikePrice().value((long) (securityDefinitionAdminMessage.getUsdStrikePrice() * 100))
          .scale(2);
      securityDefinitionAdminMessageEncoder.usdUnderlyerPrice().value((long) (securityDefinitionAdminMessage.getUsdUnderlyerPrice() * 100))
          .scale(2);
      securityDefinitionAdminMessageEncoder.usdModelPrice().value((long) (securityDefinitionAdminMessage.getUsdModelPrice() * 100))
          .scale(2);
      securityDefinitionAdminMessageEncoder.interestRate().value((long) (securityDefinitionAdminMessage.getInterestRate() * 1000000))
          .scale(6);
      securityDefinitionAdminMessageEncoder.dividend().value((long) (securityDefinitionAdminMessage.getDividend() * 100)).scale(2);
      securityDefinitionAdminMessageEncoder.timeToExpire().value((long) (securityDefinitionAdminMessage.getTimeToExpire() * 100)).scale(2);
      securityDefinitionAdminMessageEncoder.delta().value((long) (securityDefinitionAdminMessage.getDelta() * 1000000)).scale(6);
      securityDefinitionAdminMessageEncoder.theta().value((long) (securityDefinitionAdminMessage.getTheta() * 1000000)).scale(6);
      securityDefinitionAdminMessageEncoder.rho().value((long) (securityDefinitionAdminMessage.getRho() * 1000000)).scale(6);
      securityDefinitionAdminMessageEncoder.normalCDF().value((long) (securityDefinitionAdminMessage.getNormalCDF() * 1000000)).scale(6);
      securityDefinitionAdminMessageEncoder.gamma().value((long) (securityDefinitionAdminMessage.getGamma() * 1000000)).scale(6);
      securityDefinitionAdminMessageEncoder.vega().value((long) (securityDefinitionAdminMessage.getVega() * 1000000)).scale(6);
      securityDefinitionAdminMessageEncoder.sigma().value((long) (securityDefinitionAdminMessage.getSigma() * 1000000)).scale(6);


      final DecimalFloatEncoder indexFeedUsdMarkEncoder = securityDefinitionAdminMessageEncoder.indexFeedUsdMark();
      indexFeedUsdMarkEncoder.value((long) (securityDefinitionAdminMessage.getIndexFeedUsdMark() * 100));
      indexFeedUsdMarkEncoder.scale(2);

      securityDefinitionAdminMessageEncoder.externalId(securityDefinitionAdminMessage.getExternalId());
      securityDefinitionAdminMessageEncoder.sourceSeqNum(securityDefinitionAdminMessage.getSourceSeqNum()); // sourceSeqNum
      securityDefinitionAdminMessageEncoder.kafkaRecordOffset(securityDefinitionAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset

      encodedLength += securityDefinitionAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LOGGER.isDebugEnabled()) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encodedLength; i++) {
          sb.append((int) adminMessageBuffer.get(i)).append(',');
        }
        LOGGER.debug(LOG_FMT_2, SECURITYDEFINITION_ENCODEDLENGTH_EQ, encodedLength);
      }

      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.ADMIN_API, securityDefinitionAdminMessage);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return true;
  }

  public final boolean publish(final FeeAdminMessage feeAdminMessage) {
    try {
      short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      final FeeAdminMessageEncoder feeAdminMessageEncoder = new FeeAdminMessageEncoder();
      final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      feeAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      headerEncoder.transactionId(feeAdminMessage.getTransactionId());
      headerEncoder.transactionEnd(feeAdminMessage.isLastMessageInTransaction() ? (short) 1 : (short) 0);
      encodedLength += headerEncoder.encodedLength();

      feeAdminMessageEncoder.updateType(feeAdminMessage.getUpdateType());
      feeAdminMessageEncoder.assetId(feeAdminMessage.getAssetId());
      feeAdminMessageEncoder.tier(feeAdminMessage.getTier());
      feeAdminMessageEncoder.feeAssetId(feeAdminMessage.getFeeInstrumentId());
      feeAdminMessageEncoder.fee(feeAdminMessage.getFee());
      feeAdminMessageEncoder.feeType(feeAdminMessage.getFeeType());
      feeAdminMessageEncoder.makerTaker(feeAdminMessage.getMakerTaker());

      feeAdminMessageEncoder.externalId(feeAdminMessage.getExternalId());
      feeAdminMessageEncoder.sourceSeqNum(feeAdminMessage.getSourceSeqNum()); // sourceSeqNum
      feeAdminMessageEncoder.kafkaRecordOffset(feeAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset
      feeAdminMessageEncoder.triggerTimeMillis(feeAdminMessage.getTriggerTimeMillis());
      feeAdminMessageEncoder.routeToDestination(feeAdminMessage.getRouteToDestination());
      feeAdminMessageEncoder.senderInstanceId(Context.getInstanceId());

      encodedLength += feeAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LOGGER.isDebugEnabled()) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encodedLength; i++) {
          sb.append((int) adminMessageBuffer.get(i)).append(',');
        }
        LOGGER.debug(LOG_FMT_4, FEEADMINMESSAGE_ENCODEDLENGTH_EQ, encodedLength, SB_EQ, sb);
      }


      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.ADMIN_API, feeAdminMessage);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return true;
  }

  private void populateHeader(final MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;
    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(ME_SEQ_ID); // matching engine seq num
    headerEncoder.transactionId(message.getTransactionId());
    headerEncoder.transactionEnd((short) (message.isLastMessageInTransaction() ? 1 : 0));
  }


  // accepts any messageType, normal or admin
  private void publishAndCache(final byte[] bytesWithKafkaOffset, final byte messageType, final Message message) {
    PublisherEncoderCache.blockWaitGetLock();

    if (bytesWithKafkaOffset.length >= 32_768) {
      LOGGER.error(LOG_FMT_2, "error 2, publishKafka bytes send length=", bytesWithKafkaOffset.length, "data=",
          StringUtil.fixToString(bytesWithKafkaOffset), MESSAGE_EQ, message);
    }

    // handle snap messages
    if (message.getSnapId() != 0) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, "publishAndCache snap=", message.getSnapId(), MESSAGE_EQ, message);
      }
      if (snapUtil == null) {
        snapUtil = new SnapUtil(message.getSnapId());

        message.setSequenceNumber(sequenceNumber++);

      } else if (message.getSnapId() != snapUtil.getSnapId()) {
        snapUtil.close();
        snapUtil = new SnapUtil(message.getSnapId());

        message.setSequenceNumber(sequenceNumber++);
      }
      message.setSequenceNumber(sequenceNumber);

      if (message instanceof SnapResponseAdminMessage) {
        // publish marker
        ((SnapResponseAdminMessage) message).setSequenceNumber(sequenceNumber);
        snapUtil.snap(bytesWithKafkaOffset, messageType, message);
        snapUtil.close();
        snapUtil = null;

        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_4, "publishAndCache snap done snap=", message.getSnapId(), MESSAGE_EQ, message);
        }
      } else {
        snapUtil.snap(bytesWithKafkaOffset, messageType, message);
        return;
      }
    }

    message.setSequenceNumber(sequenceNumber++);

    // publish Kafka
    if (useKafka) {
      Context.getKafkaPublisher().enqueueToSend(topic, bytesWithKafkaOffset, messageType);
    }
  }

  private static ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);

  public final byte[] longToBytes(final long x) {
    longBuffer.putLong(0, x);
    return longBuffer.array();
  }

  public final void setBytes(final byte[] target, final byte[] source, final int offset) {
    for (int i = 0; i < source.length; i++) {
      target[i + offset] = source[i];
    }
  }

  public final void flushPublish() {
    LOGGER.debug("Flushing message publishers");

    if (useKafka) {
      Context.getKafkaPublisher().flush();
    }

    LOGGER.debug("Flushing message publishers completed");
  }
  final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();

  public void decodeAndPrint(byte[] bytes) {
    final int OFFSET = 19;
    decoderUnsafeBuffer.wrap(bytes);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    int headerLength = OFFSET + headerDecoder.encodedLength();

    positionReportDecoder.wrap(decoderUnsafeBuffer, headerLength, headerDecoder.blockLength(),
        headerDecoder.version());
    LOGGER.info("Start ================================================== ");
    LOGGER.info("UserId: " + positionReportDecoder.userId());
    PositionReportDecoder.PositionsGroupDecoder positionsGroupDecoder = positionReportDecoder.positionsGroup();
    int positionCount =  positionsGroupDecoder.count();
    LOGGER.info("####### Position Count: " + positionCount);
    for (int i = 0; i < positionCount; i++) {
      LOGGER.info("Iteration: i = " + i);
      positionsGroupDecoder = positionsGroupDecoder.next();
      final int securityId = positionsGroupDecoder.instrumentId();
      LOGGER.info("SecurityId: " + securityId);
      LOGGER.info("Quantity: " + positionsGroupDecoder.quantity());
      try {
        PositionReportDecoder.PositionsGroupDecoder.PositionsAssetIdGroupDecoder positionsAssetIdGroupDecoder = positionsGroupDecoder.positionsAssetIdGroup();
        int positionsAssetIdGroupCount = positionsAssetIdGroupDecoder.count();
        LOGGER.info("####### Asset Position Count: " + positionsAssetIdGroupCount);
        for (int j = 0; j < positionsAssetIdGroupCount; j++) {
          LOGGER.info("Iteration: j = " + j);
          positionsAssetIdGroupDecoder = positionsAssetIdGroupDecoder.next();
          final long assetId = positionsAssetIdGroupDecoder.assetId();
          final int tokenId = positionsAssetIdGroupDecoder.tokenId();
          LOGGER.info("assetId:" + assetId + ", tokenId:" + tokenId);
        }
      } catch (Exception e) {
        e.printStackTrace();
        LOGGER.error(e.getMessage());
      }
    }

    LOGGER.info("End ================================================== ");
  }
}
