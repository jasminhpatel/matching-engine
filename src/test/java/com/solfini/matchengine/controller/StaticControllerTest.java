package com.solfini.matchengine.controller;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.solfini.matchengine.controller.StaticController;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.message.controller.ModeControlMessage;
import com.solfini.matchengine.message.controller.ShutdownControlMessage;

public class StaticControllerTest {

  private OneToOneConcurrentArrayQueueCustom<Message> queue;
  private Controller controller;
  private Thread thread;

  @Before
  public void before() {
    queue = new OneToOneConcurrentArrayQueueCustom<>(1000, "StaticControllerTest");
  }

  @After
  public void after() {
    thread.interrupt();
    thread = null;
    queue = null;
    controller = null;
  }

  private Message poll() {
    int attempts = 0;
    while (attempts < 10) {
      try {
        Message message = queue.remove();
        if (message != null) {
          return message;
        }
      } catch (Exception e) {
      }

      try {
        Thread.sleep(100);
        ++attempts;
      } catch (InterruptedException e) {
      }
    }

    return null;
  }

  @Test
  public void switchModeToPrimaryOnStartup() {
    controller = new StaticController("me01", Mode.PRIMARY, queue);
    thread = new Thread(controller);
    thread.start();

    Message message = poll();
    Assert.assertNotNull(message);
    Assert.assertTrue(message instanceof ModeControlMessage);
    Assert.assertEquals(Mode.NONE, ((ModeControlMessage) message).getPreviousMode());
    Assert.assertEquals(Mode.PRIMARY, ((ModeControlMessage) message).getMode());
  }

  @Test
  public void switchModeToSecondaryOnStartup() {
    controller = new StaticController("me01", Mode.SECONDARY, queue);
    thread = new Thread(controller);
    thread.start();

    Message message = poll();
    Assert.assertNotNull(message);
    Assert.assertTrue(message instanceof ModeControlMessage);
    Assert.assertEquals(Mode.NONE, ((ModeControlMessage) message).getPreviousMode());
    Assert.assertEquals(Mode.SECONDARY, ((ModeControlMessage) message).getMode());
  }

  @Test
  public void noModeSwitchingOnStartup() {
    controller = new StaticController("me01", Mode.NONE, queue);
    thread = new Thread(controller);
    thread.start();

    Message message = poll();
    Assert.assertNull(message);
  }

  @Test
  public void shutdownPrimary() {
    switchModeToPrimaryOnStartup();
    controller.shutdown();

    Message message = poll();
    Assert.assertNotNull(message);
    Assert.assertTrue(message instanceof ShutdownControlMessage);
  }

  @Test
  public void shutdownSecondary() {
    switchModeToSecondaryOnStartup();
    controller.shutdown();

    Message message = poll();
    Assert.assertNotNull(message);
    Assert.assertTrue(message instanceof ShutdownControlMessage);
  }
}
