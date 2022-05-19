package com.solfini.matchengine.controller;

import java.util.ArrayList;
import java.util.List;

import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.controller.ModeControlMessage;
import com.solfini.matchengine.message.controller.ShutdownControlMessage;
import com.solfini.report.ReportUtil;
import com.solfini.util.controller.KafkaControllerPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Controller class is the base class for the matching engine controller, responsible for making decisions on the mode of operation
 * (primary, secondary, etc) and handling failover.
 */
public abstract class Controller implements Runnable {

  // We use an slf4j logger here to make sure the terminating message is always written to the log before actual termination
  private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

  private final String instanceId;
  private final OneToOneConcurrentArrayQueueCustom<Message> queue;
  private final List<String> onTerminateCommands = new ArrayList<>();
  private Mode mode = Mode.NONE;

  public Controller(final String instanceId, final Mode mode, final OneToOneConcurrentArrayQueueCustom<Message> queue) {
    this.instanceId = instanceId;
    this.mode = mode;
    this.queue = queue;
  }

  public String getInstanceId() {
    return instanceId;
  }

  protected final OneToOneConcurrentArrayQueueCustom<Message> getQueue() {
    return queue;
  }

  protected final void switchMode(final Mode mode) {
    getQueue().add(new ModeControlMessage(getMode(), mode));
    this.mode = mode;
  }

  public void execute(final String command) {
  }

  public void shutdown() {
    getQueue().add(new ShutdownControlMessage(this));
  }

  public void status() {
    ReportUtil.logReport();
  }

  public void onTerminate(final String command) {
    onTerminateCommands.add(command);
  }

  public void terminate() {
    if (!onTerminateCommands.isEmpty()) {
      LOGGER.info("Running terminating actions");
      KafkaControllerPublisher publisher = new KafkaControllerPublisher();
      for (final String command : onTerminateCommands) {
        LOGGER.info("Queue terminating action - {}", command);
        publisher.send(command);
      }
    }

    LOGGER.info("Terminating this instance.");
    System.exit(0);
  }

  public final Mode getMode() {
    return mode;
  }
}
