package com.solfini.matchengine.model.orderbook;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Test;
import com.solfini.user.User;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookChangeQueueTest extends OrderBookTest {

  // Add buy order, assert open orders
  @Test
  public void disableEnable() {
    User user = createUser(50);
    user.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);
    expectMessage("userId=50");

    orderBook.disableOutputQueue();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));

    assertMessages(); // assert empty

    orderBook.restoreOutputQueue();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

}
