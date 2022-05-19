package com.solfini.matchengine.controller;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.controller.UnifiedController;
import com.solfini.matchengine.message.controller.ModeControlMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.controller.MatchingEngineController;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class UnifiedControllerTest {

  private class ControllerThread {
    private final OneToOneConcurrentArrayQueueCustom<Message> queue;
    private final String instanceId;
    private Thread thread;
    private Mode mode = Mode.NONE;

    public ControllerThread(String instanceId) {
      this.instanceId = instanceId;
      queue = new OneToOneConcurrentArrayQueueCustom<>(1000, "ControllerThread");
    }

    public void start(Mode mode) {
      thread = new Thread(new UnifiedController(instanceId, PropertyReader.getProperty("CONTROLLER_ZOOKEEPER_LOCATION", "localhost:2181"),
          PropertyReader.getProperty("CONTROLLER_KAFKA_TOPIC", "matching-engine-control"), mode, queue));

      thread.start();
    }

    public void stop() {
      if (thread != null) {
        thread.interrupt();
        thread = null;
      }
    }

    public Message poll() {
      int attempts = 0;
      while (attempts < 50) {
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

    public void assertNoModeChange() {
      Assert.assertNull(poll());
    }

    public void assertModeChange(Mode mode) {
      Message message = poll();
      Assert.assertNotNull(message);
      Assert.assertEquals(this.mode, ((ModeControlMessage) message).getPreviousMode());
      Assert.assertEquals(mode, ((ModeControlMessage) message).getMode());
      this.mode = mode;
    }
  }

  private ArrayList<ControllerThread> threads = new ArrayList<>();

  private ControllerThread startMatchingEngine(String instanceId, Mode mode) {
    ControllerThread thread = new ControllerThread(instanceId);
    threads.add(thread);
    thread.start(mode);

    try {
      Thread.sleep(10_000);
    } catch (Exception e) {
    }

    return thread;
  }

  private void primary(String instanceId) {
    File file = new File(getClass().getClassLoader().getResource("UnifiedControllerTest.properties").getFile());
    MatchingEngineController.main(new String[] {"-c", file.getAbsolutePath(), "-i", instanceId, "--primary"});
  }

  @Before
  public void before() {
    File file = new File(getClass().getClassLoader().getResource("UnifiedControllerTest.properties").getFile());
    try {
      PropertyReader.initialize(new FileInputStream(file), null);
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }

    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
    }
  }

  @After
  public void after() {
    for (ControllerThread thread : threads) {
      thread.stop();
    }

    threads.clear();
  }

  @Test
  public void switchModeToPrimaryOnStartup() {
    ControllerThread me = startMatchingEngine("me", Mode.PRIMARY);
    me.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void switchModeToSecondaryOnStartup() {
    ControllerThread me = startMatchingEngine("me", Mode.SECONDARY);
    me.assertModeChange(Mode.SECONDARY);
  }

  @Test
  public void noModeSwitchingOnStartup() {
    ControllerThread me = startMatchingEngine("me", Mode.NONE);
    me.assertNoModeChange();
  }

  @Test
  public void failoverSecondaryToPrimary() {
    ControllerThread me = startMatchingEngine("me", Mode.SECONDARY);
    me.assertModeChange(Mode.SECONDARY);

    primary("me");
    me.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void failoverToPrimaryIgnoredWhenPrimary() {
    ControllerThread me = startMatchingEngine("me", Mode.PRIMARY);
    me.assertModeChange(Mode.PRIMARY);

    primary("me");
    me.assertNoModeChange();
  }

  public static void main(String[] args) {
    UnifiedControllerTest test = new UnifiedControllerTest();

    ControllerThread me = test.startMatchingEngine("me", Mode.PRIMARY);
    me.assertModeChange(Mode.PRIMARY);

    test.startMatchingEngine("me", Mode.PRIMARY);
  }
}
