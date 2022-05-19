package com.solfini.matchengine.controller;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.util.LogLevel;

/**
 * The ZooKeeperController class implements a controller that promote the matching engine to primary or secondary on startup and while in
 * operation, based on an election algorithm that uses ZooKeeper. Matching engine instances compete to become the primary. First to succeed
 * becomes the primary, and others become secondaries. If the primary fails, one of the secondaries is elected as the new primary.
 */
public class ZooKeeperController extends Controller implements Constants {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(ZooKeeperController.class);
  private static final String DEFAULT_KEY_PATH = "/com.solfini.matchengine.automatic";
  private static final String INSTANCES = "/instances";
  private static final String PRIMARY = "/primary";
  private static final String LAST_PRIMARY = "/last_primary";

  private final String connectString;
  private final String keyPath;
  private ZooKeeper connection;
  private long priorityId;
  private final long sessionTimeout;
  private final long connectionTimeout;
  private final String topic;

  private volatile boolean terminating = false;
  private CountDownLatch terminatingLatch = new CountDownLatch(1);
  private boolean instanceNodeCreated = false;

  public ZooKeeperController(final String instanceId, final String connectString, final long priority,
      final long sessionTimeout, final long connectionTimeout, final String topic,
      final OneToOneConcurrentArrayQueueCustom<Message> queue) {
    this(instanceId, connectString, DEFAULT_KEY_PATH, priority, sessionTimeout, connectionTimeout, topic, queue);
  }

  public ZooKeeperController(final String instanceId, final String connectString, final String keyPath,
      final long priorityId, final long sessionTimeout, final long connectionTimeout, final String topic,
      final OneToOneConcurrentArrayQueueCustom<Message> queue) {
    super(instanceId, Mode.NONE, queue);
    this.connectString = connectString;
    this.keyPath = keyPath;
    this.priorityId = priorityId;
    this.sessionTimeout = sessionTimeout;
    this.connectionTimeout = connectionTimeout;
    this.topic = topic;
  }

  // Returns the byte representation for the long value specified.
  private static final byte[] getBytes(final long data) {
    try {
      ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
      buffer.putLong(data);
      return buffer.array();
    } catch (Exception e) {
      return null;
    }
  }

  // Returns the byte representation for the string specified.
  private static final byte[] getBytes(final String data) {
    return data.getBytes();
  }

