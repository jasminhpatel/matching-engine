package com.solfini.matchengine.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.IdleStrategyFactory;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.marketdata.InactiveMarketDataPublisherThread;
import com.solfini.marketdata.MarketDataOutputBuilderThread;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.matchengine.IpcPricingToEngineListener;
import com.solfini.matchengine.MessageValidator;
import com.solfini.matchengine.PricingThread;
import com.solfini.matchengine.drmode.KafkaDRFixListener;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.matchengine.kafka.KafkaMarketDataInputFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.kafka.KafkaListener.StopMode;
import com.solfini.matchengine.message.controller.ModeControlMessage;
import com.solfini.matchengine.message.controller.PublishControlMessage;
import com.solfini.matchengine.message.controller.ShutdownControlMessage;
import com.solfini.matchengine.publisher.MessagePublisher;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.pool.BusinessRejectObjectPool;
import com.solfini.pool.DRCancelOrderObjectPool;
import com.solfini.pool.DRExecutionReportObjectPool;
import com.solfini.pool.DROrderObjectPool;
import com.solfini.pool.DRRecieverDataObjectPool;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderMatchingThreadObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.pool.UserOpenOrdersByPairMatchThreadObjectPool;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.risk.RiskThread;
import com.solfini.risk.RiskThreadIndexed;
import com.solfini.risk.UserRiskCache;
import com.solfini.user.UserStats;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * The ControllerThread class implements a thread that listen to control messages from the controller and execute them. These actions
 * include warm starting and fail-over handling.
 */
