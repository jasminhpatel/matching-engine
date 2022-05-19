package com.solfini.matchengine.controller;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.util.LogLevel;
import com.solfini.util.StringUtil;

/**
 * The KafkaController class implements a controller that promote the matching engine to primary or secondary on startup based on a set
 * configuration, and then handle failover based on administrative message published on a Kafka topic.
 */
public class KafkaController extends Controller implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaController.class);

  private final Mode controllerMode;
  private final String topic;

  public KafkaController(final String instanceId, final String topic, final Mode mode,
      final OneToOneConcurrentArrayQueueCustom<Message> queue) {
    super(instanceId, Mode.NONE, queue);
    this.controllerMode = mode;
    this.topic = topic;
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
  public void run() {
    final Mode mode = getMode();
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_9, "Starting matching engine controller thread (Type: ", KafkaController.class.toString(), ", InstanceID: ",
          getInstanceId(), ", Topic: ", topic, ", StartMode: ", mode != null ? mode.toString() : "", ")");
      LOGGER.info(LOG_FMT_2, ">>> start Summary");
      LOGGER.info(LOG_FMT_2, ">>> start Time: ", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss());
      LOGGER.info(LOG_FMT_2, ">>> start InstanceId: ", Context.getInstanceId());
      LOGGER.info(LOG_FMT_2, ">>> start Mode: ", Context.getControllerMode().toString());
      LOGGER.info(LOG_FMT_2, ">>> LogLevel: ", LogLevel.getLevel());
      LOGGER.info(LOG_FMT_2, ">>> start isReplayFromFileEnabled: ", Context.isReplayFromFileEnabled());
      LOGGER.info(LOG_FMT_2, ">>> start getReplayFromFileLocation: ", Context.getReplayFromFileLocation());
      LOGGER.info(LOG_FMT_2, ">>> start isStateValidatorEnabled: ", Context.isStateValidatorEnabled());
      LOGGER.info(LOG_FMT_2, ">>> start isCollateralSwapEnabled: ", Context.isCollateralSwapEnabled());
      LOGGER.info(LOG_FMT_2, ">>> start isRejectDuplicateClorIdsEnabled: ", Context.isRejectDuplicateClorIdsEnabled());
      LOGGER.info(LOG_FMT_2, ">>> start isUseOrderPoolEnabled: ", Context.isUseOrderPoolEnabled());
      LOGGER.info(LOG_FMT_2, ">>> start isListenToIpcMarketData: ", Context.isListenToIpcMarketData());
      LOGGER.info(LOG_FMT_2, ">>> start isListenToKafkaMarketData: ", Context.isListenToKafkaMarketData());
    }

    if (getMode() != controllerMode) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "Switching mode to ", controllerMode != null ? controllerMode.toString() : "");
      }
      switchMode(controllerMode);
    }

    final KafkaCommandListener listener = new KafkaCommandListener(topic, this);
    listener.start();

    while (true) {
      try {
        listener.process();
        Thread.sleep(100);
      } catch (InterruptedException e) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Stopping matching engine controller thread");
        }
        return;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }
}
