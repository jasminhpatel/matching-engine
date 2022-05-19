package com.solfini.util;

import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import org.junit.Assert;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class ReportUtilTest {

  @Test
  public void messagePool() {
    ManyToManyConcurrentArrayQueueCustom<BalanceAdminMessage> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(1024, "BalanceAdminMessageObjectPool");
    pool.offer(new BalanceAdminMessage());
    pool.offer(new BalanceAdminMessage());

    List<Message> list = new ArrayList<Message>(4096);

    pool.drainTo(list, 100);

    Assert.assertEquals(2, list.size());
    Assert.assertEquals(1024, pool.capacity());

    Assert.assertEquals(list.get(0).toJSON(), new BalanceAdminMessage().toJSON());
    Assert.assertEquals(list.get(1).toJSON(), new BalanceAdminMessage().toJSON());

  }
}
