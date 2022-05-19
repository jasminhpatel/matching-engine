package com.solfini.matchengine.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;

/**
 * The UnifiedController class implements a controller that promote the matching engine to primary or secondary on startup based on a set
 * configuration, and then handle failover based on administrative message published on a Kafka topic. In addition it uses ZooKeeper for
 * making sure that only a single instance acts as primary at any given time.
 */
public class UnifiedController extends Controller implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UnifiedController.class);

  private static final int CONNECTION_TIMEOUT = 10_000;
  private static final int SESSION_TIMEOUT = 5_000;
  private static final String KEY_PATH = "/com.solfini.matchengine.primary";

  private final Mode controllerMode;
  private final byte[] instanceIdBytes;
  private final String zooKeeperLocation;
  private final String topic;
  private ZooKeeper connection;

  private volatile boolean terminating = false;
  private CountDownLatch terminatingLatch = new CountDownLatch(1);

  public UnifiedController(final String instanceId, final String zooKeeperLocation, final String topic, final Mode mode,
      final OneToOneConcurrentArrayQueueCustom<Message> queue) {
    super(instanceId, Mode.NONE, queue);
    this.controllerMode = mode;
    this.instanceIdBytes = instanceId.getBytes();
    this.zooKeeperLocation = zooKeeperLocation;
    this.topic = topic;
    this.connection = null;
  }

  private boolean connect() {
    try {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "Connecting to ZooKeeper: ", zooKeeperLocation);
      }
      final CountDownLatch latch = new CountDownLatch(1);
      connection = new ZooKeeper(zooKeeperLocation, SESSION_TIMEOUT, new Watcher() {
        public void process(WatchedEvent event) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_2, "ZooKeeper Event: ", event.toString());
          }
          if (Watcher.Event.KeeperState.SyncConnected == event.getState()) {
            latch.countDown();
          }
        }
      });

      if (!latch.await(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)) {
        LOGGER.error("Timeout exceed while waiting for ZooKeeper connection");
        return false;
      }

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "Connected to ZooKeeper: ", zooKeeperLocation);
      }
      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to connect to ZooKeeper: " + e.getMessage(), e);
    }

    return false;
  }

  private boolean acquirePrimaryLock() {
    try {
      connection.create(KEY_PATH, instanceIdBytes, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
      return true;
    } catch (KeeperException.NodeExistsException e) {
      LOGGER.error("Failed to acquire primary lock: " + e.getMessage());
      LOGGER.error("Another instance may already be running as primary.");
    } catch (Exception e) {
      LOGGER.error("Failed to acquire primary lock: " + e.getMessage());
    }

    return false;
  }

  private void keepAlive() {
    try {
      connection.getData(KEY_PATH, null, null);
    } catch (KeeperException e) {
      switch (e.code()) {
        case CONNECTIONLOSS:
        case SESSIONEXPIRED:
        case SESSIONMOVED:
          connect();
          break;
        default:
      }
    } catch (InterruptedException e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  @Override
  public void execute(final String command) {
    if (command.equals("primary") && (Mode.PRIMARY != getMode())) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "Switching mode to ", Mode.PRIMARY.toString());
      }
      switchMode(Mode.PRIMARY);
    } else if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(LOG_FMT_2, "Unsupported command: ", command);
    }
  }

  @Override
  public void terminate() {
    terminating = true;
    try {
      terminatingLatch.await();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  @Override
  public void run() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_11, "Starting matching engine controller thread (Type: ", UnifiedController.class, ", InstanceID: ", getInstanceId(),
          ", ZooKeeperLocation: ", zooKeeperLocation, ", Topic: ", topic, ", StartMode: ", getMode(), ")");
    }

    if (!connect()) {
      LOGGER.error("Terminating this instance.");
      System.exit(1);
    }

    if (getMode() != controllerMode) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "Switching mode to ", controllerMode);
      }
      if ((Mode.PRIMARY == controllerMode) && !acquirePrimaryLock()) {
        LOGGER.error("Terminating this instance.");
        System.exit(1);
      }

      switchMode(controllerMode);
    }

    final KafkaCommandListener listener = new KafkaCommandListener(topic, this);
    listener.start();

    while (true) {
      try {
        if (terminating) {
          terminatingLatch.countDown();
          try {
            connection.close();
          } catch (Exception e1) {
            LOGGER.error(ERROR_LOG, e1);
          }

          super.terminate();
          return;
        }

        keepAlive();
        listener.process();
        Thread.sleep(100);
      } catch (InterruptedException e) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Stopping matching engine controller thread");
        }
        try {
          connection.close();
        } catch (Exception e1) {
          LOGGER.error(ERROR_LOG, e1);
        }
        return;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }
}
