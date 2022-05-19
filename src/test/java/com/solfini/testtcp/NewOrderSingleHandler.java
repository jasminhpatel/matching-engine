package com.solfini.testtcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;

/**
 *
 * @author Chris Mack
 *
 */
public class NewOrderSingleHandler implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(NewOrderSingleHandler.class);
  //@formatter:off
  /*
  private static AtomicLong orderId = new AtomicLong(1); // load initial and increment
  // private final MappedByteBuffer mappedByteBuffer = Context.getStateMM();
  private long orderPriority = 0;
  private NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();

  public NewOrderSingleHandler() {

  }

  public long getNextOrderId() {
    orderPriority++;
    long orderIdValue = orderId.incrementAndGet();
    // mappedByteBuffer.putLong(8, orderId.get());
    return orderIdValue;
  }

  public Message decodeNewOrderSingle(final MutableAsciiBuffer mab, final int length, final String senderCompId) {
    try {
      newOrderSingleDecoder.decode(mab, 0, length);

      // validate
      if (!newOrderSingleDecoder.hasSecurityID()) {
        BusinessRejectMessage businessRejectMessage = BusinessRejectMessage.createBusinessReject(senderCompId, MsgType.ORDER_SINGLE,
            newOrderSingleDecoder.clOrdIDAsString(), BusinessRejectReason.SECURITY_ID_IS_MISSING, SECURITY_ID_IS_MISSING,
            StringUtil.charArrayToLong(newOrderSingleDecoder.clOrdID(), newOrderSingleDecoder.clOrdIDLength()), 0);

        return businessRejectMessage;
      }

      Order order = OrderObjectPool.get();
      // order.set(newOrderSingleDecoder, getNextOrderId(), orderPriority++);
      order.setSenderCompId(senderCompId);

      return parseOrder(order);
    } catch (Exception e) {
      LOGGER.error("error", e);
    }
    BusinessRejectMessage businessRejectMessage = BusinessRejectMessage.createBusinessReject(senderCompId, MsgType.ORDER_SINGLE,
        newOrderSingleDecoder.clOrdIDAsString(), BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE,
        StringUtil.charArrayToLong(newOrderSingleDecoder.clOrdIDAsString().trim().toCharArray()), 0);
    return businessRejectMessage;
  }

  public static final Message parseOrder(final Order order) {
    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      if (instrumentPair == null) {
        BusinessRejectMessage businessRejectMessage = BusinessRejectMessage.createBusinessReject("", MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.INSTRUMENT_NOT_FOUND, INSTRUMENT_NOT_FOUND, order.getOrderId(), 0);
        return businessRejectMessage;
      }

      long priceLong = order.getPrice();
      int priceScale = order.getPriceScale();
      if (instrumentPair.getPriceScale() > priceScale) {
        for (int i = 0; i < (instrumentPair.getPriceScale() - priceScale); i++)
          priceLong = priceLong * 10;
      } else if (instrumentPair.getPriceScale() < priceScale) {
        for (int i = 0; i < (priceScale - instrumentPair.getPriceScale()); i++)
          priceLong = priceLong / 10;
      }
      order.setPriceInt((int) priceLong);

      long quantityLong = order.getQty();
      int quantityScale = order.getQtyScale();
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
        // User user = UserCache.get(StringUtil.charArrayToInt());
        User user = new User(1);
        if (user != null)
          order.setUser(user);
        else {
          BusinessRejectMessage businessRejectMessage =
              BusinessRejectMessage.createBusinessReject("", MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
                  BusinessRejectReason.USER_NOT_FOUND, USER_NOT_FOUND, order.getOrderId(), order.getSourceSeqNum());
          return businessRejectMessage;
        }
      } catch (Exception e) {
        LOGGER.error("Error", e);
        BusinessRejectMessage businessRejectMessage =
            BusinessRejectMessage.createBusinessReject("", MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
                BusinessRejectReason.UNABLE_TO_LOAD_USER, UNABLE_TO_LOAD_USER, order.getOrderId(), order.getSourceSeqNum());
        return businessRejectMessage;
      }

      // LOGGER.info("test newOrderSingleDecoder side=" + side + ", symbol=" + symbol + ", clOrdID=" + clOrdID + ", ordType=" + ordType);
      // LOGGER.info("test newOrderSingleDecoder side=" + side + ", price=" + priceLong);
      // LOGGER.info("parsed priceLong=" + priceLong + ", priceScale=" + priceScale);
      // LOGGER.info("parsed quantity=" + quantity + ", quantityScale=" + quantityScale);

      //
      // char ordType = order.getOrdType().representation(); char side = order.getSide().representation();
     //
     //
      // if (BUY == side) { if (LIMIT == ordType) order.setType(BUY_LIMIT); else if (MARKET == ordType) order.setType(BUY_MARKET); else if
     // (STOP_LIMIT == ordType || STOP == ordType) { order.setType(STOP_BUY_LIMIT); return parseStop(order, instrumentPair); } } else if
     // (SELL == side) { if (LIMIT == ordType) order.setType(SELL_LIMIT); else if (MARKET == ordType) order.setType(SELL_MARKET); else if
     // (STOP_LIMIT == ordType || STOP == ordType) { order.setType(STOP_SELL_LIMIT); return parseStop(order, instrumentPair); } }
      //

    } catch (Exception e) {
      e.printStackTrace();
      BusinessRejectMessage businessRejectMessage =
          BusinessRejectMessage.createBusinessReject("", MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
              BusinessRejectReason.UNABLE_TO_PARSE_ORDER, UNABLE_TO_PARSE_ORDER, order.getOrderId(), order.getSourceSeqNum());
      return businessRejectMessage;
    }
    return order;
  }

  private static final Message parseStop(final Order order, final InstrumentPair instrumentPair) {

    long stopPriceLong = order.getStopPx();
    int stopPriceScale = order.getStopPxScale();
    if (stopPriceLong == 0) {
      BusinessRejectMessage businessRejectMessage =
          BusinessRejectMessage.createBusinessReject("", MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
              BusinessRejectReason.STOP_PRICE_IS_MISSING, STOP_PRICE_IS_MISSING, order.getOrderId(), order.getSourceSeqNum());
      return businessRejectMessage;
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
  */
  //@formatter:on
}
