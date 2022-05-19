package com.solfini.binance.orderbook;

import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;

public class BinancePreOrderCheckTest extends BinanceOrderBookTest {

  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "2000000");
  }

  // Add buy order
  @Test
  public void addBuyOrder() {
    User user = createUser(1015);
    expectMessage("userId=1015");

    user.addPosition(USDT, 99982205_07280441L);
    Assert.assertEquals(99982205_07280441L, user.getPosition(USDT).getQuantity());
    Assert.assertEquals(99982205_07280441L, user.getPosition(USDT).getAvailableQuantity());

    pair.setIndexFeedUsdMark(11812.940000000002);

    orderBook.addOrder(createOrder(1001, user, pair.getId(), 11770_00, 2, 1_000, 3, Side.BUY, DAY));
    expectMessage("orderId=1001, ordType=LIMIT, side=BUY, price=1177000, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  // Add sell order
  @Test
  public void addSellOrder() {
    User user = createUser(1016);
    expectMessage("userId=1016");

    user.addPosition(USDT, 99982205_07280441L);
    Assert.assertEquals(99982205_07280441L, user.getPosition(USDT).getQuantity());
    Assert.assertEquals(99982205_07280441L, user.getPosition(USDT).getAvailableQuantity());

    pair.setIndexFeedUsdMark(11812.940000000002);

    orderBook.addOrder(createOrder(1002, user, pair.getId(), 11870_00, 2, 1_000, 3, Side.SELL, DAY));
    expectMessage("orderId=1002, ordType=LIMIT, side=SELL, price=1187000, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }
}
