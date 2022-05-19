package com.solfini.matchengine.util.order;

import com.solfini.common.Constants;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class OrderGenerationUtil {


  public static Order generateNewOrder(int orderId, int securityId, DecimalFloat price, DecimalFloat qty, Side side) {
    Order order = OrderObjectPool.get();
    order.setOrderId(orderId);
    order.setAccount(1);
    order.setClOrdId("ClOrdId");
    order.setOrdType(OrdType.LIMIT);
    order.setSecurityId(securityId);
    order.setSide(side);
    order.setPrice(price.value(), (short) price.scale());
    order.setQty(qty.value(), (short) qty.scale());
    order.setQuantityLong(qty.value() * 100);
    order.setPriceInt((int) price.value());
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  public static CancelOrder generateCancelOrder(int cancelId, Order order) {
    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.setCancelId(cancelId);
    cancelOrder.setOrigOrderId(order.getOrderId());
    cancelOrder.setAccount(1);
    cancelOrder.setClOrdId(order.getClOrdId());
    cancelOrder.setOrdType(order.getOrdType());
    cancelOrder.setSecurityId(order.getSecurityId());
    cancelOrder.setSide(order.getSide());
    cancelOrder.setPrice(order.getPrice(), order.getPriceScale());
    cancelOrder.setQty(order.getQty(), order.getQtyScale());

    return cancelOrder;
  }

  public static CancelOrder generateCancelOrder(int cancelId, int origOrderId, int securityId, DecimalFloat price, DecimalFloat qty,
      Side side) {
    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.setCancelId(cancelId);
    cancelOrder.setOrigOrderId(origOrderId);
    cancelOrder.setAccount(1);
    cancelOrder.setClOrdId("ClOrdId");
    cancelOrder.setOrdType(OrdType.LIMIT);
    cancelOrder.setSecurityId(securityId);
    cancelOrder.setSide(side);
    cancelOrder.setPrice(price.value(), (short) price.scale());
    cancelOrder.setQty(qty.value(), (short) qty.scale());

    return cancelOrder;
  }


}
