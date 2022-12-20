package com.solfini.matchengine.message;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageEncoder;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.matchengine.PublisherEncoderCache;
import com.solfini.matchengine.drmode.PositionReportParser;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.matchengine.publisher.MessagePublisher;
import com.solfini.matchengine.publisher.PositionReportEncoderCache;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.publisher.SnapUtil;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.pool.PositionReportObjectPool;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.PositionReportEncoder;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.MbxMath;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import uk.co.real_logic.artio.fields.DecimalFloat;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static com.solfini.common.Constants.BALANCE_ADMIN_POS_RPT;
import static com.solfini.common.Constants.ERROR_LOG;
import static com.solfini.common.Constants.EXECUTIONREPORT_EQ;
import static com.solfini.common.Constants.LOG_FMT_2;
import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.LOG_FMT_6;
import static com.solfini.common.Constants.LOG_FMT_8;
import static com.solfini.common.Constants.MESSAGE_EQ;
import static com.solfini.common.Constants.POSITIONARR_EQ;
import static com.solfini.common.Constants.POSITIONREPORTENCODER_EQ;
import static com.solfini.common.Constants.POSITIONREPORTMESSAGE_EQ;
import static com.solfini.common.Constants.POSITIONSGROUPENCODER_EQ;
import static com.solfini.common.Constants.SB_EQ;
import static com.solfini.common.Constants.SENDSTATEADMIN_ENCODEDLENGTH_EQ;
import static com.solfini.common.Constants.TOTALCOUNT_EQ;
import static com.solfini.common.Constants.TX_FUNDING_RATE;
import static com.solfini.common.Constants.USER18PUBLISH2_EXECUTIONREPORT_EQ;
import static com.solfini.instrument.Position.assetIdComparator;

