package com.solfini.matchengine.orderbook.validator;

import com.google.common.collect.Lists;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookPriceLevel;
import com.solfini.matchengine.orderbook.StopLimitContainer;

import static com.solfini.common.Constants.*;

import java.util.List;
import java.util.Map;

public class ArrayOrderBookValidator implements OrderBookValidator {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(ArrayOrderBookValidator.class);

    protected ArrayOrderBook orderBook;

    protected static final String ORDER_PLACE_INCORRECT_MSG = "This order should not be placed at price %d ,bid %b : %s";
    protected static final String DEPTH_COUNT_INCORRECT_MSG = "Depth count incorrect bid %b, expected: %d .actual: %d";
    protected static final String CACHE_ARRAY_NOT_MATCH_MSG = "Order book cache array not match with bid : %b , expected %s, actual %s";
    protected static final String MAX_BID_MIN_ASK_CROSS = "Max bid and min ask crossed with max bid %d, min ask %d";
    protected static final String OPEN_ORDER_BETWEEN_GAP = "ORDER with price %d is between max bid %d and min ask %d";
    protected static final String HEAD_NOT_NULL_TAIL_NULL_MSG = "Level at price %d with head null while tail not null";
    protected static final String HEAD_NULL_TAIL_NOT_NULL_MSG = "Level at price %d with head not null while tail null";
    protected static final String HEAD_PRE_NOT_NULL = "Level at price %d with head pre not null";
    protected static final String TAIL_NEXT_NOT_NULL = "Level at price %d with tail next not null";
    protected static final String OUT_OF_BOUND_EXCEPTION_MSG = "Order should not be place in out of bound map %s";

    public ArrayOrderBookValidator(ArrayOrderBook arrayOrderBook) {
        this.orderBook = arrayOrderBook;
    }

