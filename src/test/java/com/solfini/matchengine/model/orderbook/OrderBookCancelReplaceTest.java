package com.solfini.matchengine.model.orderbook;

import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.Order;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookCancelReplaceTest extends OrderBookTest {

  @Test
  public void createCancelReplaceOrder() {
    CancelReplaceOrder cancelReplaceOrder = createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY,
        createOrder(2, user, pair.getId(), 2301, 1000, Side.BUY, DAY));

    String toJson = cancelReplaceOrder.toJSON();
    String expectToJson = "{\"class\":\"CancelReplaceOrder\",\"sequenceNumber\":0,\"persistTime\":0,\"sourceSeqNum\":0,"
        + "\"sourceSendTime\":0,\"snapId\":0,\"kafkaRecordOffset\":0,\"securityId\":12,\"price\":1011,\"price_scale\":2,"
        + "\"qty\":500,\"qty_scale\":2,\"side\":\"BUY\",\"origOrderId\":1,\"secondaryOrderId\":0,\"cancelId\":3,"
        + "\"cancelPriority\":0,\"clOrdId\":\"ClOrdId\",\"senderCompIdCharArr\":\"null\",\"senderCompIdAsString\":\"null\","
        + "\"account\":\"18\",\"userId\":18,\"ordType\":\"LIMIT\",\"type\":0,\"priceInt\":1011,\"quantityLong\":500,"
        + "\"quantityOrigLong\":500,\"price2\":0,\"price2_scale\":0,\"qty2\":0,\"qty2_scale\":0,\"newOrderId\":0,"
        + "\"cancelId\":0,\"price2Int\":0,\"order\":2}";
    Assert.assertEquals(expectToJson, toJson);

    String toString = cancelReplaceOrder.toString();
    String expectToString = "CancelReplaceOrder [securityId=12, price=1011, price_scale=2, qty=500, qty_scale=2, price2=0, "
        + "price2_scale=0, qty2=0, qty2_scale=0, side=BUY, origOrderId=1, cancelId=3, cancelPriority=0, newOrderId=0, clOrdId=ClOrdId, "
        + "senderCompIdCharArr=null, senderCompIdAsString=null, account=18, userId=18, ordType=LIMIT, type=0, priceInt=1011, price2Int=0, "
        + "quantityLong=500, quantityOrigLong=500, secondaryOrderId=0, order=Order [securityId=12, orderId=2, origOrderId=0, price=2301, price_scale=2, "
        + "price2=0, price2_scale=0, qty=1000, qty_scale=2, side=BUY, orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=0, "
        + "clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, account=18, submitterId=0, ordType=LIMIT, type=0, priceInt=2301, quantityLong=1000, "
        + "quantityOrigLong=1000, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, "
        + "inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, isHidden=false, isLiquidation=false, isLastLook=false, "
        + "feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0], "
        + "inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0]";
    Assert.assertEquals(expectToString, toString);

    Assert.assertEquals(PayloadType.orderEntry, cancelReplaceOrder.getPayloadType());
    Assert.assertEquals(MessageType.CANCEL_ORDER, cancelReplaceOrder.getMessageType());
    Assert.assertEquals(0, cancelReplaceOrder.getCancelPriority());
  }

  // Add buy order
  // Cancel replace the buy order, assert cancel replace
  @Test
  public void cancelReplaceBuyOrderResting() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");

    assertMessages();
  }

  // Add sell order
  // Cancel replace the sell order, assert cancel replace
  @Test
  public void cancelReplaceSellOrderResting() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=2301, orderQty=1000, ordStatus=NEW");

    assertMessages();
  }


  // Add buy and sell orders to partially fill the buy order
  // Cancel replace the buy order, assert cancellation
  @Test
  public void cancelReplaceBuyOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");

    Order replacementOrder = createOrder(3, user, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(4, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");

    assertMessages();
  }

  // Add buy and sell orders to partially fill the sell order
  // Cancel replace the sell order, assert cancellation
  @Test
  public void cancelReplaceSellOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");

    Order replacementOrder = createOrder(3, user, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(4, 2, user, pair.getId(), 1011, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=2301, orderQty=1000, ordStatus=NEW");

    assertMessages();
  }

  // Add buy and sell orders to fill the buy order
  // Cancel replace the buy order, assert rejection
  @Test
  public void cancelReplaceBuyOrderFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");

    Order replacementOrder = createOrder(3, user, pair.getId(), 2301, 300, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(4, 1, user, pair.getId(), 1011, 300, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, cxlRejReason=UNKNOWN_ORDER");

    assertMessages();
  }

  // Add buy and sell orders to fill the sell order
  // Cancel replace the sell order, assert rejection
  @Test
  public void cancelReplaceSellOrderFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");

    Order replacementOrder = createOrder(3, user, pair.getId(), 2301, 300, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(4, 2, user, pair.getId(), 1011, 300, Side.SELL, DAY, replacementOrder));

    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, cxlRejReason=UNKNOWN_ORDER");

    assertMessages();
  }

  // Add 2 buy orders
  // Add sell order
  // Assert partial fills and fills
  // Add 2 sell orders
  // Add another sell order
  // Add buy order, assert fills
  // Cancel partially filled order, assert
  // Add cancel order for order already filled, assert business reject
  @Test
  public void cancelReplaceMatchedOrders() {

    System.out.println("Adding 2 buy orders");
    // Add 2 buy orders
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=NEW");

    System.out.println("Adding a sell order");
    // Add sell order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    // Assert partial fills and fills
    expectMessage("orderQty=800, leavesQty=300, lastQty=500, lastPx=1011, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderQty=500, leavesQty=0, lastQty=500, lastPx=1011, ordStatus=FILLED");
    expectMessage("orderQty=800, leavesQty=0, lastQty=300, lastPx=1010, cumQty=800, ordStatus=FILLED");
    expectMessage("orderQty=500, leavesQty=200, lastQty=300, lastPx=1010, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    System.out.println("Adding another 3 sell orders");
    // Add 2 sell orders
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1015, 200, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1015, 300, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    // Add another sell order
    orderBook.addOrder(createOrder(6, user, pair.getId(), 1015, 1800, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");

    System.out.println("Adding a buy order");
    // Add buy order, assert fills
    orderBook.addOrder(createOrder(7, user, pair.getId(), 5000, 1000, Side.BUY, DAY));
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=NEW");
    expectMessage("orderQty=1000, leavesQty=800, lastQty=200, lastPx=1015, cumQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderQty=200, leavesQty=0, lastQty=200, lastPx=1015, ordStatus=FILLED");
    expectMessage("orderQty=1000, leavesQty=500, lastQty=300, lastPx=1015, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderQty=300, leavesQty=0, lastQty=300, lastPx=1015, ordStatus=FILLED");
    expectMessage("orderQty=1000, leavesQty=0, lastQty=500, lastPx=1015, cumQty=1000, ordStatus=FILLED");
    expectMessage("orderQty=1800, leavesQty=1300, lastQty=500, lastPx=1015, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    System.out.println("Sending cancel replace for filled sell order");
    Order replacementOrder1 = createOrder(8, user, pair.getId(), 4000, 2000, Side.SELL, DAY);

    // Cancel replace partially filled order, assert
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(9, 6, user, pair.getId(), 1015, 1800, Side.SELL, DAY, replacementOrder1));
    expectMessage("ordStatus=PENDING_CANCEL");
    expectMessage("ordStatus=CANCELED");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    System.out.println("Sending cancel replace for partially filled buy order");
    Order replacementOrder2 = createOrder(10, user, pair.getId(), 200, 2000, Side.BUY, DAY);
    // Add cancel replace order for order already filled, assert business reject
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(11, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder2));
    expectMessage("ordStatus=PENDING_CANCEL");
    expectMessage("cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace unknown order, assert rejection
  @Test
  public void cancelReplaceUnknownOrder() {
    Order replacementOrder = createOrder(2, user, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace unknown order without order id, assert rejection
  @Test
  public void cancelReplaceUnknownOrderWithoutOrderId() {
    Order replacementOrder = createOrder(2, user, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=0, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=18, cxlRejReason=UNKNOWN_ORDER");
    expectMessage("orderId=2, account=18, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  // Cancel replace unknown order without price, assert rejection
  @Test
  public void cancelReplaceUnknownOrderWithoutPrice() {
    Order replacementOrder = createOrder(2, user, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 0, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order, assert rejection
  @Test
  public void cancelReplaceAnotherUsersBuyOrder1() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order, assert rejection
  @Test
  public void cancelReplaceAnotherUsersBuyOrder2() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user2, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, account=19, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order, assert rejection
  @Test
  public void cancelReplaceAnotherUsersSellOrder1() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order, assert rejection
  @Test
  public void cancelReplaceAnotherUsersSellOrder2() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user2, pair.getId(), 1011, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order without order id, assert rejection
  @Test
  public void cancelReplaceAnotherUsersBuyOrder1WithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("CancelRejectMessage", "orderId=2, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order without order id, assert rejection
  @Test
  public void cancelReplaceAnotherUsersBuyOrder2WithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 0, user2, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=0, account=19, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=19, cxlRejReason=UNKNOWN_ORDER");
    expectMessage("orderId=2, account=19, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  // Cancel replace another user's order without order id, assert rejection
  @Test
  public void cancelReplaceAnotherUsersSellOrder1WithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 0, user, pair.getId(), 1011, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order without order id, assert rejection
  @Test
  public void cancelReplaceAnotherUsersSellOrder2WithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 0, user2, pair.getId(), 1011, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("orderId=0, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=19, cxlRejReason=UNKNOWN_ORDER");
    expectMessage("orderId=2, account=19, ordType=LIMIT, side=SELL, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  // Cancel replace another user's order without price, assert rejection
  @Test
  public void cancelReplaceAnotherUsersBuyOrder1WithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 0, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order without price, assert rejection
  @Test
  public void cancelReplaceAnotherUsersBuyOrder2WithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.BUY, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user2, pair.getId(), 0, 500, Side.BUY, DAY, replacementOrder));

    expectMessage("orderId=1, account=19, ordType=LIMIT, side=BUY, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order without price, assert rejection
  @Test
  public void cancelReplaceAnotherUsersSellOrder1WithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 0, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("CancelRejectMessage", "orderId=1, account=18, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Cancel replace another user's order without price, assert rejection
  @Test
  public void cancelReplaceAnotherUsersSellOrder2WithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = createOrder(2, user2, pair.getId(), 2301, 1000, Side.SELL, DAY);
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user2, pair.getId(), 0, 500, Side.SELL, DAY, replacementOrder));

    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  private Order setClOrdId(final Order order, final String clOrdId) {
    order.setClOrdId(clOrdId);
    return order;
  }

  private CancelReplaceOrder setClOrdId(final CancelReplaceOrder order, final String clOrdId) {
    order.setClOrdId(clOrdId);
    return order;
  }

  private CancelReplaceOrder setForceAdd(final CancelReplaceOrder order) {
    order.setForceAddOrder(true);
    return order;
  }

  @Test
  public void cancelReplaceBuyOrderRestingByClientOrderId1() {
    orderBook.addOrder(setClOrdId(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O1"));
    orderBook.addOrder(setClOrdId(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O2"));
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = setClOrdId(createOrder(3, user, pair.getId(), 2301, 1000, Side.BUY, DAY), "U1O3");
    orderBook.cancelReplaceOrder(setClOrdId(createCancelReplaceOrder(4, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder), "U1O1"));

    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, clOrdId=U1O3, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  @Test
  public void cancelReplaceBuyOrderRestingByClientOrderId2() {
    orderBook.addOrder(setClOrdId(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O1"));
    orderBook.addOrder(setClOrdId(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O2"));
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = setClOrdId(createOrder(3, user, pair.getId(), 2301, 1000, Side.BUY, DAY), "U1O3");
    orderBook.cancelReplaceOrder(setClOrdId(createCancelReplaceOrder(4, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder), "U1O2"));

    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, clOrdId=U1O3, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  @Test
  public void cancelReplaceBuyOrderRestingByClientOrderIdReuse1() {
    orderBook.addOrder(setClOrdId(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O1"));
    orderBook.addOrder(setClOrdId(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O2"));
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = setClOrdId(createOrder(3, user, pair.getId(), 2301, 1000, Side.BUY, DAY), "U1O1");
    orderBook.cancelReplaceOrder(setClOrdId(createCancelReplaceOrder(4, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder), "U1O1"));

    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  @Test
  public void cancelReplaceBuyOrderRestingByClientOrderIdReuse2() {
    orderBook.addOrder(setClOrdId(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O1"));
    orderBook.addOrder(setClOrdId(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O2"));
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = setClOrdId(createOrder(3, user, pair.getId(), 2301, 1000, Side.BUY, DAY), "U1O2");
    orderBook.cancelReplaceOrder(setClOrdId(createCancelReplaceOrder(4, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder), "U1O2"));

    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  @Ignore
  @Test
  public void cancelReplaceBuyOrderRestingByClientOrderIdInvalid() {
    orderBook.addOrder(setClOrdId(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O1"));
    orderBook.addOrder(setClOrdId(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O2"));
    expectMessage("orderId=1, clOrdId=U1O1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, clOrdId=U1O2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Order replacementOrder = setClOrdId(createOrder(3, user, pair.getId(), 2301, 1000, Side.BUY, DAY), "U1O3");
    orderBook.cancelReplaceOrder(setForceAdd(setClOrdId(createCancelReplaceOrder(4, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY, replacementOrder), "U1O3")));

    expectMessage("orderId=0, clOrdId=U1O3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=18, cxlRejReason=UNKNOWN_ORDER");
    expectMessage("orderId=3, clOrdId=U1O3, ordType=LIMIT, side=BUY, price=2301, orderQty=1000, ordStatus=NEW");
    assertMessages();
  }

  @Ignore
  @Test
  public void cancelReplaceBuyOrderRestingByClientOrderIdMultiple() {
    int orderId = 1;
    int cancelId = 1;
    for (int i = 0; i < 10; i++) {
      orderBook.addOrder(setClOrdId(createOrder(orderId, user, pair.getId(), 1011, 500, Side.BUY, DAY), "U1O" + i));
      expectMessage("orderId=" + orderId + ", clOrdId=U1O" + i + ", ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
      orderId++;
    }

    for (int j = 0; j < 10; j++) {
      for (int i = 0; i < 10; i++) {
        Order replacementOrder = setClOrdId(createOrder(orderId, user, pair.getId(), 1012 + j, 500, Side.BUY, DAY), "U1O" + i);
        expectMessage("orderId=" + (orderId - 10) + ", clOrdId=U1O" + i + ", ordType=LIMIT, side=BUY, price=" + (1011 + j) + ", orderQty=500, ordStatus=PENDING_CANCEL");
        expectMessage("orderId=" + (orderId - 10) + ", clOrdId=U1O" + i + ", ordType=LIMIT, side=BUY, price=" + (1011 + j) + ", orderQty=500, ordStatus=CANCELED");

        orderBook.cancelReplaceOrder(setClOrdId(createCancelReplaceOrder(cancelId, 0, user, pair.getId(), 1011 + j, 500, Side.BUY, DAY, replacementOrder), "U1O" + i));
        expectMessage("orderId=" + orderId + ", clOrdId=U1O" + i + ", ordType=LIMIT, side=BUY, price=" + (1012 + j) + ", orderQty=500, ordStatus=NEW");

        orderId++;
        cancelId++;
      }
    }

    assertMessages();
  }
}