public class BalanceAdminDecodeTest {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BalanceAdminDecodeTest.class);
  private Instrument base;
  private Instrument quoted;
  private InstrumentPair instrumentPair;
  private SnapUtil snapUtil;

  public static final short COST_BASIS_PUBLISH_SCALE = StringUtil.toShort(PropertyReader.getProperty("COST_BASIS_PUBLISH_SCALE", "2"));
  public static final short RISK_PUBLISH_SCALE = StringUtil.toShort(PropertyReader.getProperty("RISK_PUBLISH_SCALE", "2"));
  public static final long COST_BASIS_PUBLISH_MULT = MbxMath.multiplier(COST_BASIS_PUBLISH_SCALE);
  public static final long RISK_PUBLISH_MULT = MbxMath.multiplier(RISK_PUBLISH_SCALE);

  private String topic;
  private static final int OFFSET = 19;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  private static final int USDC_ID = 1;
  private final int ME_SEQ_ID = Context.getMESeqId(); // this can be incremented with failover

  private static final short SHORT_ZERO = 0;

  private long sequenceNumber = 0; // loaded from snap now //StateLoader.getInitialOutputSequenceNumber(); // load initial and increment
  private boolean useKafka = false;

  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final PositionReportParser positionReportParser = new PositionReportParser();
  private final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();

  private User user = new User(2);

  private void initProperty() {
    LogLevel.setLevel(Level.TRACE);
    NumberFormat.getInstance().setGroupingUsed(true);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "2");
      properties.setProperty("ORDER_POOL_START_CAPACITY", "5");
      properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "2");
      properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "10");
      properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "1024");
      properties.setProperty("POSITION_POOL_START_CAPACITY", "1024");
      properties.setProperty("CHRONICLE_ENGINE_OUTPUT_DIRECTORY","./snap");
      //properties.setProperty("CHRONICLE_ENGINE_DR_INPUT_DIRECTORY=/mnt/data/chronicle/engine-output
      //properties.setProperty("CHRONICLE_PRICING_OUTPUT_DIRECTORY=/mnt/data/chronicle/pricing-output
      //properties.setProperty("CHRONICLE_API_TO_ENGINE_DIRECTORY=/mnt/data/chronicle/api-to-engine
      //properties.setProperty("CHRONICLE_API_ADMIN_TO_ENGINE_DIRECTORY=/mnt/data/chronicle/api-admin-to-engine
      //properties.setProperty("CHRONICLE_MARKET_MAKER_TO_ENGINE_DIRECTORY=/mnt/data/chronicle/market-maker-to-engine
      //properties.setProperty("CHRONICLE_ENGINE_BALANCE_DIRECTORY=/mnt/data/chronicle/engine-balance
      properties.setProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY","./snap");
      //properties.setProperty("CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY=/mnt/data/cache
      PropertyReader.initialize(null, properties);

      System.out.println("Warming up object pools");
      OrderObjectPool.init();
      ExecutionReportObjectPool.init();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Before
  public void init() {
    initProperty();

    quoted = new Instrument(0, "USD", "USD", (short) 6, (short) 6, 1, 0);
    base = new Instrument(228, "CARBON", "CARBON", (short) 6, (short) 0, 1, 0);
    instrumentPair = new InstrumentPair(229, "CARBON/USD", "CARBON/USD", base, quoted, (short) 2, (short) 0, 0, AssetType.PAIR, 0, 0, 1, 0);

    InstrumentCache.addInstrument(quoted);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addPair(instrumentPair);

    user.addPosition(0, 1000000000, null, 0, TokenType.ERC20);
    user.addPosition(1, 1000000000, null, 0, TokenType.ERC20);

    TreeSet assetIdTreeSet = new TreeSet<>(assetIdComparator);
    assetIdTreeSet.add(new long[] {101, 1, 0});
    assetIdTreeSet.add(new long[] {102, 2, 0});
    assetIdTreeSet.add(new long[] {103, 3, 0});
    assetIdTreeSet.add(new long[] {104, 4, 0});
    assetIdTreeSet.add(new long[] {105, 5, 0});

    user.addPosition(228, 1, assetIdTreeSet, 0, TokenType.ERC20);
  }

  @Test
  public void generateSnap() {
    long snapId = System.currentTimeMillis();
    final BalanceAdminMessage balanceAdminMessage = user.buildBalanceAdminMessage();
    //balanceAdminMessage.setSnapId(snapId);
    user.copySetPositionArr(balanceAdminMessage);
    //PositionReportEncoderCache.build();
    final PositionReportEncoderCache cache = PositionReportEncoderCache.get();

    byte[] encoded = publishPositionReport(balanceAdminMessage, BALANCE_ADMIN_POS_RPT, cache);

    decode(encoded);
    System.out.println("Done : " + snapId);
  }

  private void decode(final byte[] data) {
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    positionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());

    Message message = positionReportParser.parse(headerDecoder, positionReportDecoder);
    BalanceAdminMessage b = (BalanceAdminMessage) message;
    System.out.println("Done");
  }

  private final byte[] publishPositionReport(final BalanceAdminMessage balanceAdminMessage, final int posReqResult,
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

    // set balance change for settleCoin to positionReport settlePrice
    final List<Balance> balanceList = balanceAdminMessage.getBalanceList();
    if (balanceList != null) {
      for (final Balance balance : balanceList) {
        if (balance != null && balance.getAssetId() == 1) {
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
      publishAndCache(bytesWithKafkaOffset, KafkaPublisher.NORMAL_API, positionReportMessage);
      PositionReportObjectPool.returnObject(positionReportMessage);

      final SnapResponseAdminMessage end = new SnapResponseAdminMessage(balanceAdminMessage.getSnapId(), 0, 0, "ME");
      end.setSnapId(balanceAdminMessage.getSnapId());
      end.setOrderId(GlobalOrderBook.getOrderId());
      end.setExecId(GlobalOrderBook.getFilledCountGlobal());

      publish(end);
    }
    return bytesWithKafkaOffset;
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

  private void populateHeader(final MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;
    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(0); // matching engine seq num
    headerEncoder.transactionId(message.getTransactionId());
    headerEncoder.transactionEnd((short) (message.isLastMessageInTransaction() ? 1 : 0));
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

    PositionReportEncoder.PositionsGroupEncoder groupEncoder = positionReportEncoder.positionsGroupCount(positionsLength - 1); // we skip 0
    for (int i = 1; i < positionsLength; i++) {
      groupEncoder = groupEncoder.next();

      final Position position = positionArr[i];

      final Instrument instrument = (position == null) ? null : InstrumentCache.get(position.getInstrumentId());
      final InstrumentPair pair = (position == null) ? null : InstrumentCache.getPair(position.getInstrumentId());

      if (instrument != null) {
        groupEncoder.assetType(com.solfini.sbe.encoder.AssetType.ASSET);
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

        // add assetId,tokenId,groupAssetId set
        final Set<long[]> assetIdtreeSet = position.getAssetIdtreeSet();
        if (assetIdtreeSet != null && assetIdtreeSet.size() > 0) {
          PositionReportEncoder.PositionsGroupEncoder.PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(assetIdtreeSet.size());
          for (final long[] value : assetIdtreeSet) {
            System.out.println("Encoding: assetId: " + value[0]);
            assetGroupEncoder = assetGroupEncoder.next();
            assetGroupEncoder.assetId(value[0]);
            assetGroupEncoder.tokenId((int) value[1]);
            assetGroupEncoder.groupAssetId(value[2]);
          }
        } else {
          PositionReportEncoder.PositionsGroupEncoder.PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(1);
          assetGroupEncoder = assetGroupEncoder.next();
          assetGroupEncoder.assetId(0);
          assetGroupEncoder.tokenId(0);
          assetGroupEncoder.groupAssetId(0);
        }

      } else if (pair != null) {
        groupEncoder.assetType(com.solfini.sbe.encoder.AssetType.get(pair.getAssetType().value()));
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

        PositionReportEncoder.PositionsGroupEncoder.PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(1);
        assetGroupEncoder = assetGroupEncoder.next();
        assetGroupEncoder.assetId(0);
        assetGroupEncoder.tokenId(0);
        assetGroupEncoder.groupAssetId(0);
      } else {//dummy to support positionsGroupCount
        groupEncoder.instrumentId(0);
        PositionReportEncoder.PositionsGroupEncoder.PositionsAssetIdGroupEncoder assetGroupEncoder = groupEncoder.positionsAssetIdGroupCount(1);
        assetGroupEncoder = assetGroupEncoder.next();
        assetGroupEncoder.assetId(0);
        assetGroupEncoder.tokenId(0);
        assetGroupEncoder.groupAssetId(0);
      }
    }
  }

  private void publishAndCache(final byte[] bytesWithKafkaOffset, final byte messageType, final Message message) {
    //PublisherEncoderCache.blockWaitGetLock();

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
}