  // Returns the long value for the byte array specified.
  private static final long getLong(final byte[] data) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.put(data);
    buffer.flip();
    return buffer.getLong();
  }

  // Returns the string value for the byte array specified.
  private static final String getString(final byte[] data) {
    return new String(data);
  }

  // Connects to zookeeper. Throws if connection failed.
  private void connect() {
    LOGGER.info(LOG_FMT_2, "Connecting to ZooKeeper: ", connectString);

    try {
      final CountDownLatch latch = new CountDownLatch(1);
      connection = new ZooKeeper(connectString, (int) sessionTimeout * 1000, new Watcher() {
        public void process(WatchedEvent event) {
          if (Watcher.Event.KeeperState.SyncConnected == event.getState()) {
            latch.countDown();
          }
        }
      });

      // Wait indefinitely if the connection timeout is zero.
      if (connectionTimeout == 0) {
        latch.await();
      } else {
        if (!latch.await(connectionTimeout, TimeUnit.SECONDS)) {
          LOGGER.error(LOG_FMT_3, "Timeout of ", connectionTimeout,
              " seconds exceeded while waiting for Zookeeper connection. Exiting.");
          System.exit(1);
        }
      }

      LOGGER.info(LOG_FMT_2, "Connected to ZooKeeper: ", connectString);
    } catch (Exception e) {
      LOGGER.warn(LOG_FMT_2, "Failed to connect to ZooKeeper: ", connectString);
      connection = null;
    }
  }

  private String instanceKey(final String instanceId) {
    return keyPath + INSTANCES + (instanceId == null ? "" : "/" + instanceId);
  }

  // Creates persistent zookeeper nodes.
  private void createPersistentNodes() throws KeeperException, InterruptedException {
    try {
      LOGGER.debug(LOG_FMT_2, "Creating persistent node. : ", keyPath);
      connection.create(keyPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    } catch (KeeperException.NodeExistsException e) {
      LOGGER.debug(LOG_FMT_2, "Node already exists. node: ", keyPath);
    }

    try {
      LOGGER.debug(LOG_FMT_3, "Creating persistent node. node: ", keyPath, INSTANCES);
      connection.create(instanceKey(null), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    } catch (KeeperException.NodeExistsException e) {
      LOGGER.debug(LOG_FMT_3, "Node already exists. node: ", keyPath, INSTANCES);
    }
  }

  // Update the instance node with the priority of the instance.
  private void createInstanceNode() throws KeeperException, InterruptedException {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "Creating node. node: ", instanceKey(getInstanceId()));
    }
    connection.create(instanceKey(getInstanceId()), getBytes(priorityId), ZooDefs.Ids.OPEN_ACL_UNSAFE,
        CreateMode.EPHEMERAL);
  }

  private void updatePriority(final long newPriority) {
    if (priorityId != newPriority) {
      try {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "Updating node. node: ", instanceKey(getInstanceId()));
        }

        int version = connection.exists(instanceKey(getInstanceId()), true).getVersion();
        connection.setData(instanceKey(getInstanceId()), getBytes(newPriority), version);

        priorityId = newPriority;
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "Updating instance priority to ", priorityId);
        }
      } catch (Exception e) {
        LOGGER.error(LOG_FMT_2, "Failed to update instance priority", e);
      }
    }
  }

  // Returns true if this instance should be primary. False otherwise.
  private boolean shouldBePrimary() throws KeeperException, InterruptedException {
    // Check if the primary exists
    Stat primary = connection.exists(keyPath + PRIMARY, false);
    if (primary != null) {
      return false;
    }

    // Get all instances nodes
    final List<String> instances = connection.getChildren(instanceKey(null), false);

    String maxInstance = null;
    long maxPriority = Long.MIN_VALUE;

    if (LogLevel.debug()) {
      LOGGER.debug(LOG_FMT_4, "Self Instance '", getInstanceId(), "' has priority : ", priorityId);
    }

    // Find the node with the highest priority
    for (String instance : instances) {

      // Ignore self instance
      if (instance.equals(getInstanceId())) {
        continue;
      }

      final byte[] data = connection.getData(instanceKey(instance), false, null);
      final long instancePriority = getLong(data);

      if (LogLevel.debug()) {
        LOGGER.debug(LOG_FMT_4, "Instance '", instance, "' has priority : ", instancePriority);
      }

      if (instancePriority > maxPriority) {
        maxInstance = instance;
        maxPriority = instancePriority;
      }
    }

    // Should be primary as there is no other instance
    if (maxInstance == null) {
      if (LogLevel.info()) {
        LOGGER.debug(LOG_FMT_4, "No other instances present. Automatically electing as primary.");
      }
      return true;
    }

    return maxPriority <= priorityId;
  }

  // Returns true if the instance has become primary. False otherwise.
  private boolean tryPromoteToPrimary() throws KeeperException, InterruptedException {
    if (shouldBePrimary()) {
      LOGGER.info(LOG_FMT_1, "Trying to acquire primary lock.");
      try {
        connection.create(keyPath + PRIMARY, getBytes(getInstanceId()), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
      } catch (KeeperException.NodeExistsException e) {
        // Writing to primary node failed. Which means another instance with the same
        // priority has succeeded.
        LOGGER.info(LOG_FMT_1, "Primary lock was already acquired by another node.");
        return false;
      }

      LOGGER.info(LOG_FMT_1, "Primary lock acquired.");
      return true;
    }

    return false;
  }

  private void keepAlive() throws KeeperException {
    try {
      connection.getData(keyPath + PRIMARY, null, null);
    } catch (KeeperException e) {
      switch (e.code()) {
        case CONNECTIONLOSS:
        case SESSIONEXPIRED:
        case SESSIONMOVED:
          throw e;

        default:
      }
    } catch (InterruptedException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Interrupted during keep alive");
      }
    }
  }

  // Sets the last primary name to the name of this instance
  private void setLastPrimaryName() throws KeeperException, InterruptedException {
    try {
      LOGGER.debug(LOG_FMT_3, "Creating node. node: ", keyPath, LAST_PRIMARY);
      connection.create(keyPath + LAST_PRIMARY, getBytes(getInstanceId()), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    } catch (KeeperException.NodeExistsException e) {
      // Try to set data as the node already exists
      connection.setData(keyPath + LAST_PRIMARY, getBytes(getInstanceId()), -1);
    }
  }

  // Returns the name of the last primary instance
  private String getLastPrimaryName() throws KeeperException, InterruptedException {
    try {
      final byte[] data = connection.getData(keyPath + LAST_PRIMARY, false, null);
      return getString(data);
    } catch (KeeperException.NoNodeException e) {
      return null;
    }
  }

  // Shutdown the process after trying to cleanup the nodes.
  private void die() {
    try {
      // Remove the instance node
      connection.delete(instanceKey(getInstanceId()), -1);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    System.exit(1);
  }

  @Override
  public void execute(final String command) {
    if (command.startsWith("priority:")) {
      updatePriority(Long.parseLong(command.substring(command.indexOf(':') + 1)));
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
    LOGGER.info(LOG_FMT_13, "Starting matching engine controller thread (Type: ", ZooKeeperController.class,
        ", InstanceID: ", getInstanceId(), ", ZooKeeper: ", connectString, ", Priority: ", priorityId,
        ", SessionTimeout: ", sessionTimeout, "s, ConnectionTimeout: ", connectionTimeout, ")");

    while (true) {
      try {
        // Connect to zookeeper
        if (null == connection) {
          connect();

          if (null == connection) {
            LOGGER.info(LOG_FMT_1, "Zookeeper connection failed. Retrying in 5 seconds");
            Thread.sleep(5000);
            continue;
          }
        }

        // Try to create the persistent nodes.
        createPersistentNodes();

        // Write the instance node
        try {
          createInstanceNode();
        } catch (KeeperException.NodeExistsException e) {

          // Check if the instance node was created by this process. If not, exit
          // immediately as there is another instance running with the same instance id.
          // Otherwise, we have observed a temporary Zookeeper disconnection. Wait for the
          // ephemeral node to delete itself, and try again.
          if (!instanceNodeCreated) {
            LOGGER.error(LOG_FMT_2, "Another node with the same instance name exists. Shutting down. instanceId: ",
                getInstanceId());
            System.exit(1);
          } else {

            final long retryStartMs = System.currentTimeMillis();

            // Retry for twice the session timeout + 10 seconds.
            final long retryWaitTime = (10 + (2 * sessionTimeout)) * 1000;

            // Keep retrying to connect for 'retryWaitTime'. Exit immediately if unable to connect within that time.
            boolean retrySuccess = false;
            while (System.currentTimeMillis() - retryStartMs < retryWaitTime) {

              try {
                createInstanceNode();
                retrySuccess = true;
                break;

              } catch (KeeperException.NodeExistsException e2) {
                LOGGER.debug(LOG_FMT_3, "Creating instance node failed. Elapsed: ",
                    (System.currentTimeMillis() - retryStartMs)/1000, "s");


                // Giving high priority to the current master to become primary again.
                if (getMode() == Mode.PRIMARY) {
                  Thread.sleep(1000);
                } else {
                  Thread.sleep(10_000);
                }
              }
            }

            if (!retrySuccess) {
              LOGGER.error(LOG_FMT_3, "Creating instance node failed after retrying for ", retryWaitTime,
                  " seconds. Exiting");
              System.exit(1);
            }
          }
        }

        instanceNodeCreated = true;
        LOGGER.info(LOG_FMT_1, "Instance node created");


        // This flag marks that this instance was selected as a primary using the
        // current zookeeper connection. A disconnection with zookeeper would delete all
        // ephemeral nodes. However, if we have re-established the connection and
        // selected as primary, we could continue without exiting.
        boolean primaryElectedOnThisConnection = false;

        final KafkaCommandListener listener = new KafkaCommandListener(topic, this);
        listener.start();

        while (true) {

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

          if (getMode() != Mode.PRIMARY) {

            // If the current instance is not primary, try to be the primary. This fails
            // either
            // 1) There is already a primary instance selected.
            // 2) There is no primary, but there is another instance with a higher priority.
            if (tryPromoteToPrimary()) {

              LOGGER.info(LOG_FMT_2, "Switching mode to ", Mode.PRIMARY);
              switchMode(Mode.PRIMARY);
              primaryElectedOnThisConnection = true;
              setLastPrimaryName();

            } else {

              // Being primary has failed. Send a mode change if we are not already secondary.
              if (getMode() != Mode.SECONDARY) {
                LOGGER.info(LOG_FMT_2, "Switching mode to ", Mode.SECONDARY);
                switchMode(Mode.SECONDARY);
              }

            }
          } else {

            if (!primaryElectedOnThisConnection) {
              LOGGER.warn(LOG_FMT_1,
                  "Primary has observed a disconnection from zookeeper. Attempting to re-establish connection as Primary again.");

              // We were not elected as primary by this zookeeper connection. (Possible
              // zookeeper disconnection.)
              // Try to be primary.
              if (!tryPromoteToPrimary()) {
                // Being primary again failed. Stop publishing and die
                LOGGER.error(LOG_FMT_1, "Another instance has selected as primary while reconnecting to zookeeper. Exiting.");
                die();
              }

              // Even if we were able to become primary, another instance may have been
              // primary in the meantime and died.
              final String lastPrimary = getLastPrimaryName();
              if (!getInstanceId().equals(lastPrimary)) {
                LOGGER.error(LOG_FMT_2,
                    "Another instance has selected as primary and exited while reconnecting to zookeeper. Exiting. LastPrimary: ",
                    lastPrimary);
                die();
              }

              primaryElectedOnThisConnection = true;
            }

            // Otherwise keep the connection alive and continue working.
            keepAlive();
          }

          listener.process();
          Thread.sleep(900);
        }
      } catch (InterruptedException e) {
        try {
          connection.close();
        } catch (Exception e1) {
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_FMT_1, "Interrupted during connection closing");
          }
        }

        LOGGER.info(LOG_FMT_1, "Stopping matching engine controller thread");
        return;
      } catch (Exception e) {
        LOGGER.warn(LOG_FMT_1, e.getMessage());
        try {
          // Close the current connection and try to re-establish connection.
          if (connection != null) {
            connection.close();
          }
          connection = null;
        } catch (Exception e2) {
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_FMT_2, "Exception during connection closing: ", e2.getMessage());
          }
        }
      }
    }
  }
}
