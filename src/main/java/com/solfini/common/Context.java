package com.solfini.common;

import com.solfini.util.EncryptDecrypt2;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import org.agrona.collections.IntHashSet;
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
import org.web3j.abi.datatypes.Address;

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
  private static final int LIQUIDITY_DEX_QUEUE_CAPACITY = PropertyReader.getProperty("LIQUIDITY_DEX_QUEUE_CAPACITY", QUEUE_CAPACITY);

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
  private static final boolean STAKING_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("STAKING_ENABLED", TRUE));
  private static final boolean USE_SPOT_MARKET_INDEX_PRICE =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("USE_SPOT_MARKET_INDEX_PRICE", TRUE));
  private static final int SPOT_MARKET_INDEX_TWAP_SECONDS = PropertyReader.getProperty("SPOT_MARKET_INDEX_TWAP_SECONDS", 3);
  private static final double SPOT_MARKET_INDEX_TWAP_THRESHOLD = PropertyReader.getProperty("SPOT_MARKET_INDEX_TWAP_THRESHOLD", 0.002d);

  private final static boolean USER_STATS_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("USER_STATS_ENABLED", FALSE));
  private final static int USER_STATS_INTERVAL = PropertyReader.getProperty("USER_STATS_INTERVAL", 3600);
  private final static double MIN_ORDER_VALUE = PropertyReader.getProperty("MIN_TOP_THIRTY_ORDER_VALUE", 15.00d);
  private final static double MIN_TOP_THIRTY_ORDER_VALUE = PropertyReader.getProperty("MIN_TOP_N_ORDER_VALUE", 200.00d);
  private final static double LIQUIDITY_DEX_SMALL_ORDER_VALUE_THRESHOLD = PropertyReader.getProperty("LIQUIDITY_DEX_SMALL_ORDER_VALUE_THRESHOLD", 100.00d);

  private static final boolean DEBUG_LOG_RISK =
      LOGGER.isDebugEnabled() && TRUE.equalsIgnoreCase(PropertyReader.getProperty("DEBUG_LOG_RISK", FALSE));
  private static final boolean ENABLE_HISTO_REPORT = TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_HISTO_REPORT", FALSE));
  private static final boolean ENABLE_CIRCUIT_BREAKER = TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_CIRCUIT_BREAKER", TRUE));

  private static final boolean ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS", TRUE));
  private static final boolean ENABLE_BALANCE_WITHDRAW_LIMITS =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_BALANCE_WITHDRAW_LIMITS", FALSE));
  private static final boolean ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS", TRUE));

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
  private static final int LIQUIDITY_INSTRUMENT_PAIR_ID = PropertyReader.getProperty("LIQUIDITY_INSTRUMENT_PAIR_ID", 0);

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
  private static final String COPY_TRADE_THREAD_IDLE = PropertyReader.getProperty("COPY_TRADE_THREAD_IDLE", YIELDING_IDLE_STRATEGY);
  private static final String LIQUIDITY_DEX_THREAD_IDLE = PropertyReader.getProperty("LIQUIDITY_DEX_THREAD_IDLE", YIELDING_IDLE_STRATEGY);

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
  private static final int TOP_BOTTOM_RELOAD_HOUR_IN_CEST = PropertyReader.getProperty("TOP_BOTTOM_RELOAD_HOUR_IN_CEST", 7); // 7 am CEST => 1am EST
  private static final int LIQUIDITY_DEPTH_LEVELS = PropertyReader.getProperty("LIQUIDITY_DEPTH_LEVELS", 10);
  private static final boolean BLOCKCHAIN_POSITION_MANAGER_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("BLOCKCHAIN_POSITION_MANAGER_ENABLED", FALSE));
  private static final String[] LIQUIDITY_EXCHANGE_PREFERENCE =
      PropertyReader.getProperty("LIQUIDITY_EXCHANGE_PREFERENCE", "bybit,binance,mexc,deribit").split(",");
  private static final double PROMO_DEPOSIT_THRESHOLD = PropertyReader.getProperty("PROMO_DEPOSIT_THRESHOLD", 50.00D);
  private static final double PROMO_DEPOSIT_VALUE = PropertyReader.getProperty("PROMO_DEPOSIT_VALUE", 5.00D);
  private static final long EXTERNAL_EXCHANGE_BALANCE_CACHE_DURATION_MS = PropertyReader.getProperty("EXTERNAL_EXCHANGE_BALANCE_CACHE_DURATION_MS", ELEVEN_MINUTES);
  private static final String BINANCE_EXCHANGE_BASE_URL = PropertyReader.getProperty("BINANCE_EXCHANGE_BASE_URL", "https://testnet.binance.vision");
  private static final String BINANCE_FUTURES_EXCHANGE_BASE_URL = PropertyReader.getProperty("BINANCE_FUTURES_EXCHANGE_BASE_URL", "https://testnet.binancefuture.com");
  private static final String BYBIT_EXCHANGE_BASE_URL = PropertyReader.getProperty("BYBIT_EXCHANGE_BASE_URL", "https://api.bybit.com");
  private static final String BYBIT_EXCHANGE_RECV_WINDOW = PropertyReader.getProperty("BYBIT_EXCHANGE_RECV_WINDOW", "5000");
  private static final String MEXC_EXCHANGE_BASE_URL = PropertyReader.getProperty("MEXC_EXCHANGE_BASE_URL", "https://api.mexc.com");
  private static final String BITGET_EXCHANGE_BASE_URL = PropertyReader.getProperty("BITGET_EXCHANGE_BASE_URL", "https://api.bitget.com");
  private static final boolean BITGET_EXCHANGE_DEMO_TRADING_ENABLE = TRUE.equalsIgnoreCase(PropertyReader.getProperty("BITGET_EXCHANGE_DEMO_TRADING_ENABLE", FALSE));
  private static final BigInteger POLYGON_MAX_PRIORITY_GAS_PRICE =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("POLYGON_MAX_PRIORITY_GAS_PRICE", "100000000000")));// 100 Gwei
  private static final BigInteger POLYGON_MIN_PRIORITY_GAS_PRICE =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("POLYGON_MIN_PRIORITY_GAS_PRICE", "1000000000")));// 1 Gwei
  private static final BigInteger POLYGON_MAX_FEE_PER_GAS =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("POLYGON_MAX_FEE_PER_GAS", "80000000000")));// 80 Gwei
  private static final BigInteger ETHEREUM_MAX_PRIORITY_GAS_PRICE =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("ETHEREUM_MAX_PRIORITY_GAS_PRICE", "200000000")));// 0.2 Gwei
  private static final BigInteger ETHEREUM_MIN_PRIORITY_GAS_PRICE =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("ETHEREUM_MIN_PRIORITY_GAS_PRICE", "2000000000")));// 2 Gwei
  private static final BigInteger ETHEREUM_MAX_FEE_PER_GAS =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("ETHEREUM_MAX_FEE_PER_GAS", "2000000000")));// 2 Gwei
  private static final BigInteger XDC_MAX_PRIORITY_GAS_PRICE =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("XDC_MAX_PRIORITY_GAS_PRICE", "100000000000")));// 100 Gwei
  private static final BigInteger XDC_MIN_PRIORITY_GAS_PRICE =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("XDC_MIN_PRIORITY_GAS_PRICE", "1000000000")));// 1 Gwei
  private static final BigInteger XDC_MAX_FEE_PER_GAS =
      BigInteger.valueOf(StringUtil.toLong(PropertyReader.getProperty("XDC_MAX_FEE_PER_GAS", "80000000000")));// 80 Gwei
  private static final String POLYGON_WEB3_PROVIDER =
      PropertyReader.getProperty("POLYGON_WEB3_PROVIDER", "https://polygon-amoy.g.alchemy.com/v2/1MH6JeN9slpV-Qh0x31QeMl4pOnRHq94");
  private static final String POLYGON_WEB3_PROVIDER_2 =
      PropertyReader.getProperty("POLYGON_WEB3_PROVIDER_2", "https://polygon-amoy.g.alchemy.com/v2/1MH6JeN9slpV-Qh0x31QeMl4pOnRHq94");
  private static final String ETHEREUM_WEB3_PROVIDER =
      PropertyReader.getProperty("ETHEREUM_WEB3_PROVIDER", "");
  private static final String ETHEREUM_WEB3_PROVIDER_2 =
      PropertyReader.getProperty("ETHEREUM_WEB3_PROVIDER_2", "");
  private static final String XDC_WEB3_PROVIDER =
      PropertyReader.getProperty("XDC_WEB3_PROVIDER", "");
  private static final String XDC_WEB3_PROVIDER_2 =
      PropertyReader.getProperty("XDC_WEB3_PROVIDER_2", "");
  private static final String SIGNER_REQUEST_SECRET = PropertyReader.getProperty("SIGNER_REQUEST_SECRET", "").length() > 0
      ? EncryptDecrypt2.decrypt(System.getProperty("ENCRYPTION_KEY"), PropertyReader.getProperty("SIGNER_REQUEST_SECRET", ""))
      : "";
  private static final String USDC_CONTRACT = PropertyReader.getProperty("USDC_CONTRACT", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48");
  private static final String USDT_CONTRACT = PropertyReader.getProperty("USDT_CONTRACT", "0xdAC17F958D2ee523a2206206994597C13D831ec7");
  private static final String XUSDC_CONTRACT = PropertyReader.getProperty("XUSDC_CONTRACT", "0xfA2958CB79b0491CC627c1557F441eF849Ca8eb1");
  private static final String XUSDT_CONTRACT = PropertyReader.getProperty("XUSDT_CONTRACT", "");
  private static final String POLYGON_SCAN_URL = PropertyReader.getProperty("POLYGON_SCAN_URL", "https://polygonscan.com");
  private static final String POLYGON_AMOY_SCAN_URL = PropertyReader.getProperty("POLYGON_AMOY_SCAN_URL", "https://amoy.polygonscan.com");
  private static final String ETHER_SCAN_URL = PropertyReader.getProperty("ETHER_SCAN_URL", "https://etherscan.io");
  private static final String SEPOLIA_SCAN_URL = PropertyReader.getProperty("SEPOLIA_SCAN_URL", "https://sepolia.etherscan.io");

  private static final String SIGNER_URL = PropertyReader.getProperty("SIGNER_URL", "");
  private static final BigInteger GAS_LIMIT_FOR_POSITION_UPDATE = new BigInteger(PropertyReader.getProperty("GAS_LIMIT_FOR_POSITION_UPDATE", "500000"));
  private static final String POSITION_MANAGER_CHAIN = PropertyReader.getProperty("POSITION_MANAGER_CHAIN", POLYGON);
  private static final String POSITION_MANAGER_CONTRACT_ADDRESS = PropertyReader.getProperty("POSITION_MANAGER_CONTRACT_ADDRESS", "0xf857aC7bed76B43c5457f56483803C4CebbD7eff");
  private static final String XDC_POSITION_MANAGER_CHAIN = PropertyReader.getProperty("XDC_POSITION_MANAGER_CHAIN", XDC);
  private static final String XDC_POSITION_MANAGER_CONTRACT_ADDRESS = PropertyReader.getProperty("XDC_POSITION_MANAGER_CONTRACT_ADDRESS", "0xf857aC7bed76B43c5457f56483803C4CebbD7eff");
  private static final int POSITION_MANAGER_EOD_EMAIL_AT_HOUR = PropertyReader.getProperty("POSITION_MANAGER_EOD_EMAIL_AT_HOUR", 6);
  private static final String BLOCKCHAIN_KEY_FILE =
      PropertyReader.getProperty("BLOCKCHAIN_KEY_FILE", "./blockchain-keys.txt");
  private static final BigInteger GAS_LIMIT_FOR_WITHDRAWABLE_AMOUNT_UPDATE = new BigInteger(PropertyReader.getProperty("GAS_LIMIT_FOR_WITHDRAWABLE_AMOUNT_UPDATE", "500000"));
  private static final String FUND_MANAGER_CHAIN = PropertyReader.getProperty("FUND_MANAGER_CHAIN", ETHEREUM);
  private static final String FUND_MANAGER_CONTRACT_ADDRESS = PropertyReader.getProperty("FUND_MANAGER_CONTRACT_ADDRESS", "");
  private static final String FUND_MANAGER_V2_CHAIN = PropertyReader.getProperty("FUND_MANAGER_V2_CHAIN", ETHEREUM);
  private static final String FUND_MANAGER_V2_CONTRACT_ADDRESS = PropertyReader.getProperty("FUND_MANAGER_V2_CONTRACT_ADDRESS", "");
  private static final String XDC_FUND_MANAGER_V2_CHAIN = PropertyReader.getProperty("XDC_FUND_MANAGER_V2_CHAIN", XDC);
  private static final String XDC_FUND_MANAGER_V2_CONTRACT_ADDRESS = PropertyReader.getProperty("XDC_FUND_MANAGER_V2_CONTRACT_ADDRESS", "");
  private static final String STAKE_SYMBOL_IDS = PropertyReader.getProperty("STAKE_SYMBOL_IDS", "");
  private static Set<Integer> STAKE_SYMBOL_ID_MAP;
  private static final String VTOKEN_SYMBOL_IDS = PropertyReader.getProperty("VTOKEN_SYMBOL_IDS", "");
  private static Set<Integer> VTOKEN_SYMBOL_ID_MAP;
  private static int ENABLE_DETAIL_LOGS_FOR_USER_ID = PropertyReader.getProperty("ENABLE_DETAIL_LOGS_FOR_USER_ID", 41);
  private static final boolean LIQUIDITY_DEX_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("LIQUIDITY_DEX_ENABLED", FALSE));
  private static final double SAFETY_FACTOR_BPS_FOR_LIQUIDITY = PropertyReader.getProperty("SAFETY_FACTOR_BPS_FOR_LIQUIDITY", 0.002D);
  private static final double PROFIT_MARGIN_BPS_FOR_LIQUIDITY = PropertyReader.getProperty("PROFIT_MARGIN_BPS_FOR_LIQUIDITY", 0.001D);
  private static final double EXTERNAL_EXCHANGE_TRANSACTION_FEE = PropertyReader.getProperty("EXTERNAL_EXCHANGE_TRANSACTION_FEE", 0.001D); //0.1%
  private static final double EXTERNAL_EXCHANGE_SELL_QTY_TOLERANCE_PERCENTAGE = PropertyReader.getProperty("EXTERNAL_EXCHANGE_SELL_QTY_TOLERANCE_PERCENTAGE", 0.01D); //1%
  private static final double EXTERNAL_EXCHANGE_MAX_LEVERAGE = PropertyReader.getProperty("EXTERNAL_EXCHANGE_MAX_LEVERAGE", 2D); //max of 2x leverage
  private static final String TEST_USER_STRING = PropertyReader.getProperty("TEST_USERS", "");
  private static IntHashSet TEST_USERS = null;
  private static final String BINANCE_SPOT_REST = PropertyReader.getProperty("BINANCE_SPOT_REST", "https://api.binance.com");
  private static final String BINANCE_FUTURES_REST = PropertyReader.getProperty("BINANCE_FUTURES_REST", "https://fapi.binance.com");
  private static final String BINANCE_SPOT_WS = PropertyReader.getProperty("BINANCE_SPOT_WS", "wss://ws-api.binance.com:443/ws-api/v3");
  private static final String BINANCE_FUTURES_WS = PropertyReader.getProperty("BINANCE_FUTURES_WS", "wss://ws-fapi.binance.com/ws-fapi/v1");
  private static final String BINANCE_FUTURES_USERDATA_WS = PropertyReader.getProperty("BINANCE_FUTURES_USERDATA_WS", "wss://fstream.binance.com/ws");

  private static final String BYBIT_UNIFIED_REST = PropertyReader.getProperty("BYBIT_UNIFIED_REST", "https://api.bybit.com");
  private static final String BYBIT_UNIFIED_WS = PropertyReader.getProperty("BYBIT_UNIFIED_WS", "wss://stream.bybit.com/v5");
  private static final String OUTBOUND_IP = PropertyReader.getProperty("OUTBOUND_IP", null);
  private static final String API_URL = PropertyReader.getProperty("API_URL", "https://bitorderly.com");
  private static final String REQUEST_TOKEN = EncryptDecrypt2.decrypt(PropertyReader.getProperty("REQUEST_TOKEN", "Nvl8hT4o0_hUFUbWcQD6KaqFxrV9IvEhA_ziwnCjQm0="));
  private static final String REQUEST_SECRET = EncryptDecrypt2.decrypt(PropertyReader.getProperty("REQUEST_SECRET", "DHHWqr3AbV697mK-Qvrwb5RmKcuKGa-JMPshOu_KEBc="));
  private static final String RECONCILIATION_ALERT_EMAILS = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com");
  private static final String RECONCILIATION_OUTPUT_DIRECTORY = PropertyReader.getProperty("RECONCILIATION_OUTPUT_DIRECTORY", "/mnt/data/reconciliation");
  private static final boolean LIQUIDITY_IMBALANCE_SETTLE_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("LIQUIDITY_IMBALANCE_SETTLE_ENABLED", FALSE));
  private static final boolean LIQUIDITY_STABLE_COIN_AUTO_CONVERT_ENABLED = TRUE.equalsIgnoreCase(PropertyReader.getProperty("LIQUIDITY_STABLE_COIN_AUTO_CONVERT_ENABLED", FALSE));
  private static final double SPOT_PERP_SPREAD_THRESHOLD_PCT =  PropertyReader.getProperty("SPOT_PERP_SPREAD_THRESHOLD_PCT", 0.05);//5%
  // todo added to handle the compile error. please review.
  private static final String KUCOIN_SPOT_REST = PropertyReader.getProperty("KUCOIN_SPOT_REST", "https://api.kucoin.com");
  private static final String KUCOIN_FUTURES_REST = PropertyReader.getProperty("KUCOIN_FUTURES_REST", "https://api-futures.kucoin.com");
  private static final int EXTERNAL_EXCHANGE_PROXY_PORT =  PropertyReader.getProperty("EXTERNAL_EXCHANGE_PROXY_PORT", 8888);

  private static final int USDC_ID =  PropertyReader.getProperty("USDC_ID", 0);
  private static final int USDT_ID =  PropertyReader.getProperty("USDT_ID", 0);
  private static final int XUSDC_ID =  PropertyReader.getProperty("XUSDC_ID", 0);
  private static final int XUSDT_ID =  PropertyReader.getProperty("XUSDT_ID", 0);
  private static BigInteger GAS_LIMIT_FOR_USER_REGISTRATION =
      new BigInteger(PropertyReader.getProperty("GAS_LIMIT_FOR_USER_REGISTRATION", "150000"));
  private static final double AUTO_CONVERT_MIN_AMOUNT =  PropertyReader.getProperty("AUTO_CONVERT_MIN_AMOUNT", 0.01);// $0.01, or 1 cent


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
  private static final ManyToManyConcurrentArrayQueueCustom<Message> liquidityRouterQueue =
      new ManyToManyConcurrentArrayQueueCustom<>(LIQUIDITY_DEX_ENABLED ? LIQUIDITY_DEX_QUEUE_CAPACITY : 2, "liquidityDexQueue");
  private static final ManyToManyConcurrentArrayQueueCustom<User> riskToAutoConvertQueue =
      new ManyToManyConcurrentArrayQueueCustom<>(LIQUIDITY_DEX_ENABLED ? RISK_TO_MATCHER_QUEUE_CAPACITY : 2, "riskToAutoConvertQueue");

  //private static final ManyToOneConcurrentArrayQueueCustom<Message> publisherToBlockchainPositionQueue =
  //    new ManyToOneConcurrentArrayQueueCustom<>(BLOCKCHAIN_POSITION_MANAGER_ENABLED ? PUBLISHER_TO_PERSISTER_POSITION_QUEUE_CAPACITY : 2, "publisherToBlockchainPositionQueue");

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

  public static double getMinOrderValue() {
    return MIN_ORDER_VALUE;
  }

  public static double getMinTopThirtyOrderValue() {
    return MIN_TOP_THIRTY_ORDER_VALUE;
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

  public static String getCopyTradeThreadIdle() {
    return COPY_TRADE_THREAD_IDLE;
  }

  public static int getTopBottomReloadHourInCest() {
    return TOP_BOTTOM_RELOAD_HOUR_IN_CEST;
  }

  public static int getLiquidityDepthLevels() {
    return LIQUIDITY_DEPTH_LEVELS;
  }

  public static boolean isBlockchainPositionManagerEnabled() {
    return BLOCKCHAIN_POSITION_MANAGER_ENABLED;
  }

/*  public static ManyToOneConcurrentArrayQueueCustom<Message> getPublisherToBlockchainPositionQueue() {
    return publisherToBlockchainPositionQueue;
  }*/

  public static BigInteger getPolygonMaxPriorityGasPrice() {
    return POLYGON_MAX_PRIORITY_GAS_PRICE;
  }

  public static BigInteger getPolygonMinPriorityGasPrice() {
    return POLYGON_MIN_PRIORITY_GAS_PRICE;
  }

  public static BigInteger getPolygonMaxFeePerGas() {
    return POLYGON_MAX_FEE_PER_GAS;
  }

  public static BigInteger getEthereumMaxPriorityGasPrice() {
    return ETHEREUM_MAX_PRIORITY_GAS_PRICE;
  }

  public static BigInteger getEthereumMinPriorityGasPrice() {
    return ETHEREUM_MIN_PRIORITY_GAS_PRICE;
  }

  public static BigInteger getEthereumMaxFeePerGas() {
    return ETHEREUM_MAX_FEE_PER_GAS;
  }

  public static String getUsdcContract() {
    return USDC_CONTRACT;
  }

  public static String getUsdtContract() {
    return USDT_CONTRACT;
  }

  public static String getWeb3Provider(final String chainType, final boolean useSecondary) {
    if (chainType == null) return useSecondary ? POLYGON_WEB3_PROVIDER_2 : POLYGON_WEB3_PROVIDER;
    return switch (chainType.toUpperCase()) {
      case ETHEREUM, MAINNET, SEPOLIA -> useSecondary ? ETHEREUM_WEB3_PROVIDER_2 : ETHEREUM_WEB3_PROVIDER;
      case XDC, XDC_APOTHEM -> useSecondary ? XDC_WEB3_PROVIDER_2 : XDC_WEB3_PROVIDER;
      case POLYGON -> useSecondary ? POLYGON_WEB3_PROVIDER_2 : POLYGON_WEB3_PROVIDER;
      default -> useSecondary ? POLYGON_WEB3_PROVIDER_2 : POLYGON_WEB3_PROVIDER;
    };
  }

  public static byte[] getSignerRequestSecret() {
    return SIGNER_REQUEST_SECRET.getBytes();
  }

  public static String getScanUrlByTransaction(final String chainType) {
    if (chainType == null) return "";
    switch (chainType.toUpperCase()) {
      case ETHEREUM:
        return ETHER_SCAN_URL + "/tx/";
      case SEPOLIA:
        return SEPOLIA_SCAN_URL + "/tx/";
      case POLYGON:
        return POLYGON_SCAN_URL + "/tx/";
      case POLYGON_AMOY:
        return POLYGON_AMOY_SCAN_URL + "/tx/";
    }
    return "";
  }

  public static String getSignerUrl() {
    return SIGNER_URL;
  }

  public static BigInteger getGasLimitForPositionUpdate() {
    return GAS_LIMIT_FOR_POSITION_UPDATE;
  }

  public static String getPositionManagerChain() {
    return POSITION_MANAGER_CHAIN;
  }

  public static String getPositionManagerContractAddress() {
    return POSITION_MANAGER_CONTRACT_ADDRESS;
  }

  public static String getBlockchainKeyFile() {
    return BLOCKCHAIN_KEY_FILE;
  }

  public static BigInteger getGasLimitForWithdrawableAmountUpdate() {
    return GAS_LIMIT_FOR_WITHDRAWABLE_AMOUNT_UPDATE;
  }

  public static String getFundManagerChain() {
    return FUND_MANAGER_CHAIN;
  }

  public static String getFundManagerContractAddress() {
    return FUND_MANAGER_CONTRACT_ADDRESS;
  }

  public static boolean isStakingEnabled() {
    return STAKING_ENABLED;
  }

  public static Set<Integer> getStakeSymbolIdMap() {
    if (STAKE_SYMBOL_ID_MAP == null) {
      synchronized (Context.class) {
        if (STAKE_SYMBOL_ID_MAP == null) {
          Set<Integer> symbolIdSet = new HashSet<>();
          if (STAKE_SYMBOL_IDS != null && !STAKE_SYMBOL_IDS.isEmpty()) {
            for (String id : STAKE_SYMBOL_IDS.split(",")) {
              symbolIdSet.add(StringUtil.toInt(id.trim()));
            }
          }
          STAKE_SYMBOL_ID_MAP = symbolIdSet;
        }
      }
    }
    return STAKE_SYMBOL_ID_MAP;
  }

  public static Set<Integer> getVtokenSymbolIdMap() {
    if (VTOKEN_SYMBOL_ID_MAP == null) {
      synchronized (Context.class) {
        if (VTOKEN_SYMBOL_ID_MAP == null) {
          Set<Integer> symbolIdSet = new HashSet<>();
          if (VTOKEN_SYMBOL_IDS != null && !VTOKEN_SYMBOL_IDS.isEmpty()) {
            for (String id : VTOKEN_SYMBOL_IDS.split(",")) {
              symbolIdSet.add(StringUtil.toInt(id.trim()));
            }
          }
          VTOKEN_SYMBOL_ID_MAP = symbolIdSet;
        }
      }
    }
    return VTOKEN_SYMBOL_ID_MAP;
  }

  public static int getEnableDetailLogsForUserId() {
    return ENABLE_DETAIL_LOGS_FOR_USER_ID;
  }

  public static int getLiquidityInstrumentPairId() {
    return LIQUIDITY_INSTRUMENT_PAIR_ID;
  }

  public static boolean isLiquidityDexEnabled() {
    return LIQUIDITY_DEX_ENABLED;
  }

  public static ManyToManyConcurrentArrayQueueCustom<Message> getLiquidityRouterQueue() {
    return liquidityRouterQueue;
  }

  public static String getLiquidityDexThreadIdle() {
    return LIQUIDITY_DEX_THREAD_IDLE;
  }

  public static String[] getLiquidityExchangePreference() {
    return LIQUIDITY_EXCHANGE_PREFERENCE;
  }

  public static boolean isEnableBalanceWithdrawSpotLimits() {
    return ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS;
  }

  public static double getPromoDepositThreshold() {
    return PROMO_DEPOSIT_THRESHOLD;
  }

  public static double getPromoDepositValue() {
    return PROMO_DEPOSIT_VALUE;
  }

  public static double getSafetyFactorBpsForLiquidity() {
    return SAFETY_FACTOR_BPS_FOR_LIQUIDITY;
  }

  public static double getProfitMarginBpsForLiquidity() {
    return PROFIT_MARGIN_BPS_FOR_LIQUIDITY;
  }

  public static double getExternalExchangeTransactionFee() {
    return EXTERNAL_EXCHANGE_TRANSACTION_FEE;
  }

  public static long getExternalExchangeBalanceCacheDurationMs() {
    return EXTERNAL_EXCHANGE_BALANCE_CACHE_DURATION_MS;
  }

  public static String getBinanceExchangeBaseUrl() {
    return BINANCE_EXCHANGE_BASE_URL;
  }

  public static String getBinanceFuturesExchangeBaseUrl() {
    return BINANCE_FUTURES_EXCHANGE_BASE_URL;
  }

  public static String getBybitExchangeRecvWindow() {
    return BYBIT_EXCHANGE_RECV_WINDOW;
  }

  public static String getMexcExchangeBaseUrl() {
    return MEXC_EXCHANGE_BASE_URL;
  }

  public static String getBitgetExchangeBaseUrl() {
    return BITGET_EXCHANGE_BASE_URL;
  }

  public static boolean getBitgetExchangeDemoTradingEnable(){return  BITGET_EXCHANGE_DEMO_TRADING_ENABLE; }


  public static double getExternalExchangeSellQtyTolerancePercentage() {
    return EXTERNAL_EXCHANGE_SELL_QTY_TOLERANCE_PERCENTAGE;
  }

  public static double getExternalExchangeMaxLeverage() {
    return EXTERNAL_EXCHANGE_MAX_LEVERAGE;
  }

  public static Set<Integer> getTestUsers() {
    if (TEST_USERS == null) {
      final String[] userIds = TEST_USER_STRING.split(",");
      TEST_USERS = new IntHashSet(Math.max(1, userIds.length), 0.6f);

      for (final String userId : userIds) {
        TEST_USERS.add(StringUtil.toInt(userId));
      }

    }
    return TEST_USERS;
  }

  public static String getBinanceSpotRest() {
    return BINANCE_SPOT_REST;
  }

  public static String getBinanceFuturesRest() {
    return BINANCE_FUTURES_REST;
  }

  public static String getBinanceSpotWs() {
    return BINANCE_SPOT_WS;
  }

  public static String getBinanceFuturesWs() {
    return BINANCE_FUTURES_WS;
  }

  public static String getBybitUnifiedRest() {
    return BYBIT_UNIFIED_REST;
  }

  public static String getBybitUnifiedWs() {
    return BYBIT_UNIFIED_WS;
  }

  public static String getBinanceFuturesUserdataWs() {
    return BINANCE_FUTURES_USERDATA_WS;
  }

  public static String getOutboundIp() {
    return OUTBOUND_IP;
  }

  public static String getApiUrl() {
    return API_URL;
  }

  public static String getRequestToken() {
    return REQUEST_TOKEN;
  }

  public static String getRequestSecret() {
    return REQUEST_SECRET;
  }

  public static String getPolygonWeb3Provider2() {
    return POLYGON_WEB3_PROVIDER_2;
  }

  public static String getEthereumWeb3Provider2() {
    return ETHEREUM_WEB3_PROVIDER_2;
  }

  public static int getPositionManagerEodEmailAtHour() {
    return POSITION_MANAGER_EOD_EMAIL_AT_HOUR;
  }

  public static String getReconciliationAlertEmails() {
    return RECONCILIATION_ALERT_EMAILS;
  }

  public static String getReconciliationOutputDirectory() {
    return RECONCILIATION_OUTPUT_DIRECTORY;
  }

  public static double getLiquidityDexSmallOrderValueThreshold() {
    return LIQUIDITY_DEX_SMALL_ORDER_VALUE_THRESHOLD;
  }

  public static boolean isLiquidityImbalanceSettleEnabled() {
    return LIQUIDITY_IMBALANCE_SETTLE_ENABLED;
  }

  public static String getKuCoinSpotRest() {
    return KUCOIN_SPOT_REST;
  }

  public static String getKuCoinFuturesRest() {
    return KUCOIN_FUTURES_REST;
  }

  public static ManyToManyConcurrentArrayQueueCustom<User> getRiskToAutoConvertQueue() {
    return riskToAutoConvertQueue;
  }

  public static boolean isLiquidityStableCoinAutoConvertEnabled() {
    return LIQUIDITY_STABLE_COIN_AUTO_CONVERT_ENABLED;
  }

  public static double getSpotPerpSpreadThresholdPct() {
    return SPOT_PERP_SPREAD_THRESHOLD_PCT;
  }

  public static String getBybitExchangeBaseUrl() {
    return BYBIT_EXCHANGE_BASE_URL;
  }

  public static int getExternalExchangeProxyPort() {
    return EXTERNAL_EXCHANGE_PROXY_PORT;
  }

  public static String getXusdcContract() {
    return XUSDC_CONTRACT;
  }

  public static String getXusdtContract() {
    return XUSDT_CONTRACT;
  }

  public static String getFundManagerV2Chain() {
    return FUND_MANAGER_V2_CHAIN;
  }

  public static String getFundManagerV2ContractAddress() {
    return FUND_MANAGER_V2_CONTRACT_ADDRESS;
  }

  public static String getXdcFundManagerV2Chain() {
    return XDC_FUND_MANAGER_V2_CHAIN;
  }

  public static String getXdcFundManagerV2ContractAddress() {
    return XDC_FUND_MANAGER_V2_CONTRACT_ADDRESS;
  }

  public static String getXdcWeb3Provider() {
    return XDC_WEB3_PROVIDER;
  }

  public static String getXdcWeb3Provider2() {
    return XDC_WEB3_PROVIDER_2;
  }

  public static String getChainBySymbol(final String symbol) {
    if (symbol == null) return NONE;
    switch (symbol.toUpperCase()) {
      case USDC:
      case USDT:
        return ETHEREUM;
      case XUSDC:
      case XUSDT:
      case XDC:
        return XDC;
      default:
        return NONE;
    }
  }

  public static String getTokenAddressBySymbol(final String symbol) {
    if (USDC.equalsIgnoreCase(symbol)) {
      return Context.getUsdcContract();
    } else if (USDT.equalsIgnoreCase(symbol)) {
      return Context.getUsdtContract();
    } else if (XUSDC.equalsIgnoreCase(symbol)) {
      return Context.getXusdcContract();
    } else if (XUSDT.equalsIgnoreCase(symbol)) {
      return Context.getXusdtContract();
    } else if (XDC.equalsIgnoreCase(symbol)) {
      return Address.DEFAULT.toString();
    }

    return null;
  }

  public static String getContractBySymbolAndVersion(final String symbol, final int version) {
    if ((USDC.equalsIgnoreCase(symbol) || USDT.equalsIgnoreCase(symbol)) && version == 1) {
      return Context.getFundManagerContractAddress();
    } else if ((USDC.equalsIgnoreCase(symbol) || USDT.equalsIgnoreCase(symbol)) && version == 2) {
      return Context.getFundManagerV2ContractAddress();
    } else if ((XUSDC.equalsIgnoreCase(symbol) || XUSDT.equalsIgnoreCase(symbol)) && version == 2) {
      return Context.getXdcFundManagerV2ContractAddress();
    }
    return null;
  }

  public static String getFundManagerContractByNetworkAndVersion(final String network, final int version) {
    if ((ETHEREUM.equalsIgnoreCase(network) || MAINNET.equalsIgnoreCase(network) || SEPOLIA.equalsIgnoreCase(network)) && version == 1) {
      return Context.getFundManagerContractAddress();
    } else if ((ETHEREUM.equalsIgnoreCase(network) || MAINNET.equalsIgnoreCase(network) || SEPOLIA.equalsIgnoreCase(network)) && version == 2) {
      return Context.getFundManagerV2ContractAddress();
    } else if ((XDC.equalsIgnoreCase(network) || XDC_APOTHEM.equalsIgnoreCase(network)) && version == 2) {
      return Context.getXdcFundManagerV2ContractAddress();
    }
    return null;
  }

  public static int getUsdcId() {
    return USDC_ID;
  }

  public static int getUsdtId() {
    return USDT_ID;
  }

  public static int getXusdcId() {
    return XUSDC_ID;
  }

  public static int getXusdtId() {
    return XUSDT_ID;
  }

  public static BigInteger getXdcMaxFeePerGas() {
    return XDC_MAX_FEE_PER_GAS;
  }

  public static BigInteger getXdcMaxPriorityGasPrice() {
    return XDC_MAX_PRIORITY_GAS_PRICE;
  }

  public static BigInteger getXdcMinPriorityGasPrice() {
    return XDC_MIN_PRIORITY_GAS_PRICE;
  }

  public static BigInteger getGasLimitForUserRegistration() {
    return GAS_LIMIT_FOR_USER_REGISTRATION;
  }

  public static String getXdcPositionManagerChain() {
    return XDC_POSITION_MANAGER_CHAIN;
  }

  public static String getXdcPositionManagerContractAddress() {
    return XDC_POSITION_MANAGER_CONTRACT_ADDRESS;
  }

  public static double getAutoConvertMinAmount() {
    return AUTO_CONVERT_MIN_AMOUNT;
  }
}
