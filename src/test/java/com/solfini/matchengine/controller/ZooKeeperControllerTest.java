package com.solfini.matchengine.controller;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.controller.ZooKeeperController;
import com.solfini.matchengine.message.controller.ModeControlMessage;
import com.solfini.util.PropertyReader;

import java.util.ArrayList;

public class ZooKeeperControllerTest {

  private static final String ZK_CONNECT_STRING = "localhost";
  private final String KEY_PATH = buildKeyPath();

  private static String buildKeyPath() {
    return "/com.solfini.matchengine.primary." + System.nanoTime();
  }

  private class ControllerThread {
    private final OneToOneConcurrentArrayQueueCustom<Message> queue;
    private final String instanceId;
    private Thread thread;
    private Mode mode;
    private final long priority;
    private final long sessionTimeoutSec;
    private final long connectionTimeoutSec;

    public ControllerThread(String instanceId, long priority, long sessionTimeoutSec, long connectionTimeoutSec) {
      this.instanceId = instanceId;
      this.priority = priority;
      this.sessionTimeoutSec = sessionTimeoutSec;
      this.connectionTimeoutSec = connectionTimeoutSec;
      queue = new OneToOneConcurrentArrayQueueCustom<>(1000, "ControllerThread");
    }

    public void start() {
      mode = Mode.NONE;
      thread = new Thread(new ZooKeeperController(instanceId, ZK_CONNECT_STRING, KEY_PATH, priority, sessionTimeoutSec,
          connectionTimeoutSec, PropertyReader.getProperty("CONTROLLER_KAFKA_TOPIC", "matching-engine-control"),
          queue));
      thread.start();
    }

    public void stop() {
      if (thread != null) {
        mode = Mode.NONE;
        thread.interrupt();
        thread = null;
      }
    }

    public Message poll() {
      int attempts = 0;
      while (attempts < 500) {
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

    public void assertModeChange(Mode mode) {
      Message message = poll();
      Assert.assertNotNull(message);
      Assert.assertEquals(this.mode, ((ModeControlMessage) message).getPreviousMode());
      Assert.assertEquals(mode, ((ModeControlMessage) message).getMode());
      this.mode = mode;
    }
  }

  private ArrayList<ControllerThread> threads = new ArrayList<>();

  private ControllerThread startMatchingEngine(String instanceId, long priority, long sessionTimeoutSec, long connectionTimeoutSec) {
    ControllerThread thread = new ControllerThread(instanceId, priority, sessionTimeoutSec, connectionTimeoutSec);
    threads.add(thread);
    thread.start();

    return thread;
  }

  private ControllerThread startMatchingEngine(String instanceId, long priority) {
    return startMatchingEngine(instanceId, priority, 30, 30);
  }

  @After
  public void after() {
    for (ControllerThread thread : threads) {
      thread.stop();
    }

    threads.clear();
  }

  @Test
  public void firstInstanceElectedPrimary() {
    ControllerThread me1 = startMatchingEngine("me1", 0);
    me1.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void followingInstancesElectedSecondaryWithSamePriority() {
    ControllerThread me1 = startMatchingEngine("me1", 0);
    me1.assertModeChange(Mode.PRIMARY);

    for (int i = 2; i < 5; ++i) {
      ControllerThread me = startMatchingEngine("me" + i, 0);
      me.assertModeChange(Mode.SECONDARY);
    }
  }

  @Test
  public void followingInstancesElectedSecondaryWithHighPriority() {
    ControllerThread me1 = startMatchingEngine("me1", 1);
    me1.assertModeChange(Mode.PRIMARY);

    for (int i = 2; i < 5; ++i) {
      ControllerThread me = startMatchingEngine("me" + i, i);
      me.assertModeChange(Mode.SECONDARY);
    }
  }

  @Test
  public void followingInstancesElectedSecondaryWithLowerPriority() {
    ControllerThread me1 = startMatchingEngine("me1", 100);
    me1.assertModeChange(Mode.PRIMARY);

    for (int i = 2; i < 5; ++i) {
      ControllerThread me = startMatchingEngine("me" + i, i);
      me.assertModeChange(Mode.SECONDARY);
    }
  }

  @Test
  public void secondaryTakesOverOnPrimaryFailureSamePriority() {
    ControllerThread me1 = startMatchingEngine("me1", 0);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 0);
    me2.assertModeChange(Mode.SECONDARY);

    me1.stop();
    me2.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void secondaryTakesOverOnPrimaryFailureHighPriority() {
    ControllerThread me1 = startMatchingEngine("me1", 0);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 1);
    me2.assertModeChange(Mode.SECONDARY);

    me1.stop();
    me2.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void secondaryTakesOverOnPrimaryFailureLowPriority() {
    ControllerThread me1 = startMatchingEngine("me1", 1);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 0);
    me2.assertModeChange(Mode.SECONDARY);

    me1.stop();
    me2.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void restartingFailedPrimaryBecomesSecondary() {
    ControllerThread me1 = startMatchingEngine("me1", 0);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 0);
    me2.assertModeChange(Mode.SECONDARY);

    me1.stop();
    me2.assertModeChange(Mode.PRIMARY);

    me1.start();
    me1.assertModeChange(Mode.SECONDARY);
  }

  @Test
  public void nextHighestPriorityInstanceTakesOverOnPrimaryFailure() {
    ControllerThread me1 = startMatchingEngine("me1", 1);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 2);
    me2.assertModeChange(Mode.SECONDARY);

    ControllerThread me3 = startMatchingEngine("me3", 3);
    me3.assertModeChange(Mode.SECONDARY);

    ControllerThread me4 = startMatchingEngine("me4", 4);
    me4.assertModeChange(Mode.SECONDARY);

    me1.stop();
    me4.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void restartingSameInstanceShouldBeSecondaryAfterFailover() {
    ControllerThread me1 = startMatchingEngine("me1", 1);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 2);
    me2.assertModeChange(Mode.SECONDARY);

    me1.stop();
    me2.assertModeChange(Mode.PRIMARY);

    // Restart with same id
    me1 = startMatchingEngine("me1", 1);
    me1.assertModeChange(Mode.SECONDARY);

    me2.stop();
    me1.assertModeChange(Mode.PRIMARY);
  }

  @Test
  public void failoverAfterRunningPrimaryForAWhile() {
    ControllerThread me1 = startMatchingEngine("me1", 0, 10, 30);
    me1.assertModeChange(Mode.PRIMARY);

    ControllerThread me2 = startMatchingEngine("me2", 0, 10, 30);
    me2.assertModeChange(Mode.SECONDARY);

    try {
      Thread.sleep(20_000);
    } catch (Exception e) {
    }

    me1.stop();
    me2.assertModeChange(Mode.PRIMARY);
  }
}
