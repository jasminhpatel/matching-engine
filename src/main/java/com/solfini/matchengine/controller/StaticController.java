package com.solfini.matchengine.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.util.LogLevel;

/**
 * The StaticController class implements a controller that promote the matching engine to primary or secondary on startup based on a set
 * configuration.
 */
public class StaticController extends Controller {

  private static final Logger LOGGER = LoggerFactory.getLogger(StaticController.class);
  private final Mode mode;

  public StaticController(final String instanceId, final Mode mode, final OneToOneConcurrentArrayQueueCustom<Message> controlQueue) {
    super(instanceId, Mode.NONE, controlQueue);
    this.mode = mode;
  }

  @Override
  public void execute(final String command) {
    LOGGER.warn("Unsupported command: {}", command);
  }

  @Override
  public void run() {
    if (LogLevel.info()) {
      LOGGER.info("Starting matching engine controller thread (Type: {}, StartMode: {})", StaticController.class, getMode());
    }

    if (getMode() != mode) {
      if (LogLevel.info()) {
        LOGGER.info("Switching mode to {}", mode);
      }
      switchMode(mode);
    }

    while (true) {
      try {
        Thread.sleep(10_000);
        if (LogLevel.info()) {
          LOGGER.info("Matching engine controller thread is alive");
        }
      } catch (InterruptedException e) {
        if (LogLevel.info()) {
          LOGGER.info("Stopping matching engine controller thread");
        }
        return;
      }
    }
  }
}
