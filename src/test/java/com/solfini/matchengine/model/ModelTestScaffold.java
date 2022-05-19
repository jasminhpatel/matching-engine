package com.solfini.matchengine.model;

import com.solfini.common.Constants;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;

public class ModelTestScaffold implements Constants {

  public static final int USDT = 1;
  public static final int USDC = 1;
  public static final int ETH = 2;
  public static final int BTC = 3;
  public static final int ETH_USDT_F = 9;
  public static final int BTC_USDT = 8;
  public static final int BTC_USDC = 8;
  public static final int BTC_USDT_F = 12;
  public static final int BTC_USDC_F = 12;
  public static final int BTC_USDT_DF = 13;
  public static final int BTC_USDC_DF = 13;
  public static final int BTC_USDT_CALL_6000 = 14;
  public static final int BTC_USDT_PUT_6000 = 15;
  public static final int BTC_USDT_CALL_NOW_6000 = 20;
  public static final int BTC_USDT_PUT_NOW_6000 = 21;
  public static final int BTC_USDC_CALL_6000 = 14;
  public static final int BTC_USDC_PUT_6000 = 15;
  public static final int BTC_USDC_CALL_NOW_6000 = 20;
  public static final int BTC_USDC_PUT_NOW_6000 = 21;

  public static final int BTC_USDT_PUT_9000 = 150;
  public static final int BTC_USDC_PUT_9000 = 150;

  public static final int OFFSET = 19;

