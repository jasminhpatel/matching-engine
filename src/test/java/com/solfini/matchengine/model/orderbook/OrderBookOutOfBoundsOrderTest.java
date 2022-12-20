package com.solfini.matchengine.model.orderbook;

import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.user.User;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import java.util.Properties;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookOutOfBoundsOrderTest extends OrderBookTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "10");
  }

  // Out of bound limit buy orders are rejected.
  @Test
  public void limitBuyOrderRejected() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=REJECTED");
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=PRICE_IS_OUT_OF_BOUNDS, text=Price is out of bounds");
    assertMessages();
  }

  // Out of bound limit sell orders are rejected.
  @Ignore
  public void limitSellOrderRejected() {
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=REJECTED");
    expectMessage("BusinessRejectMessage", "orderId=2, businessRejectReason=PRICE_IS_OUT_OF_BOUNDS, text=Price is out of bounds");
    assertMessages();
  }

  // Out of bound stop limit buy orders are rejected.
  @Test
  public void stopLimitBuyOrderRejected() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1011, 1015, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=REJECTED");
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=PRICE_IS_OUT_OF_BOUNDS, text=Price is out of bounds");
    assertMessages();
  }

  // Out of bound stop limit sell orders are rejected.
  @Ignore
  public void stopLimitSellOrderAccepted() {
    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1011, 1005, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=REJECTED");
    expectMessage("BusinessRejectMessage", "orderId=2, businessRejectReason=PRICE_IS_OUT_OF_BOUNDS, text=Price is out of bounds");
    assertMessages();
  }

  // Add orders in such a way that they are being added to the outOfBoundsOrderMap.
  // Cancel one of them
  // Order cancel should happen without any error
  @Test
  @Ignore
  public void cancelOrderFromOutOfBoundsOrderMap() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    Order cancelingOrder = createOrder(5, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    orderBook.addOrder(cancelingOrder);
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(5, 5, user, pair.getId(), 1011, 500, Side.SELL, DAY)); // ARRAY INDEX OUT OF BOUNDS HERE!!
    expectMessage("orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderQty=500, ordStatus=CANCELED");

    assertMessages();
  }

  @Test
  @Ignore
  public void cancelReplaceFromOutOfBoundsOrderMap() {
    Order newOrder = createOrder(5, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    orderBook.addOrder(newOrder);
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    Order amendingOrder = createOrder(5, user, pair.getId(), 1011, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(5, 5, user, pair.getId(), 1011, 500, Side.SELL, DAY, amendingOrder)); // Array
                                                                                                                                // index out
                                                                                                                                // of
                                                                                                                                // bounds!!
    expectMessage("orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderQty=500, ordStatus=CANCELED");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();
  }

  @Test
  @Ignore
  public void cancelReplaceFromOutOfBoundsOrderMapToBookArray() {
    Order newOrder = createOrder(5, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    orderBook.addOrder(newOrder);
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    Order amendingOrder = createOrder(5, user, pair.getId(), 2, 500, Side.SELL, DAY);

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(5, 5, user, pair.getId(), 1011, 500, Side.SELL, DAY, amendingOrder)); // Array
                                                                                                                                // index out
                                                                                                                                // of
                                                                                                                                // bounds!!
    expectMessage("orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderQty=500, ordStatus=CANCELED");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    assertMessages();
  }

  @Test
  @Ignore
  public void cancelReplaceFromOutOfBoundsOrderMapToNewPositionInMap() {
    Order newOrder = createOrder(5, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    orderBook.addOrder(newOrder);
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    Order amendingOrder = createOrder(5, user, pair.getId(), 1013, 500, Side.SELL, DAY);

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(5, 5, user, pair.getId(), 1011, 500, Side.SELL, DAY, amendingOrder)); // Array
                                                                                                                                // index out
                                                                                                                                // of
                                                                                                                                // bounds!!
    expectMessage("orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderQty=500, ordStatus=CANCELED");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    assertMessages();
  }

  @Test
  @Ignore
  public void cancelReplaceToOutOfBoundsOrderMapFromBookArray() {
    Order newOrder = createOrder(5, user, pair.getId(), 5, 500, Side.SELL, DAY);
    orderBook.addOrder(newOrder);
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    Order amendingOrder = createOrder(5, user, pair.getId(), 1023, 500, Side.SELL, DAY);

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(5, 5, user, pair.getId(), 5, 500, Side.SELL, DAY, amendingOrder)); // Array index
                                                                                                                             // out of
                                                                                                                             // bounds!!
    expectMessage("orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderQty=500, ordStatus=CANCELED");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    assertMessages();
  }

  @Test
  @Ignore
  public void restateOutOfBoundOrders() {
    Order outOfBoundOrder = createOrder(5, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    Order bookArrayOrder = createOrder(6, user, pair.getId(), 5, 500, Side.SELL, DAY);
    Order restateOrder = createOrder(7, user, pair.getId(), 5, 500, Side.SELL, DAY);

    orderBook.addOrder(outOfBoundOrder);
    orderBook.addOrder(bookArrayOrder);

    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    pair.changeState(MarketStatus.RESTATE, 0, restateOrder);

    expectMessage("execType=RESTATED, ordType=PREVIOUSLY_INDICATED");
    expectMessage("orderId=6, execType=RESTATED, ordStatus=NEW");
    expectMessage("orderId=5, execType=RESTATED, ordStatus=NEW");

    assertMessages();

  }

  @Test
  @Ignore
  public void updateRiskOnOutOfBoundsOrders() {

    User newUser = createUser(28);
    newUser.addPosition(pair.getId(), 10_000, null, 0, TokenType.ERC20);
    expectMessage("userId=28");

    newUser.setUsdValue(0.0); // set USD value to zero

    Assert.assertEquals(0.0, newUser.getUsdValue(), 0);
    orderBook.addOrder(createOrder(5, newUser, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    Assert.assertEquals(10000, newUser.getUsdValue(), 0.001);

    assertMessages();
  }

  // Cancel sell order of a different user providing all attributes, assert rejection
  @Test
  @Ignore
  public void cancelAnotherUsersSellOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user2, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
  }

  // Cancel sell order of a different user without providing order id, assert rejection
  @Test
  @Ignore
  public void cancelAnotherUsersSellOrderWithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 0, user2, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=0, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
  }

  // Cancel sell order of a different user without providing price, assert rejection
  @Test
  @Ignore
  public void cancelAnotherUserSellOrderWithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user2, pair.getId(), 0, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
  }

  // Cancel replace sell order of a different user, assert rejection
  @Test
  @Ignore
  public void cancelReplaceSellOrderFromAnotherUser1() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order order = createOrder(2, user, pair.getId(), 1011, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user2, pair.getId(), 1011, 500, Side.SELL, DAY, order));
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
  }

  // Cancel replace sell order of a different user, assert rejection
  @Test
  @Ignore
  public void cancelReplaceSellOrderFromAnotherUser2() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order order = createOrder(2, user2, pair.getId(), 1011, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user2, pair.getId(), 1011, 500, Side.SELL, DAY, order));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
  }

}
