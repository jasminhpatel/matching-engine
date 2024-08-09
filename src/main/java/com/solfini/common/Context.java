package com.solfini.common;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.schema.InternalMessageWrapperInboundEncoder;
import com.solfini.internal.schema.MessageHeaderEncoder;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.MatchingThread;
import com.solfini.matchengine.MessageValidator;
import com.solfini.matchengine.PersistPositionThread;
import com.solfini.matchengine.PersistThread;
import com.solfini.matchengine.PublisherThread;
import com.solfini.matchengine.TimeEventGeneratorThread;
import com.solfini.matchengine.TimeTriggerThread;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.publisher.MessagePublisher;
import com.solfini.report.check.InternalCheckReport;
import com.solfini.report.check.InternalCheckService;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

public final class Context implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(Context.class);

  private Context() {}

  private static final String INSTANCE_ID = PropertyReader.getProperty("INSTANCE_ID", "me01");
  private static final int ME_SEQ_ID = StringUtil.toInt(PropertyReader.getProperty("ME_SEQ_ID", "1")); // should increment with failover

  static {
    if (LogLevel.info()) {
      LOGGER.info("loaded INSTANCE_ID={}", INSTANCE_ID);
    }
  }

  private static final String USDC_SYMBOL = PropertyReader.getProperty("USDC_SYMBOL", "USDC");
  private static final String USDT_SYMBOL = PropertyReader.getProperty("USDT_SYMBOL", "USDT");
  private static final String BTC_SYMBOL = PropertyReader.getProperty("BTC_SYMBOL", "BTC");
  private static final String ETH_SYMBOL = PropertyReader.getProperty("ETH_SYMBOL", "ETH");
  private static final String BTC_USDC_SPOT_SYMBOL = PropertyReader.getProperty("BTC_USDC_SPOT_SYMBOL", "BTC/USDC");
  private static final String ETH_USDC_SPOT_SYMBOL = PropertyReader.getProperty("ETH_USDC_SPOT_SYMBOL", "ETH/USDC");
  private static final String BTC_USDC_FUTURE_SYMBOL = PropertyReader.getProperty("BTC_USDC_FUTURE_SYMBOL", "BTC/USDC[F]");
  private static final String ETH_USDC_FUTURE_SYMBOL = PropertyReader.getProperty("ETH_USDC_FUTURE_SYMBOL", "ETH/USDC[F]");

  private static final int STREAM_ID = PropertyReader.getProperty("STREAM_ID", 1);
  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("QUEUE_CAPACITY", 8_388_608);
  private static final int CONTROL_QUEUE_CAPACITY = PropertyReader.getProperty("CONTROL_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int RISK_TO_AUTO_LIQUIDATOR_QUEUE_CAPACITY = PropertyReader.getProperty("RISK_TO_AUTO_LIQUIDATOR_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int RISK_TO_MATCHER_QUEUE_CAPACITY = PropertyReader.getProperty("RISK_TO_MATCHER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int RECEIVER_TO_MATCHER_QUEUE_CAPACITY = PropertyReader.getProperty("RECEIVER_TO_MATCHER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int ADMIN_RECEIVER_TO_MATCHER_QUEUE_CAPACITY = PropertyReader.getProperty("ADMIN_RECEIVER_TO_MATCHER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int MATCHER_TO_PUBLISHER_QUEUE_CAPACITY = PropertyReader.getProperty("MATCHER_TO_PUBLISHER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int PUBLISHER_TO_PERSISTER_QUEUE_CAPACITY = PropertyReader.getProperty("PUBLISHER_TO_PERSISTER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int PUBLISHER_TO_PERSISTER_POSITION_QUEUE_CAPACITY = PropertyReader.getProperty("PUBLISHER_TO_PERSISTER_POSITION_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int PUBLISHER_TO_KAFKA_PUBLISHER_QUEUE_CAPACITY = PropertyReader.getProperty("PUBLISHER_TO_KAFKA_PUBLISHER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int DR_TO_MATCHER_QUEUE_CAPACITY = PropertyReader.getProperty("DR_TO_MATCHER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int MARKET_DATA_BUILDER_QUEUE_CAPACITY = PropertyReader.getProperty("MARKET_DATA_BUILDER_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final int COPY_TRADE_QUEUE_CAPACITY = PropertyReader.getProperty("COPY_TRADE_QUEUE_CAPACITY", QUEUE_CAPACITY);

  private static MarketStatus MARKET_STATUS = MarketStatus.get(Short.parseShort(PropertyReader.getProperty("MARKET_STATUS", "0")));

  private static final String CHRONICLE_ENGINE_OUTPUT_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_ENGINE_OUTPUT_DIRECTORY", "/chronicle/engine-output");
  private static final String CHRONICLE_ENGINE_DR_INPUT_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_ENGINE_DR_INPUT_DIRECTORY", "/chronicle/engine-output"); // currently the
  private static final String CHRONICLE_ENGINE_SNAP_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "/chronicle/snap");
  private static final String CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY", "");
  private static final boolean ACK_REJECT_MESSAGES = TRUE.equalsIgnoreCase(PropertyReader.getProperty("ACK_REJECT_MESSAGES", TRUE));
  private static boolean PUBLISH_MARKET_DATA = TRUE.equalsIgnoreCase(PropertyReader.getProperty("PUBLISH_MARKET_DATA", TRUE));
  private static boolean LISTEN_TO_KAFKA_MARKET_DATA =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("LISTEN_TO_KAFKA_MARKET_DATA", FALSE));
  private static boolean LISTEN_TO_IPC_MARKET_DATA = TRUE.equalsIgnoreCase(PropertyReader.getProperty("LISTEN_TO_IPC_MARKET_DATA", FALSE));
  private static boolean ENABLE_AUTO_CHANGE_LOG_LEVEL =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_AUTO_CHANGE_LOG_LEVEL", TRUE));
  private static boolean STATE_VALIDATOR_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("STATE_VALIDATOR_ENABLED", FALSE));
  private static boolean USE_ORDER_POOL_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("USE_ORDER_POOL_ENABLED", FALSE));
  private static boolean REJECT_DUP_CLORIDS_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("REJECT_DUP_CLORIDS_ENABLED", TRUE));
  private static boolean REJECT_IMMEDIATE_TRIGGERED_ENABLED =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("REJECT_IMMEDIATE_TRIGGERED_ENABLED", TRUE));
  private static boolean REPLAY_FROM_FILE_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("REPLAY_FROM_FILE_ENABLED", FALSE));
  private static String REPLAY_FROM_FILE_LOCATION = PropertyReader.getProperty("REPLAY_FROM_FILE_LOCATION", "");
  private static boolean ENABLE_SCAN_FOR_ORDER = TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_SCAN_FOR_ORDER", TRUE));
  private static boolean PERSIST_MODE_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("PERSIST_MODE_ENABLED", FALSE));
  private static boolean PERSIST_MARKET_MAKER_ORDERS = TRUE.equalsIgnoreCase(PropertyReader.getProperty("PERSIST_MARKET_MAKER_ORDERS", FALSE));

  private static boolean PRICING_THREAD_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("PRICING_THREAD_ENABLED", TRUE));
  private static boolean PERPETUALS_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("PERPETUALS_ENABLED", TRUE));
  private static boolean CONTRACT_EXPIRY_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("CONTRACT_EXPIRY_ENABLED", TRUE));
  private static boolean CROSS_COLLATERAL_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("CROSS_COLLATERAL_ENABLED", TRUE));
  private static final boolean COLLATERAL_SWAP_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("COLLATERAL_SWAP_ENABLED", TRUE));
  private static final boolean USE_SPOT_MARKET_INDEX_PRICE =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("USE_SPOT_MARKET_INDEX_PRICE", TRUE));
  private static final int SPOT_MARKET_INDEX_TWAP_SECONDS = PropertyReader.getProperty("SPOT_MARKET_INDEX_TWAP_SECONDS", 3);
  private static final double SPOT_MARKET_INDEX_TWAP_THRESHOLD = PropertyReader.getProperty("SPOT_MARKET_INDEX_TWAP_THRESHOLD", 0.002d);

  private static boolean USER_STATS_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("USER_STATS_ENABLED", FALSE));
  private static int USER_STATS_INTERVAL = PropertyReader.getProperty("USER_STATS_INTERVAL", 3600);

  private static final boolean DEBUG_LOG_RISK =
      LOGGER.isDebugEnabled() && TRUE.equalsIgnoreCase(PropertyReader.getProperty("DEBUG_LOG_RISK", FALSE));
  private static final boolean ENABLE_HISTO_REPORT = TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_HISTO_REPORT", FALSE));
  private static final boolean ENABLE_CIRCUIT_BREAKER = TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_CIRCUIT_BREAKER", TRUE));

  private static final boolean ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS", TRUE));
  private static final boolean ENABLE_BALANCE_WITHDRAW_LIMITS =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_BALANCE_WITHDRAW_LIMITS", FALSE));

  private static final boolean LIQUIDATION_ORDER_EXTERNAL_ROUTING =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("LIQUIDATION_ORDER_EXTERNAL_ROUTING", FALSE));

  private static final int INITIAL_USER_CACHE_SIZE = PropertyReader.getProperty("INITIAL_USER_CACHE_SIZE", 1_048_576);
  private static final int INITIAL_ORDER_BOOK_SIZE = PropertyReader.getProperty("INITIAL_ORDER_BOOK_SIZE", 16_777_216);
  private static final int USER_OPEN_ORDER_LIMIT = PropertyReader.getProperty("USER_OPEN_ORDER_LIMIT", 0); // 0 is no limit

  private static int DISCOUNT_FEES_INSTRUMENT_ID = PropertyReader.getProperty("DISCOUNT_FEES_INSTRUMENT_ID", 0);
  private static int DISCOUNT_FEES_COIN_BASIS_POINTS = PropertyReader.getProperty("DISCOUNT_FEES_COIN_BASIS_POINTS", 0);
  private static int NUM_ENCODER_THREADS = PropertyReader.getProperty("NUM_ENCODER_THREADS", 8);
  private static int NUM_DECODER_THREADS = PropertyReader.getProperty("NUM_DECODER_THREADS", 8);
  private static Mode CONTROLLER_MODE = Mode.valueOf(PropertyReader.getProperty("CONTROLLER_MODE", "none").trim().toUpperCase());
  private static final double MARGIN_LIQUIDATION_SATISFIED_THRESHOLD =
      PropertyReader.getProperty("MARGIN_LIQUIDATION_SATISFIED_THRESHOLD", .95d);


  private static final double AUCTION_HIGH_BOUND = PropertyReader.getProperty("AUCTION_HIGH_BOUND", 1.02d);
  private static final double AUCTION_LOW_BOUND = PropertyReader.getProperty("AUCTION_LOW_BOUND", 0.98d);
  private static final long AUCTION_RECALC_TIME_INTERVAL = PropertyReader.getProperty("AUCTION_RECALC_TIME_INTERVAL", 10_000);
  private static final boolean AUCTIONS_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("AUCTIONS_ENABLED", TRUE));
  private static final int AUCTION_FIX_MAX_ATTEMPTS = PropertyReader.getProperty("AUCTION_FIX_MAX_ATTEMPTS", 6);

  private static final long CIRCUIT_BREAKER_TIME_INTERVAL = PropertyReader.getProperty("CIRCUIT_BREAKER_TIME_INTERVAL", 300_000); // 5 mins
  private static final String ASSET_GROUPS_COMPACTION_TOPIC = PropertyReader.getProperty("ASSET_GROUPS_COMPACTION_TOPIC", null);

  // same
  private static final String CHRONICLE_PRICING_OUTPUT_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_PRICING_OUTPUT_DIRECTORY", "/chronicle/pricing-output");
  private static final String CHRONICLE_API_TO_ENGINE_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_API_TO_ENGINE_DIRECTORY", "/chronicle/api-to-engine");
  private static final String CHRONICLE_API_ADMIN_TO_ENGINE_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_API_ADMIN_TO_ENGINE_DIRECTORY", "/chronicle/api-admin-to-engine");
  private static final String CHRONICLE_MARKET_MAKER_TO_ENGINE_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_MARKET_MAKER_TO_ENGINE_DIRECTORY", "/chronicle/market-maker-to-engine");
  private static final String CHRONICLE_ENGINE_BALANCE_DIRECTORY =
      PropertyReader.getProperty("CHRONICLE_ENGINE_BALANCE_DIRECTORY", "/chronicle/engine-balance");

  private static final String INBOUND_THREAD_IDLE = PropertyReader.getProperty("INBOUND_THREAD_IDLE", NO_OP_IDLE_STATEGY);
  private static final String MATCHING_THREAD_IDLE = PropertyReader.getProperty("MATCHING_THREAD_IDLE", NO_OP_IDLE_STATEGY);
  private static final String PUBLISHER_THREAD_IDLE = PropertyReader.getProperty("PUBLISHER_THREAD_IDLE", NO_OP_IDLE_STATEGY);
  private static final String RISK_THREAD_IDLE = PropertyReader.getProperty("RISK_THREAD_IDLE", NO_OP_IDLE_STATEGY);

  private static final int INACTIVE_MARKET_DATA_PUBLISH_TIME = PropertyReader.getProperty("INACTIVE_MARKET_DATA_PUBLISH_TIME", 300_000);
  private static final String ENVIRONMENT = PropertyReader.getProperty("ENVIRONMENT", "PRODUCTION");
  private static boolean PUBLISH_HEALTH_REPORT_MAIL = TRUE.equalsIgnoreCase(PropertyReader.getProperty("PUBLISH_HEALTH_REPORT_MAIL", TRUE));
  private static final int MARKET_MAKER_USERID = PropertyReader.getProperty("MARKET_MAKER_USERID", 0);
  private static final int ROUTER_THREAD_POOL_CORE_SIZE = PropertyReader.getProperty("ROUTER_THREAD_POOL_CORE_SIZE", 1);
  private static final boolean COPY_TRADE_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("COPY_TRADE_ENABLED", FALSE));
  private static final boolean COPY_TRADE_ONLY = TRUE.equalsIgnoreCase(PropertyReader.getProperty("COPY_TRADE_ONLY", FALSE));
  private static final String COPY_TRADE_PROXY_IPS = PropertyReader.getProperty("COPY_TRADE_PROXY_IPS", null);
  private static final String COIN_MARKET_CAP_API_KEY = PropertyReader.getProperty("COIN_MARKET_CAP_API_KEY", "5d89f95b-4f21-4909-8b97-746fb1892fc3");
  private static final int MAX_DELAY_TO_OPEN_ORDER_IN_MS = PropertyReader.getProperty("MAX_DELAY_TO_OPEN_ORDER_IN_MS", FIVE_MINUTE);
  private static final int MAX_DELAY_TO_CLOSE_ORDER_IN_MS = PropertyReader.getProperty("MAX_DELAY_TO_CLOSE_ORDER_IN_MS", ONE_HOUR);
  private static final double MIN_COPY_TRADE_AMOUNT_IN_USD = PropertyReader.getProperty("MIN_COPY_TRADE_AMOUNT_IN_USD", 10D);
  private static final double COPY_TRADE_STABLE_COIN_CONVERSION_SAFE_FACTOR = PropertyReader.getProperty("MIN_COPY_TRADE_AMOUNT_IN_USD", 1.01D);// = 1% => 101%

  private static final String COPY_TRADE_USER_PARTITION_IDS = PropertyReader.getProperty("COPY_TRADE_USER_PARTITION_IDS", "0,1,2");//all 3 partitions
  private static final int NO_OF_TOTAL_COPY_TRADE_USER_PARTITIONS = PropertyReader.getProperty("NO_OF_TOTAL_COPY_TRADE_USER_PARTITIONS", 3);
  private static final boolean REJECT_OUT_OF_BOUND_ORDERS = TRUE.equalsIgnoreCase(PropertyReader.getProperty("REJECT_OUT_OF_BOUND_ORDERS", FALSE));

  private static final OneToOneConcurrentArrayQueueCustom<Message> controlQueue =
      new OneToOneConcurrentArrayQueueCustom<>(CONTROL_QUEUE_CAPACITY, "controlQueue");
  private static final ManyToManyConcurrentArrayQueueCustom<User> riskToAutoLiquidatorQueue =
      new ManyToManyConcurrentArrayQueueCustom<>(RISK_TO_AUTO_LIQUIDATOR_QUEUE_CAPACITY, "riskToAutoLiquidatorQueue");
  private static final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue =
      new ManyToOneConcurrentArrayQueueCustom<>(RISK_TO_MATCHER_QUEUE_CAPACITY, "riskToMatcherQueue");
  private static final TransactionalInputManyToOneConcurrentArrayQueue receiverToMatcherQueue =
      new TransactionalInputManyToOneConcurrentArrayQueue(RECEIVER_TO_MATCHER_QUEUE_CAPACITY, "receiverToMatcherQueue");
  private static final OneToOneConcurrentArrayQueueCustom<Message> adminReceiverToMatcherQueue =
      new OneToOneConcurrentArrayQueueCustom<>(ADMIN_RECEIVER_TO_MATCHER_QUEUE_CAPACITY, "adminReceiverToMatcherQueue");
  private static final TransactionalOutputManyToOneConcurrentArrayQueue matcherToPublisherQueue =
      new TransactionalOutputManyToOneConcurrentArrayQueue(MATCHER_TO_PUBLISHER_QUEUE_CAPACITY, "matcherToPublisherQueue");
  private static final ManyToOneConcurrentArrayQueueCustom<Message> publisherToPersisterQueue =
      new ManyToOneConcurrentArrayQueueCustom<>(PUBLISHER_TO_PERSISTER_QUEUE_CAPACITY, "publisherToPersisterQueue");
  private static final ManyToOneConcurrentArrayQueueCustom<PositionReportMessage> publisherToPersisterPositionQueue =
      new ManyToOneConcurrentArrayQueueCustom<>(PUBLISHER_TO_PERSISTER_POSITION_QUEUE_CAPACITY, "publisherToPersisterPositionQueue");
  private static final OneToOneConcurrentArrayQueueCustom<byte[]> publisherToKafkaPublisherQueue =
      new OneToOneConcurrentArrayQueueCustom<>(PUBLISHER_TO_KAFKA_PUBLISHER_QUEUE_CAPACITY, "publisherToKafkaPublisherQueue");
  private static final OneToOneConcurrentArrayQueueCustom<Message> drToMatcherQueue =
      new OneToOneConcurrentArrayQueueCustom<>(DR_TO_MATCHER_QUEUE_CAPACITY, "drToMatcherQueue");
  private static final ManyToOneConcurrentArrayQueueCustom<InstrumentPair> marketDataBuilderQueue =
      new ManyToOneConcurrentArrayQueueCustom<>(MARKET_DATA_BUILDER_QUEUE_CAPACITY, "marketDataBuilderQueue");
  private static final ManyToManyConcurrentArrayQueueCustom<Message> copyTradeQueue =
      new ManyToManyConcurrentArrayQueueCustom<>(COPY_TRADE_ENABLED ? COPY_TRADE_QUEUE_CAPACITY : 2, "copyTradeQueue");

  private static final LoggingThread LOGGING_THREAD = new LoggingThread(IdleStrategyFactory.create(MATCHING_THREAD_IDLE));
  private static final MatchingThread MATCHING_THREAD = new MatchingThread(IdleStrategyFactory.create(MATCHING_THREAD_IDLE));
  private static final PublisherThread PUBLISHER_THREAD = new PublisherThread(IdleStrategyFactory.create(PUBLISHER_THREAD_IDLE));

  private static final TimeTriggerThread timeTriggerThread = new TimeTriggerThread(IdleStrategyFactory.create(RISK_THREAD_IDLE));
  private static final TimeEventGeneratorThread timeEventGeneratorThread =
      new TimeEventGeneratorThread(IdleStrategyFactory.create(RISK_THREAD_IDLE));

  private static KafkaPublisher kafkaPublisher = null;
  private static int loadStrategy;

  private static MessageValidator messageValidator = null;

  public static final int getLoadStrategy() {
    return loadStrategy;
  }

  public static final void setLoadStrategy(final int value) {
    loadStrategy = value;
  }

  private static final MessagePublisher outboundMessagePublisher = new MessagePublisher();

  private static final InternalMessageWrapperInboundEncoder outboundInternalMessageEncoder = new InternalMessageWrapperInboundEncoder();
  private static final com.solfini.internal.schema.MessageHeaderEncoder outboundHeaderEncoder = new MessageHeaderEncoder();
  private static final com.solfini.internal.admin.schema.MessageHeaderEncoder outboundAdminHeaderEncoder =
      new com.solfini.internal.admin.schema.MessageHeaderEncoder();
  private static final UnsafeBuffer outboundInternalMessageUnsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(32768));

  private static PersistThread persistThread = null;
  private static PersistPositionThread persistPositionThread = null;

  public static void setPersistThread(final PersistThread thread) {
    persistThread = thread;
  }

  public static PersistThread getPersistThread() {
    return persistThread;
  }

  public static void setPersistPositionThread(final PersistPositionThread thread) {
    persistPositionThread = thread;
  }

  public static PersistPositionThread getPersistPositionThread() {
    return persistPositionThread;
  }

  public static final void setKafkaPublisher(final KafkaPublisher value) {
    kafkaPublisher = value;
  }

  public static final KafkaPublisher getKafkaPublisher() {
    if (null == kafkaPublisher) {
      try {
        long seqNum = 0;
        kafkaPublisher = new KafkaPublisher(seqNum);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    return kafkaPublisher;
  }

  public static final InternalCheckService internalCheckService = new InternalCheckService();

  public static final InternalCheckReport statusCheck(long mark) {
    try {
      LOGGER.warn("DO STATUS CHECK...");
      return internalCheckService.internalCheck(mark);
    } catch (Exception e) {
      LOGGER.error("internal check failed with exception", e);
      return null;
    }
  }

  public static final int getMESeqId() {
    return ME_SEQ_ID;
  }

  public static final String getInstanceId() {
    return INSTANCE_ID;
  }

  public static final int getStreamId() {
    return STREAM_ID;
  }

  public static final int getQueueCapacity() {
    return QUEUE_CAPACITY;
  }

  public static final String getChronicleEngineOutputQueueDirectory() {
    return CHRONICLE_ENGINE_OUTPUT_DIRECTORY;
  }

  public static final String getChronicleEngineSnapQueueDirectory() {
    return CHRONICLE_ENGINE_SNAP_DIRECTORY;
  }

  public static final String getChronicleEngineSnapCacheDirectory() {
    return CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY;
  }

  public static final String getChronicleDRInputQueueDirectory() {
    return CHRONICLE_ENGINE_DR_INPUT_DIRECTORY; // DR input queue is the same as the primary engine output
  }

  public static final String getChronicleEngineBalanceQueueDirectory() {
    return CHRONICLE_ENGINE_BALANCE_DIRECTORY;
  }

  public static final String getChroniclePricingOutputQueueDirectory() {
    return CHRONICLE_PRICING_OUTPUT_DIRECTORY;
  }

  public static final String getChronicleApiToEngineQueueDirectory() {
    return CHRONICLE_API_TO_ENGINE_DIRECTORY;
  }

  public static final String getChronicleApiAdminToEngineQueueDirectory() {
    return CHRONICLE_API_ADMIN_TO_ENGINE_DIRECTORY;
  }

  public static final String getChronicleMarketMakerToEngineQueueDirectory() {
    return CHRONICLE_MARKET_MAKER_TO_ENGINE_DIRECTORY;
  }

  public static final String getInboundThreadIdle() {
    return INBOUND_THREAD_IDLE;
  }

  public static final String getMatchingThreadIdle() {
    return MATCHING_THREAD_IDLE;
  }

  public static final String getRiskThreadIdle() {
    return RISK_THREAD_IDLE;
  }

  public static final String getPublisherThreadIdle() {
    return PUBLISHER_THREAD_IDLE;
  }

  public static final LoggingThread getLoggingThread() {
    return LOGGING_THREAD;
  }

  public static final MatchingThread getMatchingThread() {
    return MATCHING_THREAD;
  }

  public static final PublisherThread getPublisherThread() {
    return PUBLISHER_THREAD;
  }

  public static final TransactionalInputManyToOneConcurrentArrayQueue getReceiverToMatcherQueue() {
    return receiverToMatcherQueue;
  }

  public static final OneToOneConcurrentArrayQueueCustom<Message> getControlQueue() {
    return controlQueue;
  }

  public static final OneToOneConcurrentArrayQueueCustom<Message> getAdminReceiverToMatcherQueue() {
    return adminReceiverToMatcherQueue;
  }

  public static final ManyToManyConcurrentArrayQueueCustom<User> getRiskToAutoLiquidatorQueue() {
    return riskToAutoLiquidatorQueue;
  }

  public static final ManyToOneConcurrentArrayQueueCustom<Message> getRiskToMatcherQueue() {
    return riskToMatcherQueue;
  }

  public static final OneToOneConcurrentArrayQueueCustom<byte[]> getPublisherToKafkaPublisherQueue() {
    return publisherToKafkaPublisherQueue;
  }

  public static final OneToOneConcurrentArrayQueueCustom<Message> getDRToMatcherQueue() {
    return drToMatcherQueue;
  }

  public static final ManyToOneConcurrentArrayQueueCustom<InstrumentPair> getMarketDataBuilderQueue() {
    return marketDataBuilderQueue;
  }

  public static final TransactionalOutputManyToOneConcurrentArrayQueue getMatcherToPublisherQueue() {
    return matcherToPublisherQueue;
  }

  public static final ManyToOneConcurrentArrayQueueCustom<Message> getPublisherToPersisterQueue() {
    return publisherToPersisterQueue;
  }

  public static final ManyToOneConcurrentArrayQueueCustom<PositionReportMessage> getPublisherToPersisterPositionQueue() {
    return publisherToPersisterPositionQueue;
  }

  public static final MessagePublisher getMessagePublisher() {
    return outboundMessagePublisher;
  }

  public static final InternalMessageWrapperInboundEncoder getOutboundInternalMessageEncoder() {
    return outboundInternalMessageEncoder;
  }

  public static final com.solfini.internal.schema.MessageHeaderEncoder getOutboundHeaderEncoder() {
    return outboundHeaderEncoder;
  }

  public static final com.solfini.internal.admin.schema.MessageHeaderEncoder getOutboundAdminHeaderEncoder() {
    return outboundAdminHeaderEncoder;
  }

  public static final UnsafeBuffer getOutboundInternalMessageUnsafeBuffer() {
    return outboundInternalMessageUnsafeBuffer;
  }

  public static final MarketStatus getMarketStatus() {
    return MARKET_STATUS;
  }

  public static final boolean isAckRejectMessages() {
    return ACK_REJECT_MESSAGES;
  }

  public static final boolean isAuctionsEnabled() {
    return AUCTIONS_ENABLED;
  }

  public static final boolean isPublishMarketData() {
    return PUBLISH_MARKET_DATA;
  }

  public static final void setPublishMarketData(final boolean value) {
    PUBLISH_MARKET_DATA = value;
  }

  public static final boolean isPublishHealthReportMail() {
    return PUBLISH_HEALTH_REPORT_MAIL;
  }

  public static final void setPublishHealthReportMail(final boolean value) {
    PUBLISH_HEALTH_REPORT_MAIL = value;
  }

  public static final boolean isListenToKafkaMarketData() {
    return LISTEN_TO_KAFKA_MARKET_DATA;
  }

  public static final void setListenToKafkaMarketData(final boolean value) {
    LISTEN_TO_KAFKA_MARKET_DATA = value;
  }

  public static final boolean isListenToIpcMarketData() {
    return LISTEN_TO_IPC_MARKET_DATA;
  }

  public static final void setListenToIpcMarketData(final boolean value) {
    LISTEN_TO_IPC_MARKET_DATA = value;
  }

  public static final boolean isEnableAutoChangeLogLevel() {
    return ENABLE_AUTO_CHANGE_LOG_LEVEL;
  }

  public static final void setEnableAutoChangeLogLevel(final boolean value) {
    ENABLE_AUTO_CHANGE_LOG_LEVEL = value;
  }

  public static final boolean isUseOrderPoolEnabled() {
    return USE_ORDER_POOL_ENABLED;
  }

  public static final void isUseOrderPoolEnabled(final boolean value) {
    USE_ORDER_POOL_ENABLED = value;
  }

  public static final boolean isRejectDuplicateClorIdsEnabled() {
    return REJECT_DUP_CLORIDS_ENABLED;
  }

  public static final void setRejectDuplicateClorIdsEnabled(final boolean value) {
    REJECT_DUP_CLORIDS_ENABLED = value;
  }

  public static final boolean isRejectImmediateTriggeredEnabled() {
    return REJECT_IMMEDIATE_TRIGGERED_ENABLED;
  }

  public static final void setRejectImmediateTriggeredEnabled(final boolean value) {
    REJECT_IMMEDIATE_TRIGGERED_ENABLED = value;
  }

  public static final boolean isReplayFromFileEnabled() {
    return REPLAY_FROM_FILE_ENABLED;
  }

  public static final void setReplayFromFileEnabled(final boolean value) {
    REPLAY_FROM_FILE_ENABLED = value;
  }

  public static final boolean isScanForOrderEnabled() {
    return ENABLE_SCAN_FOR_ORDER;
  }

  public static final void setScanForOrderEnabled(final boolean value) {
    ENABLE_SCAN_FOR_ORDER = value;
  }

  public static final boolean isPersistModeEnabled() {
    return PERSIST_MODE_ENABLED;
  }

  public static boolean isPersistMarketMakerOrders() {
    return PERSIST_MARKET_MAKER_ORDERS;
  }

  public static final void setPersistModeEnabled(final boolean value) {
    PERSIST_MODE_ENABLED = value;
  }

  public static final boolean isPerpetualsEnabled() {
    return PERPETUALS_ENABLED;
  }

  public static final void setCrossCollateralEnabled(final boolean enabled) {
    CROSS_COLLATERAL_ENABLED = enabled;
  }

  public static final boolean isCrossCollateralEnabled() {
    return CROSS_COLLATERAL_ENABLED;
  }

  public static final boolean isContractExpiryEnabled() {
    return CONTRACT_EXPIRY_ENABLED;
  }

  public static final boolean isPricingThreadEnabled() {
    return PRICING_THREAD_ENABLED;
  }

  public static final void setPricingThreadEnabled(final boolean value) {
    PRICING_THREAD_ENABLED = value;
  }

  public static final boolean isUserStatsEnabled() {
    return USER_STATS_ENABLED;
  }

  public static final int getUserStatsInterval() {
    return USER_STATS_INTERVAL;
  }

  public static final int getSpotMarketIndexTwapSeconds() {
    return SPOT_MARKET_INDEX_TWAP_SECONDS;
  }

  public static final double getSpotMarketIndexThreshold() {
    return SPOT_MARKET_INDEX_TWAP_THRESHOLD;
  }

  public static final String getReplayFromFileLocation() {
    return REPLAY_FROM_FILE_LOCATION;
  }

  public static final void setReplayFromFileLocation(final String value) {
    REPLAY_FROM_FILE_LOCATION = value;
  }

  public static final boolean isStateValidatorEnabled() {
    return STATE_VALIDATOR_ENABLED;
  }

  public static final void setStateValidatorEnabled(final boolean value) {
    STATE_VALIDATOR_ENABLED = value;
  }

  public static final boolean isCollateralSwapEnabled() {
    return COLLATERAL_SWAP_ENABLED;
  }

  public static final boolean isUseSpotMarketIndexPriceEnabled() {
    return USE_SPOT_MARKET_INDEX_PRICE;
  }

  public static final boolean isDebugLogRisk() {
    return DEBUG_LOG_RISK;
  }

  public static final boolean isEnableHistoReport() {
    return ENABLE_HISTO_REPORT;
  }

  public static final boolean isEnableCircuitBreaker() {
    return ENABLE_CIRCUIT_BREAKER;
  }

  public static final boolean isEnableBalanceWithdrawExactLimits() {
    return ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS;
  }

  public static final boolean isEnableBalanceWithdrawLimits() {
    return ENABLE_BALANCE_WITHDRAW_LIMITS;
  }

  public static final int getInitialUserCacheSize() {
    return INITIAL_USER_CACHE_SIZE;
  }

  public static final int getInitialOrderBookSize() {
    return INITIAL_ORDER_BOOK_SIZE;
  }

  public static final void setDiscountFeesInstrumentId(final int value) {
    DISCOUNT_FEES_INSTRUMENT_ID = value;
  }

  public static final int getDiscountFeesInstrumentId() {
    return DISCOUNT_FEES_INSTRUMENT_ID;
  }

  public static final void setDiscountFeesCoinBasisPoints(final int value) {
    DISCOUNT_FEES_COIN_BASIS_POINTS = value;
  }

  public static final int getDiscountFeesCoinBasisPoints() {
    return DISCOUNT_FEES_COIN_BASIS_POINTS;
  }

  public static final void setDecoderThreads(final int value) {
    NUM_DECODER_THREADS = value;
  }

  public static final int getDecoderThreads() {
    return NUM_DECODER_THREADS;
  }

  public static final void setEncoderThreads(final int value) {
    NUM_ENCODER_THREADS = value;
  }

  public static final int getEncoderThreads() {
    return NUM_ENCODER_THREADS;
  }

  public static final void setMarketStatus(final MarketStatus marketStatus) {
    MARKET_STATUS = marketStatus;
  }

  public static final TimeTriggerThread getTimeTriggerThread() {
    return timeTriggerThread;
  }

  public static final TimeEventGeneratorThread getTimeEventGeneratorThread() {
    return timeEventGeneratorThread;
  }

  public static final Mode getControllerMode() {
    return CONTROLLER_MODE;
  }

  public static final void setControllerMode(final Mode mode) {
    CONTROLLER_MODE = mode;
  }

  public static final MessageValidator getMessageValidator() {
    return messageValidator;
  }

  public static void setMessageValidator(MessageValidator instance) {
    messageValidator = instance;
  }

  public static final String getUsdtSymbol() {
    return USDT_SYMBOL;
  }

  public static final String getUsdcSymbol() {
    return USDC_SYMBOL;
  }

  public static final String getBtcSymbol() {
    return BTC_SYMBOL;
  }

  public static final String getEthSymbol() {
    return ETH_SYMBOL;
  }

  public static final String getBtcUsdcSpotSymbol() {
    return BTC_USDC_SPOT_SYMBOL;
  }

  public static final String getEthUsdcSpotSymbol() {
    return ETH_USDC_SPOT_SYMBOL;
  }

  public static final String getBtcUsdcFutureSymbol() {
    return BTC_USDC_FUTURE_SYMBOL;
  }

  public static final String getEthUsdcFutureSymbol() {
    return ETH_USDC_FUTURE_SYMBOL;
  }

  public static final int getUserOpenOrderLimit() {
    return USER_OPEN_ORDER_LIMIT;
  }

  public static final double getMarginLiquidationSatisfiedThreshold() {
    return MARGIN_LIQUIDATION_SATISFIED_THRESHOLD;
  }

  public static final boolean isLiquidationOrderExternalRouting() {
    return LIQUIDATION_ORDER_EXTERNAL_ROUTING;
  }

  public static final double getAuctionHighBound() {
    return AUCTION_HIGH_BOUND;
  }

  public static final double getAuctionLowBound() {
    return AUCTION_LOW_BOUND;
  }

  public static final long getAuctionRecalcTimeInterval() {
    return AUCTION_RECALC_TIME_INTERVAL;
  }

  public static final int getAuctionFixMaxAttempts() {
    return AUCTION_FIX_MAX_ATTEMPTS;
  }

  public static final long getCircuitBreakerTimeInterval() {
    return CIRCUIT_BREAKER_TIME_INTERVAL;
  }

  public static int getMarketMakerUserid() {
    return MARKET_MAKER_USERID;
  }

  public static String getAssetGroupsCompactionTopic() {
    return ASSET_GROUPS_COMPACTION_TOPIC;
  }

  public static int getInactiveMarketDataPublishTime() {
    return INACTIVE_MARKET_DATA_PUBLISH_TIME;
  }

  public static String getEnvironment() {
    return ENVIRONMENT;
  }

  public static int getRouterThreadPoolCoreSize() {
    return ROUTER_THREAD_POOL_CORE_SIZE;
  }

  public static ManyToManyConcurrentArrayQueueCustom<Message> getCopyTradeQueue() {
    return copyTradeQueue;
  }

  public static boolean isCopyTradeEnabled() {
    return COPY_TRADE_ENABLED;
  }

  public static boolean isCopyTradeOnly() {
    return COPY_TRADE_ONLY;
  }

  public static String getCopyTradeProxyIps() {
    return COPY_TRADE_PROXY_IPS;
  }

  public static String getCoinMarketCapApiKey() {
    return COIN_MARKET_CAP_API_KEY;
  }

  public static int getMaxDelayToOpenOrderInMs() {
    return MAX_DELAY_TO_OPEN_ORDER_IN_MS;
  }

  public static int getMaxDelayToCloseOrderInMs() {
    return MAX_DELAY_TO_CLOSE_ORDER_IN_MS;
  }

  public static double getMinCopyTradeAmountInUsd() {
    return MIN_COPY_TRADE_AMOUNT_IN_USD;
  }

  public static int getNoOfTotalCopyTradeUserPartitions() {
    return NO_OF_TOTAL_COPY_TRADE_USER_PARTITIONS;
  }

  public static String getCopyTradeUserPartitionIds() {
    return COPY_TRADE_USER_PARTITION_IDS;
  }

  public static boolean isRejectOutOfBoundOrders() {
    return REJECT_OUT_OF_BOUND_ORDERS;
  }

  public static double getCopyTradeStableCoinConversionSafeFactor() {
    return COPY_TRADE_STABLE_COIN_CONVERSION_SAFE_FACTOR;
  }
}