    @Override
    public int getOrderBookType() {
        return ARRAY_ORDER_BOOK;
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    @Override
    public List<String> validate(List<Order> allOrders) {

        List<String> validateResult = Lists.newArrayList();

        try {
            OrderBookPriceLevel[] orderBookArray = orderBook.getBookArr();
            StopLimitContainer stopLimitContainer = orderBook.getStopLimitContainer();
            Map<Long, Order> outOfBoundsMap = orderBook.getOutOfBoundsOrderMap();

            int cacheDepth = orderBook.CACHE_DEPTH;
            int arraySize = orderBook.ARR_SIZE;

            int[] bidLevelCachePtrArr = new int[cacheDepth];
            int[] askLevelCachePtrArr = new int[cacheDepth];

            int bidDepth = 0;
            int askDepth = 0;

            int maxBid = orderBook.getBid();
            int minAsk = orderBook.getAsk();

            if (minAsk == 0) {
                minAsk = arraySize;
            }

            if (maxBid == 0) {
                maxBid = -1;
            }

            if (maxBid >= minAsk) {
                validateResult.add(String.format(MAX_BID_MIN_ASK_CROSS, maxBid, minAsk));
            }

            for (int i = maxBid + 1; i < minAsk; i++) {
                if (orderBookArray[i].getHead() != null) {
                    validateResult.add(String.format(OPEN_ORDER_BETWEEN_GAP, i, maxBid, minAsk));
                    validateOrderBookPriceLevel(orderBookArray[i], i, orderBookArray[i].getHead().getType() == BUY_LIMIT, validateResult, allOrders);
                }
            }

            if (maxBid != -1) {
                int index = 0;
                for (int i = maxBid; i >= 0; i--) {
                    if (orderBookArray[i].getHead() != null) {
                        if (index < cacheDepth) {
                            bidLevelCachePtrArr[index] = i;
                            index++;
                        }
                        bidDepth += validateOrderBookPriceLevel(orderBookArray[i], i, true, validateResult, allOrders);
                    } else if (orderBookArray[i].getTail() != null) {
                        validateResult.add(String.format(HEAD_NULL_TAIL_NOT_NULL_MSG, i));
                    }
                }
            }

            if (minAsk != arraySize) {
                int index = 0;
                for (int i = minAsk; i < arraySize; i++) {
                    if (orderBookArray[i].getHead() != null) {
                        if (index < cacheDepth) {
                            askLevelCachePtrArr[index] = i;
                            index++;
                        }
                        askDepth += validateOrderBookPriceLevel(orderBookArray[i], i, false, validateResult, allOrders);
                    } else if (orderBookArray[i].getTail() != null) {
                        validateResult.add(String.format(HEAD_NULL_TAIL_NOT_NULL_MSG, i));
                    }
                }
            }

            validateStopLimitOrder(stopLimitContainer, true, validateResult, allOrders);
            validateStopLimitOrder(stopLimitContainer, false, validateResult, allOrders);

            validateOutOfRangeOrders(outOfBoundsMap, validateResult, allOrders);

            if (bidDepth != orderBook.getBidDepth()) {
                validateResult.add(String.format(DEPTH_COUNT_INCORRECT_MSG, true, bidDepth, orderBook.getBidDepth()));
            }

            if (askDepth != orderBook.getAskDepth()) {
                validateResult.add(String.format(DEPTH_COUNT_INCORRECT_MSG, false, askDepth, orderBook.getAskDepth()));
            }

            for (int i = 0; i < cacheDepth; i++) {
                if (bidLevelCachePtrArr[i] != orderBook.getBidLevelCachePtrArr()[i]) {
                    String expectedCache = join(bidLevelCachePtrArr);
                    String actualCache = join(orderBook.getBidLevelCachePtrArr());
                    validateResult.add(String.format(CACHE_ARRAY_NOT_MATCH_MSG, true, expectedCache, actualCache));
                    break;
                }
                if (askLevelCachePtrArr[i] != orderBook.getAskLevelCachePtrArr()[i]) {
                    String expectedCache = join(askLevelCachePtrArr);
                    String actualCache = join(orderBook.getAskLevelCachePtrArr());
                    validateResult.add(String.format(CACHE_ARRAY_NOT_MATCH_MSG, true, expectedCache, actualCache));
                    break;
                }
            }
            return validateResult;

        } catch (Exception e) {
            LOGGER.error("Got exception when doing status check", e);
            return validateResult;
        }
    }

    @Override
    public List<String> validate() {
        return this.validate(Lists.newArrayList());
    }

    protected int validateOrderBookPriceLevel(OrderBookPriceLevel level, int price, boolean bid, List<String> exceptions, List<Order> allOrders) {

        if (level.getTail() == null) {
            exceptions.add(String.format(HEAD_NOT_NULL_TAIL_NULL_MSG, price));
            return 0;
        }

        if (level.getHead().getPrev() != null) {
            exceptions.add(String.format(HEAD_PRE_NOT_NULL, price));
        }

        if (level.getTail().getNext() != null) {
            exceptions.add(String.format(TAIL_NEXT_NOT_NULL, price));
        }

        Order index = level.getHead();
        int count = 0;

        while (true) {
            allOrders.add(index);
            count++;

            if (index.getPriceInt() != price) {
                exceptions.add(String.format(ORDER_PLACE_INCORRECT_MSG, price, bid, index));
            }

            if ((bid && index.getType() != BUY_LIMIT) || (!bid && index.getType() != SELL_LIMIT)) {
                exceptions.add(String.format(ORDER_PLACE_INCORRECT_MSG, price, bid, index));
            }

            if (index == level.getTail()) {
                break;
            }

            index = index.getNext();
        }
        return count;
    }

    protected int validateStopLimitOrder(StopLimitContainer stopLimitContainer, boolean bid, List<String> exceptions, List<Order> allOrders) {
        int originSize = allOrders.size();
        if (bid) {
            stopLimitContainer.getTriggeredBuyLimitList(Integer.MAX_VALUE, allOrders); // This removes entries from the stop limit container !!!
        } else {
            stopLimitContainer.getTriggeredSellLimitList(0, allOrders); // This removes entries from the stop limit container !!!
        }
        return allOrders.size() - originSize;
    }

    protected int validateOutOfRangeOrders(Map<Long, Order> outOfBoundsMap, List<String> exceptions, List<Order> allOrders) {

        for (Order order : outOfBoundsMap.values()) {
            if (order.getType() != SELL_LIMIT || order.getType() != STOP_SELL_LIMIT) {
                exceptions.add(String.format(OUT_OF_BOUND_EXCEPTION_MSG, order));
            }
            allOrders.add(order);
        }
        return outOfBoundsMap.size();

    }

    protected String join(int[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(i).append(",");
        }

        if (sb.length() > 0) {
            return sb.substring(0, sb.length() - 1);
        } else {
            return "";
        }
    }
}
