package com.solfini.matchengine.orderbook;

import com.solfini.common.Constants;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.preordercheck.CashPreOrderCheck;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.PreOrderCheck;

/**
 *
 * @author Chris Mack
 *
 */
public class OrderBookFactory implements Constants {

  private OrderBookFactory() {
    // hidden default constructor
  }

  public static final OrderBook create(final int orderBookStrategy, final int preOrderCheckStrategy, final InstrumentPair pair) {
    PreOrderCheck preOrderCheck = null;
    switch (preOrderCheckStrategy) {
      case CASH_PREORDER_CHECK:
        preOrderCheck = new CashPreOrderCheck();
        break;
      case MARGIN_PREORDER_CHECK:
        preOrderCheck = new MarginPreOrderCheckAndSettle();
        break;
      default:
        preOrderCheck = new NoPreOrderCheck();
    }

    switch (orderBookStrategy) {
      case TREE_ORDER_BOOK:
        return new TreeOrderBook(pair.getSymbol(), preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case ARRAY_ORDER_BOOK:
        return new ArrayOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case LINKED_LIST_ORDER_BOOK:
        return new LinkedListOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case TREE_ORDER_BOOK2:
        return new TreeOrderBook2(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case SELECT_ARRAY_ORDER_BOOK:
        return new SelectArrayOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case COPY_TRADE_ORDER_BOOK:
        return new CopyTradeOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case LIQUIDITY_ORDER_BOOK:
        return new LiquidityOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      default:
        return new TreeOrderBook(pair.getSymbol(), preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
    }
  }

  public static final OrderBook create(final int orderBookStrategy, final int preOrderCheckStrategy, final InstrumentPair pair,
      final int arrSize, final int cacheDepth) {
    PreOrderCheck preOrderCheck = null;
    switch (preOrderCheckStrategy) {
      case CASH_PREORDER_CHECK:
        preOrderCheck = new CashPreOrderCheck();
        break;
      case MARGIN_PREORDER_CHECK:
        preOrderCheck = new MarginPreOrderCheckAndSettle();
        break;
      default:
        preOrderCheck = new NoPreOrderCheck();
    }

    switch (orderBookStrategy) {
      case TREE_ORDER_BOOK:
        return new TreeOrderBook(pair.getSymbol(), preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case ARRAY_ORDER_BOOK:
        return new ArrayOrderBook(pair, preOrderCheck, arrSize, cacheDepth, orderBookStrategy, preOrderCheckStrategy);
      case LINKED_LIST_ORDER_BOOK:
        return new LinkedListOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case TREE_ORDER_BOOK2:
        return new TreeOrderBook2(pair, preOrderCheck, arrSize, cacheDepth, orderBookStrategy, preOrderCheckStrategy);
      case SELECT_ARRAY_ORDER_BOOK:
        return new SelectArrayOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case COPY_TRADE_ORDER_BOOK:
        return new CopyTradeOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      case LIQUIDITY_ORDER_BOOK:
        return new LiquidityOrderBook(pair, preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
      default:
        return new TreeOrderBook(pair.getSymbol(), preOrderCheck, orderBookStrategy, preOrderCheckStrategy);
    }
  }

  // must be called from matching thread
  // recreate an order book to replace existing one
  // could be used to resize
  public static final OrderBook recreateReplace(final int orderBookStrategy, final int preOrderCheckStrategy, final InstrumentPair oldPair,
      final InstrumentPair newPair, final int arrSize, final int cacheDepth) {
    OrderBook oldOrderBook = (oldPair == null ? null : oldPair.getOrderBook());
    if (oldOrderBook == null) {
      final OrderBook orderBook = create(orderBookStrategy, preOrderCheckStrategy, newPair, arrSize, cacheDepth);
      newPair.setOrderBook(orderBook);
      return orderBook;
    }

    // check if we need to recreate the order book or simply reuse it
    if ((ARRAY_ORDER_BOOK == orderBookStrategy) && (oldOrderBook instanceof ArrayOrderBook)
        && (newPair.getPriceScale() == oldPair.getPriceScale()) && (newPair.getQuantityScale() == oldPair.getQuantityScale())) {
      ArrayOrderBook oldArrayOrderBook = (ArrayOrderBook) oldOrderBook;
      if ((oldArrayOrderBook.ARR_SIZE == (arrSize > 0 ? arrSize : ArrayOrderBook.DEFAULT_ARR_SIZE))
          && (oldArrayOrderBook.CACHE_DEPTH == (cacheDepth > 0 ? cacheDepth : ArrayOrderBook.DEFAULT_CACHE_DEPTH))) {
        newPair.setOrderBook(oldOrderBook);
        return oldOrderBook;
      }
    }

    // check if we need to recreate the order book or simply reuse it
    if ((SELECT_ARRAY_ORDER_BOOK == orderBookStrategy) && (oldOrderBook instanceof SelectArrayOrderBook)
        && (newPair.getPriceScale() == oldPair.getPriceScale()) && (newPair.getQuantityScale() == oldPair.getQuantityScale())) {
      SelectArrayOrderBook oldSelectArrayOrderBook = (SelectArrayOrderBook) oldOrderBook;
      if ((oldSelectArrayOrderBook.ARR_SIZE == (arrSize > 0 ? arrSize : ArrayOrderBook.DEFAULT_ARR_SIZE))
          && (oldSelectArrayOrderBook.CACHE_DEPTH == (cacheDepth > 0 ? cacheDepth : ArrayOrderBook.DEFAULT_CACHE_DEPTH))) {
        newPair.setOrderBook(oldOrderBook);
        return oldOrderBook;
      }
    }

    final OrderBook orderBook = create(orderBookStrategy, preOrderCheckStrategy, newPair, arrSize, cacheDepth);
    oldOrderBook.copyTo(orderBook, buildTransform(newPair, oldPair));

    newPair.setOrderBook(orderBook);
    oldOrderBook.reclaim();
    return orderBook;
  }

  // build transform to change scale for existing orders
  private static final OrderBook.Transform buildTransform(final InstrumentPair newPair, final InstrumentPair oldPair) {
    return new OrderBook.Transform() {
      public Order transform(final Order order) { // re-parse the order if there are scale changes
        InstrumentPair instrumentPair = newPair;
        if (oldPair.getPriceScale() != newPair.getPriceScale()) {
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

          if (order.getStopPx() > 0) {
            int stopPxInt = order.getStopPxInt();
            if (instrumentPair.getPriceScale() > order.getStopPxScale()) {
              for (int i = 0; i < (instrumentPair.getPriceScale() - order.getStopPxScale()); i++)
                stopPxInt = stopPxInt * 10;
            } else if (instrumentPair.getPriceScale() < order.getStopPxScale()) {
              for (int i = 0; i < (order.getStopPxScale() - instrumentPair.getPriceScale()); i++)
                stopPxInt = stopPxInt / 10;
            }
            order.setStopPxInt(stopPxInt);
          }
        }

        if (oldPair.getQuantityScale() != newPair.getQuantityScale()) {
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
        }

        return order;
      }
    };
  }
}