  protected SecurityDefinitionAdminMessage createInstrumentDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int priceScale, final int quantityScale) {

    SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage();
    message.setUpdateType(updateType);
    message.setAssetType(AssetType.ASSET);
    message.setSymbol(symbol);
    message.setName(symbol);
    message.setSecurityId(securityId);
    message.setQuantityScale((short) quantityScale);
    message.setPriceScale((short) priceScale);
    message.setOrderBookStrategy(ARRAY_ORDER_BOOK);
    message.setPreOrderCheckStrategy(CASH_PREORDER_CHECK);

    return message;
  }

  protected SecurityDefinitionAdminMessage createInstrumentPairDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int baseId, final int quotedId, final int priceScale, final int quantityScale) {
    return createInstrumentPairDefinition(securityId, updateType, symbol, baseId, quotedId, priceScale, quantityScale,
        MARGIN_PREORDER_CHECK);
  }

  protected SecurityDefinitionAdminMessage createInstrumentPairDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int baseId, final int quotedId, final int priceScale, final int quantityScale,
      final int preOrderCheckStrategy) {

    SecurityDefinitionAdminMessage message = createInstrumentDefinition(securityId, updateType, symbol, priceScale, quantityScale);
    message.setAssetType(preOrderCheckStrategy == CASH_PREORDER_CHECK ? AssetType.PAIR : AssetType.PERPETUAL_SWAP);
    message.setPreOrderCheckStrategy(preOrderCheckStrategy);
    message.setBaseId(baseId);
    message.setQuotedId(quotedId);

    return message;
  }

  protected User createUser(final int userId, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    message.setUsername("user_" + userId);
    message.setPassword("pass_" + userId);
    message.setFeeTier(0);
    message.setUserType(User.TRADER);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }

  protected Order createOrder(final int orderId, final User user, final int securityId, final long price, final long quantity,
      final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  protected Order createMarketOrder(final int orderId, final User user, final int securityId, final long quantity, final Side side,
      final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.MARKET, securityId, 0, quantity, side, timeInForce);
    order.setOrdType(OrdType.MARKET);
    if (side == Side.SELL)
      order.setType(Constants.SELL_MARKET);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_MARKET);

    return order;
  }

  protected Order createStopLimitOrder(final int orderId, final User user, final int securityId, final long price, final long stopPx,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.STOP_LIMIT, securityId, price, quantity, side, timeInForce);
    order.setStopPx(stopPx, (short) 2);
    order.setStopPxInt((int) stopPx);
    if (side == Side.SELL)
      order.setType(Constants.STOP_SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.STOP_BUY_LIMIT);

    return order;
  }

  private Order createOrder(final int orderId, final User user, final OrdType orderType, final int securityId, final long price,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    final int priceScale = InstrumentCache.getPair(securityId) == null ? 2 : InstrumentCache.getPair(securityId).getPriceScale();
    final int quantityScale = InstrumentCache.getPair(securityId) == null ? 2 : InstrumentCache.getPair(securityId).getQuantityScale();
    Order order = OrderObjectPool.get();
    order.setOrderId(orderId);
    order.setUser(user);
    order.setAccount(user == null ? 0 : user.getId());
    order.setClOrdId("ClOrdId");
    order.setOrdType(orderType);
    order.setSecurityId(securityId);
    order.setSide(side);
    order.setPrice(price, (short) 2);
    order.setPriceInt((int) scale(price, 2, priceScale));
    order.setQty(quantity, (short) 2);
    order.setQuantityLong(scale(quantity, 2, quantityScale));
    order.setQuantityOrigLong(scale(quantity, 2, quantityScale));
    order.setTimeInForce(timeInForce);

    return order;
  }

  private static long scale(final long value, final int scale1, final int scale2) {
    long result = value;
    for (int i = 0; i < Math.abs(scale1 - scale2); i++) {
      if (scale1 > scale2)
        result /= 10;
      else if (scale2 > scale1)
        result *= 10;
    }
    return result;
  }

  protected static CancelOrder createCancelOrder(int cancelId, final int orderId, final User user, final int securityId, final long price,
      final long quantity, Side side, TimeInForce timeInForce) {
    return createCancelOrder(cancelId, orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
  }

  protected static CancelOrder createCancelOrder(int cancelId, final int orderId, final User user, OrdType orderType, final int securityId,
      final long price, final long quantity, Side side, TimeInForce timeInForce) {
    final int priceScale = InstrumentCache.getPair(securityId) == null ? 2 : InstrumentCache.getPair(securityId).getPriceScale();
    final int quantityScale = InstrumentCache.getPair(securityId) == null ? 2 : InstrumentCache.getPair(securityId).getQuantityScale();
    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.setCancelId(cancelId);
    cancelOrder.setUser(user);
    cancelOrder.setOrigOrderId(orderId);
    cancelOrder.setAccount(user.getId());
    cancelOrder.setClOrdId("ClOrdId");
    cancelOrder.setOrdType(orderType);
    cancelOrder.setSecurityId(securityId);
    cancelOrder.setSide(side);
    cancelOrder.setPrice(price, (short) 2);
    cancelOrder.setPriceInt((int) scale(price, 2, priceScale));
    cancelOrder.setQty(quantity, (short) 2);
    cancelOrder.setQuantityLong(scale(quantity, 2, quantityScale));
    cancelOrder.setQuantityOrigLong(scale(quantity, 2, quantityScale));

    return cancelOrder;
  }

  protected static CancelReplaceOrder createCancelReplaceOrder(int cancelId, final long orderId, final User user, final int securityId,
      final long price, final long quantity, Side side, TimeInForce timeInForce, Order order) {
    final int priceScale = InstrumentCache.getPair(securityId) == null ? 2 : InstrumentCache.getPair(securityId).getPriceScale();
    final int quantityScale = InstrumentCache.getPair(securityId) == null ? 2 : InstrumentCache.getPair(securityId).getQuantityScale();
    CancelReplaceOrder cancelReplaceOrder = new CancelReplaceOrder();
    cancelReplaceOrder.setOrigOrderId(orderId);
    cancelReplaceOrder.setOrder(order);
    cancelReplaceOrder.setCancelId(cancelId);
    cancelReplaceOrder.setUser(user);
    cancelReplaceOrder.setAccount(user.getId());
    cancelReplaceOrder.setClOrdId("ClOrdId");
    cancelReplaceOrder.setOrdType(OrdType.LIMIT);
    cancelReplaceOrder.setSecurityId(securityId);
    cancelReplaceOrder.setSide(side);
    cancelReplaceOrder.setPrice(price, (short) 2);
    cancelReplaceOrder.setPriceInt((int) scale(price, 2, priceScale));
    cancelReplaceOrder.setQty(quantity, (short) 2);
    cancelReplaceOrder.setQuantityLong(scale(quantity, 2, quantityScale));
    cancelReplaceOrder.setQuantityOrigLong(scale(quantity, 2, quantityScale));

    return cancelReplaceOrder;
  }

  protected static MassCancelOrder createMassCancelOrder(final long cancelId, final Order order) {
    MassCancelOrder massCancelOrder = new MassCancelOrder(order, cancelId, 1);

    return massCancelOrder;
  }
}
