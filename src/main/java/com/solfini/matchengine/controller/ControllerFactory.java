package com.solfini.matchengine.controller;

import com.solfini.common.Context;
import com.solfini.util.PropertyReader;

/**
 * The ControllerFactory class creates an instance of a controller based on the configuration details specified
 * in the config.properties file.
 *
 * Use the CONTROLLER_TYPE parameter to select the type of the controller.
 *
 * Static:
 *    CONTROLLER_TYPE=static
 *    CONTROLLER_MODE=[none|primary|secondary|suspended]  ; Optional, defaults to none, sets the mode at startup
 *
 * ZooKeeper:
 *    INSTANCE_ID=<instance>  ; Unique identifier for the matching engine instance
 *    CONTROLLER_TYPE=zookeeper
 *    CONTROLLER_ZOOKEEPER_LOCATION=<zookeeper-connect-string>  ; Connection string to use for ZooKeeper connection
 *    CONTROLLER_ZOOKEEPER_PRIORITY=<value>  ; Priority value for the instance. Upon a failover, highest instances with
 *        higher priority will be elected as the primary. (default value : 0)
 *    CONTROLLER_ZOOKEEPER_SESSION_TIMEOUT=<value>   ; Timeout value for the zookeeper session. All ephemeral nodes created
 *        will timeout when there is no heartbeat within this time period.  (default value : 120)
 *    CONTROLLER_ZOOKEEPER_CONNECTION_TIMEOUT=<value> ; Timeout value to establish a connection with Zookeeper. If unable
 *        to connect within this time period, ME will terminate. Setting this value to 0 will wait indefinitely to establish
 *        connection. (default value : 60)
 *
 * Kafka:
 *    INSTANCE_ID=<instance>  ; Unique identifier for the matching engine instance
 *    CONTROLLER_TYPE=kafka
 *    CONTROLLER_MODE=[none|primary|secondary|suspended]  ; Optional, defaults to none, sets the mode at startup
 *    CONTROLLER_KAFKA_TOPIC=<kafka-topic>  ; Kafka topic to process for control messages
 *
 * Unified:
 *    INSTANCE_ID=<instance>  ; Unique identifier for the matching engine instance
 *    CONTROLLER_TYPE=unified
 *    CONTROLLER_MODE=[none|primary|secondary|suspended]  ; Optional, defaults to none, sets the mode at startup
 *    CONTROLLER_ZOOKEEPER_LOCATION=<zookeeper-connect-string>  ; Connection string to use for ZooKeeper connection
 *    CONTROLLER_KAFKA_TOPIC=<kafka-topic>  ; Kafka topic to process for control messages
 */
public final class ControllerFactory {

  private ControllerFactory() {
    throw new UnsupportedOperationException();
  }

  public static Controller create() {
    final String controllerType = PropertyReader.getProperty("CONTROLLER_TYPE", "static").toLowerCase();
    final String instanceId = PropertyReader.getProperty("INSTANCE_ID", "me01");
    final String controllerZookeeperLocation = PropertyReader.getProperty("CONTROLLER_ZOOKEEPER_LOCATION", "localhost");
    final String controllerKafkaTopic = PropertyReader.getProperty("CONTROLLER_KAFKA_TOPIC", "matching-engine-control");
    final Mode mode = Mode.valueOf(PropertyReader.getProperty("CONTROLLER_MODE", "none").trim().toUpperCase());

    if (controllerType.equals("kafka")) {
      return new KafkaController(
        instanceId,
        controllerKafkaTopic,
        mode,
        Context.getControlQueue());
    }

    if (controllerType.equals("zookeeper")) {
      return new ZooKeeperController(
        instanceId,
        controllerZookeeperLocation,
        PropertyReader.getProperty("CONTROLLER_ZOOKEEPER_PRIORITY", 0),
        PropertyReader.getProperty("CONTROLLER_ZOOKEEPER_SESSION_TIMEOUT", 120),
        PropertyReader.getProperty("CONTROLLER_ZOOKEEPER_CONNECTION_TIMEOUT", 60),
        controllerKafkaTopic,
        Context.getControlQueue());
    }

    if (controllerType.equals("unified")) {
      return new UnifiedController(
        instanceId,
        controllerZookeeperLocation,
        controllerKafkaTopic,
        mode,
        Context.getControlQueue());
    }

    return new StaticController(instanceId, mode, Context.getControlQueue());
  }
}