public class ControllerThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ControllerThread.class);

  public static final String KAFKA_DR_FIX_LISTENER = "KafkaDRFixListener";
  private static final String LOAD_FROM_SNAP = PropertyReader.getProperty("LOAD_FROM_SNAP", "");
  private static final ReplayMode LOAD_FROM_SNAP_AND_REPLAY = loadReplayMode();
  private static final boolean PUBLISH_ON_SNAP_AND_REPLAY =
      Boolean.parseBoolean(PropertyReader.getProperty("PUBLISH_ON_SNAP_AND_REPLAY", "false"));

  private static ReplayMode loadReplayMode() {
    ReplayMode replayMode = ReplayMode.valueOf(PropertyReader.getProperty("LOAD_FROM_SNAP_AND_REPLAY", "OUTPUT").toUpperCase());
    if (ReplayMode.INPUT == replayMode) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "LOAD_FROM_SNAP_AND_REPLAY mode INPUT is no longer supported - defaulting to NONE");
      }
      replayMode = ReplayMode.NONE;
    }
    if (ReplayMode.BOTH == replayMode) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "LOAD_FROM_SNAP_AND_REPLAY mode BOTH is no longer supported - defaulting to OUTPUT");
      }
      replayMode = ReplayMode.OUTPUT;
    }

    return replayMode;
  }

  private KafkaListener listener;
  private long lastOutputSequence = 0;

  private void startListener(final KafkaListener listener, final String name) {
    LOGGER.info(LOG_FMT_2, "Starting listener thread: ", name);
    this.listener = listener;

    Thread thread = new Thread(listener, name);
    thread.start();
  }

  // Stops the listener at the offset specified.
  private KafkaListener stopListener(final long offset) {
    if (null != listener) {
      try {
        listener.shutdownAtOffset(offset);
        LOGGER.info(LOG_FMT_1, "Stopped listener thread");
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    KafkaListener stopped = listener;
    listener = null;

    return stopped;
  }

  // Stops the listener
  private KafkaListener stopListener(final StopMode mode) {
    if (null != listener) {
      try {
        listener.shutdown(mode);
        LOGGER.info(LOG_FMT_1, "Stopped listener thread");
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    KafkaListener stopped = listener;
    listener = null;

    return stopped;
  }

  private void startRiskThreads() {
    LOGGER.info(LOG_FMT_1, "Starting risk threads");

    final RiskThread riskThread = new RiskThread(IdleStrategyFactory.create(Context.getRiskThreadIdle()));
    final RiskAutoLiquidationThread riskAutoLiquidationThread =
        new RiskAutoLiquidationThread(IdleStrategyFactory.create(Context.getRiskThreadIdle()));

    final int[] sides = {0, 1};
    final int[] buckets1 = {UserRiskCache.RISK_BUCKET_LEVERAGE_10};
    final int[] buckets2 = {UserRiskCache.RISK_BUCKET_LEVERAGE_9};
    final int[] buckets3 = {UserRiskCache.RISK_BUCKET_LEVERAGE_8};
    final int[] buckets4 = {UserRiskCache.RISK_BUCKET_LEVERAGE_7};
    final int[] buckets5 = {UserRiskCache.RISK_BUCKET_LEVERAGE_6};
    final int[] buckets6 = {UserRiskCache.RISK_BUCKET_LEVERAGE_5, UserRiskCache.RISK_BUCKET_LEVERAGE_4,
        UserRiskCache.RISK_BUCKET_LEVERAGE_3, UserRiskCache.RISK_BUCKET_LEVERAGE_2, UserRiskCache.RISK_BUCKET_LEVERAGE_1};

    int[] pairArr = new int[InstrumentCache.getPairCapacity()];
    for (int i = 0; i < pairArr.length; i++) {
      if (InstrumentCache.getPair(i) != null)
        pairArr[i] = i;
    }

    final RiskThreadIndexed riskThreadIndexed1 =
        new RiskThreadIndexed(IdleStrategyFactory.create(Context.getRiskThreadIdle()), pairArr, sides, buckets1);
    final RiskThreadIndexed riskThreadIndexed2 =
        new RiskThreadIndexed(IdleStrategyFactory.create(Context.getRiskThreadIdle()), pairArr, sides, buckets2);
    final RiskThreadIndexed riskThreadIndexed3 =
        new RiskThreadIndexed(IdleStrategyFactory.create(Context.getRiskThreadIdle()), pairArr, sides, buckets3);
    final RiskThreadIndexed riskThreadIndexed4 =
        new RiskThreadIndexed(IdleStrategyFactory.create(Context.getRiskThreadIdle()), pairArr, sides, buckets4);
    final RiskThreadIndexed riskThreadIndexed5 =
        new RiskThreadIndexed(IdleStrategyFactory.create(Context.getRiskThreadIdle()), pairArr, sides, buckets5);
    final RiskThreadIndexed riskThreadIndexed6 =
        new RiskThreadIndexed(IdleStrategyFactory.create(Context.getRiskThreadIdle()), pairArr, sides, buckets6);

    new Thread(riskThread, "riskThread").start();
    new Thread(riskAutoLiquidationThread, "riskAutoLiquidationThread").start();
    new Thread(riskThreadIndexed1, "riskThreadIndexed1").start();
    new Thread(riskThreadIndexed2, "riskThreadIndexed2").start();
    new Thread(riskThreadIndexed3, "riskThreadIndexed3").start();
    new Thread(riskThreadIndexed4, "riskThreadIndexed4").start();
    new Thread(riskThreadIndexed5, "riskThreadIndexed5").start();
    new Thread(riskThreadIndexed6, "riskThreadIndexed6").start();
  }

  private void startMarketDataThreads() throws IOException {
    LOGGER.info(LOG_FMT_2, "Starting market data threads: enabled=", Context.isPublishMarketData());

    if (Context.isListenToIpcMarketData()) {
      final IpcPricingToEngineListener ipcPricingToEngineListener =
          new IpcPricingToEngineListener(IdleStrategyFactory.create(Context.getRiskThreadIdle()));
      new Thread(ipcPricingToEngineListener, "ipcPricingToEngineListener").start();
    }

    if (Context.isListenToKafkaMarketData()) {
      final KafkaMarketDataInputFixListener kafkaMarketDataInputFixListener = new KafkaMarketDataInputFixListener();
      new Thread(kafkaMarketDataInputFixListener, "kafkaMarketDataInputFixListener").start();
    }

    if (Context.isPublishMarketData()) {
      final MarketDataOutputBuilderThread marketDataBuilderThread = new MarketDataOutputBuilderThread();
      new Thread(marketDataBuilderThread, "marketDataBuilderThread").start();

      final InactiveMarketDataPublisherThread inactiveMarketDataPublisherThread = new InactiveMarketDataPublisherThread();
      new Thread(inactiveMarketDataPublisherThread, "inactiveMarketDataPublisherThread").start();
    }
  }

  private void startPricingThread() {
    LOGGER.info(LOG_FMT_2, "Starting pricing thread: enabled=", Context.isPricingThreadEnabled());

    if (Context.isPricingThreadEnabled()) {
      final PricingThread pricingThread = new PricingThread(IdleStrategyFactory.create(Context.getRiskThreadIdle()));
      new Thread(pricingThread, "pricingThread").start();
    }
  }

  private void startTimeEventGeneratorThread() {
    LOGGER.info(LOG_FMT_1, "Starting time event generator thread");

    new Thread(Context.getTimeEventGeneratorThread(), "timeEventGeneratorThread").start();
  }

  private String getSnapshotId() {
    if ((null != LOAD_FROM_SNAP) && (LOAD_FROM_SNAP.length() > 0)) {
      return LOAD_FROM_SNAP;
    }

    return null;
  }

  private enum ReplayMode {
    NONE, INPUT, OUTPUT, BOTH
  }

  private long restorePrimaryState() {
    long lastOffset = -1;
    lastOutputSequence = 0;
    if (null != getSnapshotId()) {
      long snapId = StringUtil.toLong(getSnapshotId());
      boolean replay = (ReplayMode.OUTPUT == LOAD_FROM_SNAP_AND_REPLAY) || (ReplayMode.BOTH == LOAD_FROM_SNAP_AND_REPLAY);

      LOGGER.info(LOG_FMT_4, "WarmStart: Restoring state from snapshot: snapId=", snapId, ", replay=", replay);
      SnapLoader snapLoader = new SnapLoader(snapId);
      lastOffset = snapLoader.loadPrimary(replay);
      if (replay) {
        lastOutputSequence = snapLoader.getLastSequenceNumber();
      }
      LOGGER.debug(LOG_FMT_4, "WarmStart: Restoring state from snapshot completed: snapId=", snapId, ", lastOutputOffset=", lastOffset);
    } else {
      LOGGER.warn("WarmStart: Skipping state restoration as no snapshot is specified");
    }

    return lastOffset;
  }

  private long restoreSecondaryState() {
    long lastOffset = -1;
    lastOutputSequence = 0;
    if (null != getSnapshotId()) {
      long snapId = StringUtil.toLong(getSnapshotId());
      boolean replay = (ReplayMode.OUTPUT == LOAD_FROM_SNAP_AND_REPLAY) || (ReplayMode.BOTH == LOAD_FROM_SNAP_AND_REPLAY);

      LOGGER.info(LOG_FMT_4, "WarmStart: Restoring state from snapshot: snapId=", snapId, ", replay=", replay);
      SnapLoader snapLoader = new SnapLoader(snapId);
      lastOffset = snapLoader.loadSecondary(replay);
      if (replay) {
        lastOutputSequence = snapLoader.getLastSequenceNumber();
      }
      LOGGER.debug(LOG_FMT_4, "WarmStart: Restoring state from snapshot completed: snapId=", snapId, ", lastOutputOffset=", lastOffset);
    } else {
      LOGGER.warn("WarmStart: Skipping state restoration as no snapshot is specified");
    }

    return lastOffset;
  }

  // Publishes a TradeStateAdmin message to the primary output queue with the
  // failover flag set. Returns the offset of the message sent.
  private long sendFailoverMessage() {
    short encodedLength = 2;
    final int KAFKA_OFFSET = 17;

    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
    com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    headerEncoder.transactionId(TimeUtil.getTime());
    headerEncoder.transactionEnd((short) 1);
    encodedLength += headerEncoder.encodedLength();

    tradeStateAdminMessageEncoder.marketStatus(MarketStatus.FAILOVER);
    tradeStateAdminMessageEncoder.senderInstanceId(Context.getInstanceId());

    encodedLength += tradeStateAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    final String topic = PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "");

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "Sending failover message to ", topic, " InstanceID: ", Context.getInstanceId());
    }

    // Set the index to -1 so that the failover message will have the sequence number of 0.
    KafkaPublisher publisher = new KafkaPublisher(-1);
    Future<RecordMetadata> future = publisher.sendDirect(topic, bytesWithKafkaOffset, KafkaPublisher.ADMIN_API);
    publisher.flush();

    try {
      return future.get().offset();
    } catch (Exception e) {
      LOGGER.error("Exception thrown while waiting for offset. Message: " + e.getMessage());
      return 0;
    }
  }

  private void initializePrimaryObjectPools() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, "Warming up primary object pools");
    }

    OrderObjectPool.init();
    PositionMatchThreadObjectPool.init();
    UserOpenOrdersByPairMatchThreadObjectPool.init();
    BalanceAdminMessageObjectPool.init();
    BusinessRejectObjectPool.init();
    ExecutionReportObjectPool.init();
    OrderMatchingThreadObjectPool.init();
  }

  private void initializeSecondaryObjectPools() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, "Warming up secondary object pools");
    }

    DROrderObjectPool.init();
    DRCancelOrderObjectPool.init();
    DRExecutionReportObjectPool.init();
    DRRecieverDataObjectPool.init();
  }

  private void handleMessage(final ModeControlMessage message) throws IOException {
    switch (message.getMode()) {
      case PRIMARY:
        PublishControlMessage publishControlMessage = null;
        if (Mode.NONE == message.getPreviousMode()) {
          // Initialize pools
          initializePrimaryObjectPools();

          // The instance is being warm-started as the primary
          if (PUBLISH_ON_SNAP_AND_REPLAY) {
            // If we are using --warm-start, directly switch to primary mode and enable output message publication
            Context.getReceiverToMatcherQueue().add(message);

            publishControlMessage = new PublishControlMessage(MessagePublisher.ME_KAFKA_TOPIC_PRIMARY, 0);
            Context.getReceiverToMatcherQueue().add(publishControlMessage);
          } else {
            // Otherwise we first switch to secondary mode until we recover state
            Context.setMarketStatus(MarketStatus.DR_MODE);
            Context.getReceiverToMatcherQueue().add(new ModeControlMessage(message.getPreviousMode(), Mode.SECONDARY));
          }

          // Restore state from snapshot and if specified, output queue replay
          restorePrimaryState();
        } else {
          // The instance is being promoted to primary after running as a secondary, so we mark the failover point
          final long lastOffset = sendFailoverMessage();

          // We then consume messages up to the market
          KafkaListener stopped = stopListener(lastOffset);
          if (stopped != null) {
            Context.getReceiverToMatcherQueue().add(message);
            lastOutputSequence = stopped.getLastSequence();
            LOGGER.error(LOG_FMT_2, "Last output sequence : ", lastOutputSequence);
          }
        }

        if (!PUBLISH_ON_SNAP_AND_REPLAY) {
          // Once we recovered in secondary mode, switch to primary
          Context.getReceiverToMatcherQueue().add(new ModeControlMessage(Mode.SECONDARY, Mode.PRIMARY));

          publishControlMessage = new PublishControlMessage(MessagePublisher.ME_KAFKA_TOPIC_PRIMARY, lastOutputSequence);
          Context.getReceiverToMatcherQueue().add(publishControlMessage);
        }

        // Message validator is no longer required.
        Context.setMessageValidator(null);

        // Wait for PublishControlMessage to be processed by the matching thread
        try {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_1, "Waiting for publisher to be ready");
          }
          if (publishControlMessage != null) {
            publishControlMessage.await();
          }
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_1, "Publisher is ready");
          }
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }

        // Start time events, risk, market data and pricing threads
        startTimeEventGeneratorThread();
        startRiskThreads();
        startMarketDataThreads();
        startPricingThread();

        // Start the input listener, with replay if configured
        boolean replay = (ReplayMode.INPUT == LOAD_FROM_SNAP_AND_REPLAY) || (ReplayMode.BOTH == LOAD_FROM_SNAP_AND_REPLAY);
        startListener(new KafkaInputFixListener(replay), "KafkaInputFixListener");

        LOGGER.info(LOG_FMT_1, ">>> READY <<<");
        break;

      case SECONDARY:
        if (Mode.NONE == message.getPreviousMode()) {
          // Initialize pools
          initializePrimaryObjectPools();
          initializeSecondaryObjectPools();

          // The instance is being warm-started as a secondary, so the steps to execute are
          Context.setMessageValidator(new MessageValidator());
          Context.getReceiverToMatcherQueue().add(message);
          Context.getReceiverToMatcherQueue().add(new PublishControlMessage(MessagePublisher.ME_KAFKA_TOPIC_SECONDARY, 0));
          long lastOffset = restoreSecondaryState();
          if (-1 == lastOffset) {
            // If we did not restore state from a snapshot (+ replay), then we need to tell the DR listener
            // to request for a new snapshot and work from there (i.e. use LOAD_STRATEGY_WAIT_FOR_SNAP)
            startListener(new KafkaDRFixListener(KafkaDRFixListener.LOAD_STRATEGY_WAIT_FOR_SNAP), KAFKA_DR_FIX_LISTENER);
          } else {
            // If we did restore the state from a snapshot (+ replay), then we need to tell the DR listener
            // to continue to read the PRIMARY output queue from where we left off.
            if ((ReplayMode.OUTPUT == LOAD_FROM_SNAP_AND_REPLAY) || (ReplayMode.BOTH == LOAD_FROM_SNAP_AND_REPLAY)) {
              startListener(new KafkaDRFixListener(KafkaDRFixListener.LOAD_STRATEGY_REPLAY_FROM_OFFSET, lastOffset), KAFKA_DR_FIX_LISTENER);
            } else {
              startListener(new KafkaDRFixListener(KafkaDRFixListener.LOAD_STRATEGY_NONE), KAFKA_DR_FIX_LISTENER);
            }
          }
        }

        LOGGER.info(LOG_FMT_1, ">>> READY <<<");
        break;

      default:
    }
  }

  private void handleMessage(final ShutdownControlMessage message) throws InterruptedException {
    // Shutdown the listener thread
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down listener");
    }
    stopListener(KafkaListener.StopMode.NEXT_ITERATION);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down listener completed");
    }

    // Shutdown the auto liquidation thread
    if (RiskAutoLiquidationThread.getInstance() != null) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down auto liquidation thread");
      }
      RiskAutoLiquidationThread.getInstance().stopThread();
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down auto liquidation thread completed");
      }
    }

    // Shutdown persist threads
    if (Context.getPersistThread() != null) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down persist thread");
      }
      Context.getPersistThread().shutdown();
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down persist thread completed");
      }
    }

    if (Context.getPersistPositionThread() != null) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down persist (position) thread");
      }
      Context.getPersistPositionThread().shutdown();
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Shutting down persist (position) thread completed");
      }
    }

    // Shutdown user stats
    if (Context.isUserStatsEnabled()) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Updating user statistics");
      }
      UserStats.persist();
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Shutdown: Updating user statistics completed");
      }
    }

    // Flush the pipeline and shutdown
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, "Shutdown: Flushing processing pipeline");
    }
    Context.getReceiverToMatcherQueue().add(message);
  }

  @Override
  public void run() {
    while (true) {
      try {
        // Poll the control queue and handle control messages
        final Message message = Context.getControlQueue().poll();
        if (null != message) {
          LOGGER.info(LOG_FMT_2, "Control message received: ", message);

          if (message instanceof ModeControlMessage) {
            handleMessage((ModeControlMessage) message);
          } else if (message instanceof ShutdownControlMessage) {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_2, "Shutdown: Handling shutdown message ", message);
            }
            handleMessage((ShutdownControlMessage) message);
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_2, "Shutdown: Handling shutdown message completed ", message);
            }
          } else {
            LOGGER.info(LOG_FMT_1, "Unsupported control message rejected");
          }

          LOGGER.info(LOG_FMT_2, "Control message processed: ", message);
        }

        Thread.sleep(100);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }
}
