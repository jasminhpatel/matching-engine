package com.solfini.matchengine.decoder;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.pool.LiquidationOrderObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.NewOrderSingleDecoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import com.solfini.sbe.encoder.TimeInForce;

/**
 *
 * @author Chris Mack
 *
 */
public class NewOrderSingleHandler implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(NewOrderSingleHandler.class);

  private static final AtomicLong orderId = new AtomicLong(); // (StateLoader.getInitialOrderId()); // load initial and increment
  private static final AtomicLong[] secondaryOrderIdArr = buildSecondaryOrderIdArr();

  private static long orderPriority = 0;
  private final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();

  public NewOrderSingleHandler() {
    // default constructor
  }

  private static AtomicLong[] buildSecondaryOrderIdArr() {
    final AtomicLong[] secondaryOrderIdArr = new AtomicLong[1024];
    for (int i = 0; i < 1024; i++)
      secondaryOrderIdArr[i] = new AtomicLong();

    return secondaryOrderIdArr;
  }

  public static final long getOrderId() {
    return orderId.get();
  }

  public static final void setOrderId(final long newValue) {
    orderId.set(newValue);
  }

  public static final void setOrderIdIfGreater(final long newValue) {
    if (newValue > orderId.get())
      orderId.set(newValue);
  }

  public static final long getNextOrderId() {
    orderPriority++;
    return orderId.incrementAndGet();
  }

  public static final long getSecondaryOrderId(final int pairId) {
    return secondaryOrderIdArr[pairId].incrementAndGet();
  }

  public static final void setSecondaryOrderId(final int pairId, final long newValue) {
    secondaryOrderIdArr[pairId].set(newValue);
  }

  public static final void setSecondaryOrderIdIfGreater(final int pairId, final long newValue) {
    if (newValue > secondaryOrderIdArr[pairId].get())
      secondaryOrderIdArr[pairId].set(newValue);
  }

  public final long getNextSecondaryOrderId(final int pairId) {
    return secondaryOrderIdArr[pairId].incrementAndGet();
  }

  public final Message buildNewOrderSingle(final User user, final String senderCompId, final int securityId, final String clOrdId,
      final long price, final short price_scale, final long qty, final short qty_scale, final Side side, final OrdType ordType,
      final boolean toClose) {
    try {
      final Order order = OrderObjectPool.get();
      order.setOrderId(getNextOrderId());
      order.setOrderPriority(orderPriority++);
      order.setSecondaryOrderId(getSecondaryOrderId(securityId));

      order.setUser(user);
      order.setAccount(user.getId());
      order.setSenderCompId(senderCompId);
      order.setSecurityId(securityId);
      order.setClOrdId(clOrdId);
      order.setPrice(price, price_scale);
      order.setQty(qty, qty_scale);
      order.setSide(side);
      order.setOrdType(ordType);
      order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
      order.setToClose(toClose);

      return parseOrder(order);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return BusinessRejectMessage.createBusinessRejectWithClOrdId(senderCompId, MsgType.ORDER_SINGLE, newOrderSingleDecoder.clOrdID(),
        BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE, 0, 0, newOrderSingleDecoder.secondaryOrderId(),
        newOrderSingleDecoder.securityId(), StringUtil.toLong(newOrderSingleDecoder.clOrdID()));
  }

  public final Message buildNewLiquidationOrder(final User user, final String senderCompId, final int securityId, final String clOrdId,
      final long price, final short price_scale, final long qty, final short qty_scale, final Side side, final OrdType ordType,
      final boolean toClose) {
    try {
      final LiquidationOrder order = LiquidationOrderObjectPool.get();
      order.setOrderId(getNextOrderId());
      order.setOrderPriority(orderPriority++);
      order.setSecondaryOrderId(getSecondaryOrderId(securityId));

      order.setUser(user);
      order.setAccount(user.getId());
      order.setSenderCompId(senderCompId);
      order.setSecurityId(securityId);
      order.setClOrdId(clOrdId);
      order.setPrice(price, price_scale);
      order.setQty(qty, qty_scale);
      order.setSide(side);
      order.setOrdType(ordType);
      order.setTimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL);
      order.setToClose(toClose);

      return parseOrder(order);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return BusinessRejectMessage.createBusinessRejectWithClOrdId(senderCompId, MsgType.ORDER_SINGLE, newOrderSingleDecoder.clOrdID(),
        BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE, 0, 0, newOrderSingleDecoder.secondaryOrderId(),
        newOrderSingleDecoder.securityId(), StringUtil.toLong(newOrderSingleDecoder.clOrdID()));
  }

  public final Message buildNewCollateralSwapLiquidationOrder(final User user, final int securityId, final long price,
      final short price_scale, final long qty, final short qty_scale, final Side side) {
    try {
      final LiquidationOrder order = LiquidationOrderObjectPool.get();
      order.setOrderId(getNextOrderId());
      order.setOrderPriority(orderPriority++);
      order.setSecondaryOrderId(getSecondaryOrderId(securityId));

      order.setUser(user);
      order.setAccount(user.getId());
      order.setSenderCompId("" + 1_000_000_000 + user.getId());
      order.setSecurityId(securityId);
      order.setClOrdId(String.valueOf(TimeUtil.getTime()));
      order.setPrice(price, price_scale);
      order.setQty(qty, qty_scale);
      order.setSide(side);
      order.setOrdType(OrdType.LIMIT);
      order.setTimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL);
      order.setToClose(true);

      return parseOrder(order);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return BusinessRejectMessage.createBusinessRejectWithClOrdId("" + 1_000_000_000 + user.getId(), MsgType.ORDER_SINGLE,
        newOrderSingleDecoder.clOrdID(), BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE, 0, 0,
        newOrderSingleDecoder.secondaryOrderId(), newOrderSingleDecoder.securityId(), StringUtil.toLong(newOrderSingleDecoder.clOrdID()));
  }

  public Message decodeNewOrderSingle(final MessageHeaderDecoder headerDecoder, final NewOrderSingleDecoder newOrderSingleDecoder) {
    try {

      // validate
      if (newOrderSingleDecoder.securityId() == 0) {
        return BusinessRejectMessage.createBusinessReject(headerDecoder.senderCompId(), MsgType.ORDER_SINGLE,
            newOrderSingleDecoder.clOrdID(), BusinessRejectReason.SECURITY_ID_IS_MISSING, SECURITY_ID_IS_MISSING,
            newOrderSingleDecoder.orderId(), 0, newOrderSingleDecoder.secondaryOrderId(), newOrderSingleDecoder.securityId());
      }

      final Order order =
          (BooleanType.FALSE == newOrderSingleDecoder.isLiquidation()) ? OrderObjectPool.get() : LiquidationOrderObjectPool.get();
      order.set(newOrderSingleDecoder, getNextOrderId(), orderPriority++);
      order.setSenderCompId(headerDecoder.senderCompId());
      order.setSecondaryOrderId(getSecondaryOrderId(order.getSecurityId()));

      return parseOrder(order);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return BusinessRejectMessage.createBusinessReject(headerDecoder.senderCompId(), MsgType.ORDER_SINGLE, newOrderSingleDecoder.clOrdID(),
        BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE, newOrderSingleDecoder.orderId(), 0, newOrderSingleDecoder.secondaryOrderId(),
        newOrderSingleDecoder.securityId());
  }

  public static final Message parseOrder(final Order order) {
    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      if (instrumentPair == null) {
        return BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
            BusinessRejectReason.INSTRUMENT_NOT_FOUND, INSTRUMENT_NOT_FOUND, order.getOrderId(), 0, order.getSecondaryOrderId(),
            order.getSecurityId());
      }

      long priceLong = order.getPrice();
      final int priceScale = order.getPriceScale();
      if (instrumentPair.getPriceScale() > priceScale) {
        for (int i = 0; i < (instrumentPair.getPriceScale() - priceScale); i++)
          priceLong = priceLong * 10;
      } else if (instrumentPair.getPriceScale() < priceScale) {
        for (int i = 0; i < (priceScale - instrumentPair.getPriceScale()); i++)
          priceLong = priceLong / 10;
      }
      order.setPriceInt((int) priceLong);

      if (order.getPrice2() > 0) {
        long price2Long = order.getPrice2();
        final int price2Scale = order.getPrice2Scale();
        if (instrumentPair.getPriceScale() > price2Scale) {
          for (int i = 0; i < (instrumentPair.getPriceScale() - price2Scale); i++)
            price2Long = price2Long * 10;
        } else if (instrumentPair.getPriceScale() < price2Scale) {
          for (int i = 0; i < (price2Scale - instrumentPair.getPriceScale()); i++)
            price2Long = price2Long / 10;
        }
        order.setPrice2Int((int) price2Long);
      }

      long quantityLong = order.getQty();
      final int quantityScale = order.getQtyScale();
      if (instrumentPair.getQuantityScale() > quantityScale) {
        for (int i = 0; i < (instrumentPair.getQuantityScale() - quantityScale); i++)
          quantityLong = quantityLong * 10;
      } else if (instrumentPair.getQuantityScale() < quantityScale) {
        for (int i = 0; i < (quantityScale - instrumentPair.getQuantityScale()); i++)
          quantityLong = quantityLong / 10;
      }
      order.setQuantityLong(quantityLong);
      order.setQuantityOrigLong(quantityLong);

      try {
        final User user = UserCache.get(order.getAccount());
        if (user != null && user.isActive()) {
          order.setUser(user);
        } else {
          order.setUser(null);
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        return BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
            BusinessRejectReason.UNABLE_TO_LOAD_USER, UNABLE_TO_LOAD_USER, order.getOrderId(), 0, order.getSecondaryOrderId(),
            order.getSecurityId());
      }

      final OrdType ordType = order.getOrdType();
      final Side side = order.getSide();

      if (Side.BUY == side) {
        if (OrdType.LIMIT == ordType)
          order.setType(BUY_LIMIT);
        else if (OrdType.SELECT == ordType)
          order.setType(BUY_SELECT);
        else if (OrdType.MARKET == ordType)
          order.setType(BUY_MARKET);
        else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
          order.setType(STOP_BUY_LIMIT);
          return parseStop(order, instrumentPair);
        }
      } else if (Side.SELL == side) {
        if (OrdType.LIMIT == ordType)
          order.setType(SELL_LIMIT);
        else if (OrdType.SELECT == ordType)
          order.setType(SELL_SELECT);
        else if (OrdType.MARKET == ordType)
          order.setType(SELL_MARKET);
        else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
          order.setType(STOP_SELL_LIMIT);
          return parseStop(order, instrumentPair);
        }
      }

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
          BusinessRejectReason.UNABLE_TO_PARSE_ORDER, UNABLE_TO_PARSE_ORDER, order.getOrderId(), 0, order.getSecondaryOrderId(),
          order.getSecurityId());
    }
    return order;
  }

  private static final Message parseStop(final Order order, final InstrumentPair instrumentPair) {

    long stopPriceLong = order.getStopPx();
    final int stopPriceScale = order.getStopPxScale();
    if (stopPriceLong == 0 && !order.isTrailingStop()) {
      return BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
          BusinessRejectReason.STOP_PRICE_IS_MISSING, STOP_PRICE_IS_MISSING, order.getOrderId(), 0, order.getSecondaryOrderId(),
          order.getSecurityId());
    }


    if (instrumentPair.getPriceScale() > stopPriceScale) {
      for (int i = 0; i < (instrumentPair.getPriceScale() - stopPriceScale); i++)
        stopPriceLong = stopPriceLong * 10;
    } else if (instrumentPair.getPriceScale() < stopPriceScale) {
      for (int i = 0; i < (stopPriceScale - instrumentPair.getPriceScale()); i++)
        stopPriceLong = stopPriceLong / 10;
    }
    // swap prices
    order.setStopPxInt(order.getPriceInt());
    order.setPriceInt((int) stopPriceLong);
    return order;
  }

  public static final BusinessRejectMessage lookupUser(final Order order) {
    try {
      final User user = UserCache.get(order.getAccount());
      if (user != null && user.isActive())
        order.setUser(user);
      else {
        return BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
            BusinessRejectReason.USER_NOT_FOUND, USER_NOT_FOUND, order.getOrderId(), 0, order.getSecondaryOrderId(), order.getSecurityId());
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
          BusinessRejectReason.UNABLE_TO_LOAD_USER, UNABLE_TO_LOAD_USER, order.getOrderId(), 0, order.getSecondaryOrderId(),
          order.getSecurityId());
    }

    return null;
  }
}
